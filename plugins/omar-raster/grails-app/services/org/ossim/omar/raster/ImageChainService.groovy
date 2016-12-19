package org.ossim.omar.raster

import joms.oms.Chain
import joms.oms.WmsMap
import joms.oms.ossimKeywordlist
import joms.oms.ossimGptVector
import joms.oms.ossimDptVector
import joms.oms.Util
import joms.oms.ossimDpt
import joms.oms.ossimGpt
import org.ossim.oms.image.omsImageSource
import org.ossim.oms.image.omsRenderedImage
import java.awt.image.*
import org.ossim.omar.core.TransparentFilter
import org.ossim.omar.core.ImageGenerator
import org.ossim.omar.core.Utility;

import joms.oms.Init;

class ImageChainService
{

    static transactional = false

    def parserPool

    /**
     * @param numberOfInputBands
     * @param bandSelection
     * @return true if the bandSelection list is valid or false otherwise
     */
    static def validBandSelection(def numberOfInputBands, def bandSelection)
    {
        def bandArray = []
        def validBands = true
        if ( bandSelection == "default" )
        {
            return validBands
        }
        if ( bandSelection instanceof String )
        {
            bandArray = bandSelection.split( "," )
        }
        // validate that the ban list is within the desired ranges
        // the http request is 0 based.
        if ( bandArray.size() < 1 )
        {
            validBands = false
        }
        else
        {
            bandArray.each {
                try
                {
                    if ( Integer.parseInt( it ) >= numberOfInputBands )
                    {
                        validBands = false;
                    }
                }
                catch ( Exception e )
                {
                    validBands = false
                }
            }
        }
        validBands
    }
    /**
     * @param entry
     * @param params
     * @return a map that allocates a chain if specified and the keywordlist string representing the parameters
     */
    def createImageChain(def entry, def params, def allocateChain = true, def threaded = false )
    {
        def quickLookFlagString = params?.quicklook ?: "false"
        def interpolation = params.interpolation ? params.interpolation : "bilinear"
        def sharpenMode = params?.sharpen_mode ?: ""
        def crs = params.srs ? params.srs : params.crs ?: null
        def nullFlip = params?.null_flip ?: null
        def requestFormat = params?.format?.toLowerCase()
        def sharpenWidth = params?.sharpen_width ?: null
        def sharpenSigma = params?.sharpen_sigma ?: null
        def stretchMode = params?.stretch_mode ? params?.stretch_mode.toLowerCase() : "linear_auto_min_max"
        def stretchModeRegion = params?.stretch_mode_region ?: "global" 
        def totalBands = 1
        def bands = params?.bands ?: "0"
        def rotate = params?.rotate ?: null
        def scale = params?.scale ?: null
        def pivot = params?.pivot ?: null
	def frame = params?.frame ?: 0
	def process = params?.process ?: "true"
        def tempHistogramFile = entry?.getHistogramFile()//getFileFromObjects("histogram")?.name
	def tempOverviewFile = entry?.mainFile?.name.substring(0, entry?.mainFile?.name.lastIndexOf(".")) + ".ovr"
        def histogramFile = new File( tempHistogramFile ?: "" )
        def overviewFile = new File( tempOverviewFile ?: "" )
	if (!overviewFile.exists()) 
	{
		def tempOverview2File = entry?.mainFile?.name.substring(0, entry?.mainFile?.name.lastIndexOf(".")) + "_e" + entry?.entryId + ".ovr"
		overviewFile = new File( tempOverview2File ?: "" )
	}
	if (!overviewFile.exists()) 
	{
		def tempOverview3File = entry?.getFileFromObjects( "overview" )?.name
		overviewFile = new File( tempOverview3File ?: "" )
	}
        def objectPrefixIdx = 0
        def kwlString = "type: ossimImageChain\n"
        def quickLookFlag = false
        def enableCache = true
        def viewGeom = params.wmsView ? params.wmsView.getImageGeometry() : params.viewGeom
        def keepWithinScales = params.keepWithinScales ?: false
        def cacheTileSize = (threaded) ? 64 : 256 
        def jpeg2000 = (entry?.className?.equals("ossimKakaduNitfReader"))
        //def brightness = params.brightness ?: 0
        //def contrast = params.contrast ?: 1
        // we will use this for a crude check to see if we are within decimation levels
        //
        def geomPtr = null //createModelFromTiePointSet( entry );
        double scaleCheck = 1.0

        if ( ( geomPtr != null ) && params.wmsView && keepWithinScales )
        {
            scaleCheck = params.wmsView.getScaleChangeFromInputToView( geomPtr.get() )
        }
        if ( ( scaleCheck < 0.9 ) && entry )
        {
            // do we have enough zoom levels?
            // check to see if the decimation puts us smaller than the bounding rect of the smallest
            // res level scale
            //
            long maxSize = ( entry.width > entry.height ) ? entry.width : entry.height
            if ( ( maxSize * scaleCheck ) < ( maxSize / ( 2 ** entry.numberOfResLevels ) ) )
            {
                return [chain: null, kwl: "", prefixIdx: 0]
            }
        }

        switch ( quickLookFlagString?.toLowerCase() )
        {
            case "true":
            case "on":
                quickLookFlag = true
                break
        }
        if ( entry )
        {
	    totalBands = entry.numberOfBands
            if (totalBands >= 3) bands = params?.bands ?: "2,1,0"
	    if (bands == "default")
	    {
		if (totalBands < 3) bands = "0";
		else if (totalBands == 3) bands = "0,1,2";
		else bands = "2,1,0";
	    }

            // CONSTRUCT HANDLER
            //
            kwlString += "object${objectPrefixIdx}.type:${entry.className ? entry.className : 'ossimImageHandler'}\n"
            kwlString += "object${objectPrefixIdx}.entry:${entry.entryId}\n"
            //kwlString += "object${objectPrefixIdx}.frame:${(params?.frame) ?: 0}\n"
            //kwlString += "object${objectPrefixIdx}.process:${(params?.process) ?: "true"}\n"
            //kwlString += "object${objectPrefixIdx}.hdfPath:${(entry.hdfPath) ?: ""}\n"
            kwlString += "object${objectPrefixIdx}.filename:${entry.mainFile.name}\n"
            kwlString += "object${objectPrefixIdx}.width:${entry.width}\n"
            kwlString += "object${objectPrefixIdx}.height:${entry.height}\n"
            if (jpeg2000 && totalBands > 3 && validBandSelection(totalBands, bands)) kwlString += "object${objectPrefixIdx}.bands:(${bands})\n"
            if ( overviewFile.exists() )
            {
                kwlString += "object${objectPrefixIdx}.overview_file:${overviewFile}\n"
            }
 	    // Added a default band selector here because the sharpen and ortho filters do not handle disconnect/reconnect right now in multithreaded mode if they are first after the handler
	    // This is a core dump crash workaround
 	    if (jpeg2000 && totalBands > 3 && validBandSelection(totalBands, bands))
 	    {
 	      kwlString += "object${objectPrefixIdx}.bands:(${bands})\n"
 	      ++objectPrefixIdx
 	      kwlString += "object${objectPrefixIdx}.type:ossimBandSelector\n"
 	      kwlString += "object${objectPrefixIdx}.bands:(0,1,2)\n"
 	    }
        }
        ++objectPrefixIdx

      //kwlString += "object${objectPrefixIdx}.type:ossimCacheTileSource\n"
      //kwlString += "object${objectPrefixIdx}.id:${++idStart}\n"
      //kwlString += "object${objectPrefixIdx}.tile_size_xy:(64,64)\n"
      //++objectPrefixIdx




        // CONSTRUCT BAND SELECTION IF NEEDED
        //
        if (bands && !(jpeg2000 && totalBands > 3))
        {
            if ( entry )
            {
                if ( validBandSelection( entry.numberOfBands, bands ) )
                {
                    // the keywordlist in ossim takes a list of integers surrounded
                    // by parenthesis
                    //
                    kwlString += "object${objectPrefixIdx}.type:ossimBandSelector\n"
                    if ( bands != "default" )
                    {
                        kwlString += "object${objectPrefixIdx}.bands:(${bands})\n"
                    }
		    else if ( entry.numberOfBands > 2 )
                    {
                       kwlString += "object${objectPrefixIdx}.bands:rgb\n"
                    }
                    ++objectPrefixIdx
                }
                else
                {
                    log.error( "Invalid band selection (${bands}) for image ${entry.id}" )
                }
            }
            else
            {
                def validBands = true
                if ( params.maxBands )
                {
                    validBands = validBandSelection( maxBands, bands )
                }
                if ( validBands )
                {
                    // the keywordlist in ossim takes a list of integers surrounded
                    // by parenthesis
                    //
                    kwlString += "object${objectPrefixIdx}.type:ossimBandSelector\n"
                    if ( bands != "default" )
                    {
                        kwlString += "object${objectPrefixIdx}.bands:(${bands})\n"
                    }
                    ++objectPrefixIdx
                }
            }
        }

        if ( nullFlip )
        {
            kwlString += "object${objectPrefixIdx}.type:ossimNullPixelFlip\n"
            ++objectPrefixIdx
        }

        if (params.elevation)
        {
          // ELLIPSOID/MSL Remap
          if (params.msl && !entry?.geoidFlag)
          {
             kwlString += "object${objectPrefixIdx}.type:ossimElevRemapper\n"
             kwlString += "object${objectPrefixIdx}.remap_mode:geoid\n"
             ++objectPrefixIdx
          }
          else if (!params.msl && entry?.geoidFlag)
          {
             kwlString += "object${objectPrefixIdx}.type:ossimElevRemapper\n"
             kwlString += "object${objectPrefixIdx}.remap_mode:ellipsoid\n"
             ++objectPrefixIdx
          }

          kwlString += "object${objectPrefixIdx}.type:ossimScalarRemapper\n"
          kwlString += "object${objectPrefixIdx}.elevation:true\n"
          kwlString += "object${objectPrefixIdx}.scalar_type:ossim_float32\n"
          ++objectPrefixIdx
        }

        // CONSTRUCT HISTOGRAM STRETCHING IF NEEDED
        //
        if ( stretchMode && stretchModeRegion )
        {
            if(stretchMode!="none" && stretchMode!="remap" && (stretchModeRegion!="viewport")) 
            {
                if ( histogramFile.exists() )
                {
                    kwlString += "object${objectPrefixIdx}.type:ossimHistogramRemapper\n"
                    kwlString += "object${objectPrefixIdx}.histogram_filename:${histogramFile}\n"
                    kwlString += "object${objectPrefixIdx}.stretch_mode:${stretchMode}\n"
                    ++objectPrefixIdx
                }
                else
                {
                    log.error( "Histogram file does not exist and will ignore the stretch: ${histogramFile}" )
                }
            }
        }

        if (stretchMode == "remap")
        {
          kwlString += "object${objectPrefixIdx}.type:ossimPiecewiseRemapper\n"
          kwlString += "object${objectPrefixIdx}.remap_type:linear_native\n"
          kwlString += "object${objectPrefixIdx}.number_bands:1\n"
          //kwlString += "object${objectPrefixIdx}.scalar_type:OSSIM_UINT8\n"
          kwlString += "object${objectPrefixIdx}.band0.remap0:((0, 127, 0, 127), (128, 255, 128, 382))\n"
          kwlString += "object${objectPrefixIdx}.band0.remap1:((0, 382, 0, 255))\n"
          ++objectPrefixIdx
        }

        // if we are not the identity then add
	/*
        if ( ( brightness != 0 ) || ( contrast != 1 ) )
        {
            kwlString += "object${objectPrefixIdx}.type:ossimBrightnessContrastSource\n"
            kwlString += "object${objectPrefixIdx}.brightness: ${brightness ?: 0.0}\n"
            kwlString += "object${objectPrefixIdx}.contrast: ${contrast ?: 0.0}\n"

            ++objectPrefixIdx
        }
	*/
        // CONSTRUCT SHARPENING IF NEEDED
        //
        if ( sharpenMode )
        {
            switch ( sharpenMode )
            {
                case "light":
                    sharpenSigma = 0.5
                    sharpenWidth = 3
                    break
                case "heavy":
                    sharpenSigma = 1.0
                    sharpenWidth = 5.0
                    break
                default:
                    break
            }

        }
        if ( sharpenSigma && sharpenWidth )
        {
            kwlString += "object${objectPrefixIdx}.type:ossimImageSharpenFilter\n"
            kwlString += "object${objectPrefixIdx}.kernel_sigma:${sharpenSigma}\n"
            kwlString += "object${objectPrefixIdx}.kernel_width:${sharpenWidth}\n"
            ++objectPrefixIdx
        }

        if ( crs )
        {
            //CONSTRUCT IMAGE CACHE
            //
	  if (!threaded)
          {
            kwlString += "object${objectPrefixIdx}.type:ossimCacheTileSource\n"
            kwlString += "object${objectPrefixIdx}.tile_size_xy:(${cacheTileSize},${cacheTileSize})\n"
            kwlString += "object${objectPrefixIdx}.enable_cache:${enableCache}\n"
            ++objectPrefixIdx
	  }

            //CONSTRUCT RENDERER
            //
            kwlString += "object${objectPrefixIdx}.type:ossimImageRenderer\n"
            kwlString += "object${objectPrefixIdx}.max_levels_to_compute:0\n"
            kwlString += "object${objectPrefixIdx}.resampler.magnify_type:  ${interpolation}\n"
            kwlString += "object${objectPrefixIdx}.resampler.minify_type:  ${interpolation}\n"
            def kwl = new ossimKeywordlist()
            kwl.add( "object${objectPrefixIdx}.image_view_trans.type", "ossimImageViewProjectionTransform" )
            if ( viewGeom?.get() )
            {
                viewGeom?.get().saveState( kwl, "object${objectPrefixIdx}.image_view_trans.view_geometry." )
            }
            if ( quickLookFlag && entry )
            {
                if ( geomPtr != null )
                {
                    geomPtr.get().saveState( kwl, "object${objectPrefixIdx}.image_view_trans.image_geometry." )

                }
                geomPtr.delete()
            }
            kwlString += "${kwl.toString()}\n"
            kwl.delete()
            kwl = null
            ++objectPrefixIdx
            //CONSTRUCT VIEW CACHE
            //
            if (!threaded)
            {
              kwlString += "object${objectPrefixIdx}.type:ossimCacheTileSource\n"
              kwlString += "object${objectPrefixIdx}.tile_size_xy:(${cacheTileSize},${cacheTileSize})\n"
              kwlString += "object${objectPrefixIdx}.enable_cache:${enableCache}\n"
              ++objectPrefixIdx
            }

            //CONSTRUCT VIEW CACHE
            //
           // kwlString += "object${objectPrefixIdx}.type:ossimCacheTileSource\n"
           // kwlString += "object${objectPrefixIdx}.tile_size_xy:(64,64)\n"
           // kwlString += "object${objectPrefixIdx}.enable_cache:${enableCache}\n"
           // ++objectPrefixIdx
        }
        else if ( rotate || scale || pivot )
        {
            //CONSTRUCT IMAGE CACHE
            //
	  if (!threaded)
          {

            kwlString += "object${objectPrefixIdx}.type:ossimCacheTileSource\n"
	    kwlString += "object${objectPrefixIdx}.tile_size_xy:(${cacheTileSize},${cacheTileSize})\n"
            kwlString += "object${objectPrefixIdx}.enable_cache:${enableCache}\n"
	    ++objectPrefixIdx
          }

            //CONSTRUCT RENDERER
            //
            kwlString += "object${objectPrefixIdx}.type:ossimImageRenderer\n"
            kwlString += "object${objectPrefixIdx}.max_levels_to_compute:0\n"
            kwlString += "object${objectPrefixIdx}.resampler.magnify_type:  ${interpolation}\n"
            kwlString += "object${objectPrefixIdx}.resampler.minify_type:  ${interpolation}\n"
            kwlString += "object${objectPrefixIdx}.image_view_trans.type: ossimImageViewAffineTransform\n"
            if ( rotate )
            {
                kwlString += "object${objectPrefixIdx}.image_view_trans.rotate: ${rotate}\n"
            }
            if ( scale )
            {
                kwlString += "object${objectPrefixIdx}.image_view_trans.scale: (${scale},${scale})\n"
            }
            if ( pivot )
            {
                kwlString += "object${objectPrefixIdx}.image_view_trans.pivot: (${pivot})\n"
            }
      	    //CONSTRUCT VIEW CACHE
            //
            if (!threaded)
            {
              kwlString += "object${objectPrefixIdx}.type:ossimCacheTileSource\n"
              kwlString += "object${objectPrefixIdx}.enable_cache:${enableCache}\n"
              kwlString += "object${objectPrefixIdx}.tile_size_xy:(${cacheTileSize},${cacheTileSize})\n"
              ++objectPrefixIdx
            }

            //++objectPrefixIdx

            //CONSTRUCT VIEW CACHE
            //
         //   kwlString += "object${objectPrefixIdx}.type:ossimCacheTileSource\n"
         //   kwlString += "object${objectPrefixIdx}.enable_cache:${enableCache}\n"
         //   kwlString += "object${objectPrefixIdx}.tile_size_xy:(64,64)\n"
         //   ++objectPrefixIdx
        }
        else
        {
            //CONSTRUCT image cache depending on if parameters were supplied
            //
            if ( params )
            {
                kwlString += "object${objectPrefixIdx}.type:ossimCacheTileSource\n"
                kwlString += "object${objectPrefixIdx}.enable_cache:${enableCache}\n"
                kwlString += "object${objectPrefixIdx}.tile_size_xy:(${cacheTileSize},${cacheTileSize})\n"
                ++objectPrefixIdx
            }
            else
            {
                // because this is straight to an image let's just use the default
                // tile size
                kwlString += "object${objectPrefixIdx}.type:ossimCacheTileSource\n"
                ++objectPrefixIdx
            }
        }
        def chain = null
        if ( allocateChain )
        {
            chain = new joms.oms.Chain()
            chain.loadChainKwlString( kwlString )
        }

        //println "INPUT CHAIN *******************\n${kwlString}\n*************"
        [chain: chain, kwl: kwlString, prefixIdx: objectPrefixIdx]
    }

    /**
     * @param params
     * @return A Map that contains the content-type and the chain object
     */
    def createWriterChain(def params, def prefix = "")
    {
        def requestFormat = params?.format?.toLowerCase()
        def temporaryDirectory = params?.temporaryDirectory
        def tempFilenamePrefix = params?.filenamePrefix ?: "imageChainService"
        def ext = null
        def contentType = null
        def kwlString = ""
      switch ( requestFormat )
        {
            case ~/.*jpeg.*/:
                kwlString += "type:ossimJpegWriter\n"
                kwlString += "create_external_geometry:false\n"
                contentType = "image/jpeg"
                ext = ".jpg"
                break
            case ~/.*tiff.*/:
                kwlString += "type:ossimTiffWriter\n"
                kwlString += "image_type:tiff_tiled\n"
                kwlString += "create_external_geometry:false\n"
                contentType = "image/tiff"
                ext = ".tif"
                break
            case ~/.*jp2.*/:
                kwlString += "type:ossimKakaduJp2Writer\n"
                kwlString += "create_external_geometry:false\n"
                contentType = "image/jp2"
                ext = ".jp2"
                break
            case ~/.*png.*/:
                kwlString += "type:ossimPngWriter\n"
                kwlString += "create_external_geometry:false\n"
                contentType = "image/png"
                ext = ".png"
                break
            case ~/.*pdf.*/:
              kwlString += "type:${requestFormat}\n"
              kwlString += "create_external_geometry:false\n"
              contentType = "${requestFormat}"
              ext = ".pdf"
              break;
            default:
                log.error( "Unsupported FORMAT=${requestFormat}" )
                break
        }

        def writer = null
        def outputFileName = null
        def tempFile = null
        if ( ext != null )
        {
            tempFile = File.createTempFile( tempFilenamePrefix, ext, temporaryDirectory ? new File( temporaryDirectory ) : null );
            // now establish a writer
            //
            kwlString += "filename:${tempFile}\n"
            writer = new joms.oms.Chain();
            writer.loadChainKwlString( kwlString )
        }

        return [chain: writer, contentType: contentType, file: tempFile, ext: ext]
    }

    def createModelFromTiePointSet(def entry)
    {
        def gptArray = new ossimGptVector();
        def dptArray = new ossimDptVector();
        if ( entry?.tiePointSet )
        {
            def parser = parserPool.borrowObject()
            def tiepoints = new XmlSlurper( parser ).parseText( entry?.tiePointSet )
            parserPool.returnObject( parser )
            def imageCoordinates = tiepoints.Image.toString().trim()
            def groundCoordinates = tiepoints.Ground.toString().trim()
            def splitImageCoordinates = imageCoordinates.split( " " );
            def splitGroundCoordinates = groundCoordinates.split( " " );
            splitImageCoordinates.each {
                def point = it.split( "," )
                if ( point.size() >= 2 )
                {
                    dptArray.add( new ossimDpt( Double.parseDouble( point.getAt( 0 ) ),
                            Double.parseDouble( point.getAt( 1 ) ) ) )
                }
            }
            splitGroundCoordinates.each {
                def point = it.split( "," )
                if ( point.size() >= 2 )
                {
                    gptArray.add( new ossimGpt( Double.parseDouble( point.getAt( 1 ) ),
                            Double.parseDouble( point.getAt( 0 ) ) ) )
                }
            }
        }
        else if ( entry?.groundGeom ) // lets do a fall back if the tiepoint set is not set.
        {
            def coordinates = entry?.groundGeom.getCoordinates();
            if ( coordinates.size() >= 4 )
            {
                def w = width as double
                def h = height as double
                ( 0..<4 ).each {
                    def point = coordinates[it];
                    gptArray.add( new ossimGpt( coordinates[it].y, coordinates[it].x ) );
                }
                dptArray.add( new ossimDpt( 0.0, 0.0 ) )
                dptArray.add( new ossimDpt( w - 1, 0.0 ) )
                dptArray.add( new ossimDpt( w - 1, h - 1 ) )
                dptArray.add( new ossimDpt( 0.0, h - 1 ) )
            }
        }
        if ( ( gptArray.size() < 1 ) || ( dptArray.size() < 1 ) )
        {
            return null
        }
        return Util.createBilinearModel( dptArray, gptArray )
    }

    def grabOptimizedImageFromChain(def inputChain, def params)
    {
      def imageSource   = new omsImageSource( inputChain.getChainAsImageSource() )
      def renderedImage = new omsRenderedImage( imageSource )
      def result        = null

      try{

	//println (System.currentTimeMillis() as String)
	//Init.instance().setTrace("ossimImageHandlerMtAdaptor.*")
	//Init.instance().setTrace("ossimCacheTileSource.*")
	//Init.instance().setTrace("ossimScalarRemapper.*")
	//Init.instance().setTrace("ossimNitfTileSource.*")
	
        def image = renderedImage.getData();
	//println (System.currentTimeMillis() as String)

        ColorModel colorModel = renderedImage.colorModel

        boolean isRasterPremultiplied = true
        Hashtable<?, ?> properties = null

        def transparentFlag = params?.transparent?.equalsIgnoreCase( "true" )
        if ( image.numBands == 1  && image.dataBuffer instanceof java.awt.image.DataBufferByte )
        {
          result = Utility.convertToColorIndexModel( image.dataBuffer,
                  image.width,
                  image.height,
                  transparentFlag )
        }
        else
        {
          result = new BufferedImage(
                  colorModel,
                  image,
                  isRasterPremultiplied,
                  properties
          )
          if ( image.numBands == 3 )
          {
            if ( transparentFlag )
            {
              result = TransparentFilter.fixTransparency( new TransparentFilter(), result )
            }
            if ( params?.format?.equalsIgnoreCase( "image/gif" ) )
            {
              result = ImageGenerator.convertRGBAToIndexed( result )
            }
          }
        }


      }
      catch(def e)
      {
	println e.message
      }
      renderedImage.setImageSource(null)
      imageSource.setImageSource(null)


      result
    }
}
