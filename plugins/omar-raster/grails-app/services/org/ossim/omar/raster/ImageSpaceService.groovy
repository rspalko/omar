package org.ossim.omar.raste

import java.awt.Rectangle
import java.awt.image.BufferedImage
import joms.oms.WmsMap
import java.awt.image.DataBuffer
import joms.oms.ossimKeywordlist
import java.awt.image.DataBufferByte
import java.awt.Point
import java.awt.image.WritableRaster
import java.awt.image.ColorModel
import org.ossim.oms.image.omsImageSource
import org.ossim.omar.core.Utility
import joms.oms.ImageModel

class ImageSpaceService
{
  def imageChainService
  def grailsApplication

  static transactional = true

  def getPixels(Rectangle rect,
                def rasterEntry,
                def params)
  {
    // println "GetPixels: ${params}"
//		def newParams = params.clone()
//		newParams.image_cut = "${rect.x},${rect.y},${rect.width},${rect.height}"
    def w = rect.width as Integer
    def h = rect.height as Integer
    def maxlen = (w > h) ? w : h
    Integer cache_tile_size = 1024
    def threads = grailsApplication.config.threads?.enabled ?: false
    if (threads == true)
    {
      if (rasterEntry?.className.equals("ossimKakaduNitfReader"))
      {
        cache_tile_size = 512
      }
      if (maxlen < 1024) cache_tile_size = 512
      if (maxlen < 512) threads = false
      //if (maxlen < 512) cache_tile_size = 256
      //if (maxlen < 256) threads = false
      //def rotate = params?.rotate ?: null
      //if (rotate) threads= false
    }

    def result = null
    def idStart = 10000;
    def maxBands = 0
    def rasterChain = imageChainService.createImageChain( rasterEntry, params, true, threads ).chain
    def stretchMode = params.stretch_mode ? params.stretch_mode.toLowerCase() : "linear_auto_min_max" 
    def stretchModeRegion = params.stretch_mode_region ? params.stretch_mode_region.toLowerCase() : "global"
    def brightness = params.brightness ? (params.brightness as Double) : 0.0
    def contrast = params.contrast ? (params.contrast as Double) : 1.0
    def viewportStretch = false

    //rasterChain.print()
    if ( rasterChain )
    {
      maxBands = rasterChain.getChainAsImageSource().getNumberOfOutputBands()
      def objectPrefixIdx = 0
      def kwlString = ""
      def kwlStringBuilder = new StringBuilder()
      kwlStringBuilder << "type:ossimImageChain\n"
      kwlStringBuilder << "object${objectPrefixIdx}.type:ossimRectangleCutFilter\n"
      kwlStringBuilder << "object${objectPrefixIdx}.rect:(${rect.x},${rect.y},${rect.width},${rect.height},lh)\n"
      kwlStringBuilder << "object${objectPrefixIdx}.cut_type:null_outside\n"
      kwlStringBuilder << "object${objectPrefixIdx}.id:${++idStart}\n"
      ++objectPrefixIdx

      if ( stretchModeRegion == "viewport" && stretchMode != "none" && stretchMode != "remap") // && rasterEntry.numberOfBands == 1 && !remapFirst)
      {
        //def x = rect.x + rect.width / 2 - 128
        //def y = rect.y + rect.height / 2 - 128
	if (threads == true)
	{
	  viewportStretch = true
          kwlStringBuilder << "object${objectPrefixIdx}.type:ossimMultiThreadSequencer\n"
          kwlStringBuilder << "object${objectPrefixIdx}.num_threads:${grailsApplication.config.threads.number?:4}\n"
          kwlStringBuilder << "object${objectPrefixIdx}.cache_tile_size:${cache_tile_size}\n"
          kwlStringBuilder << "object${objectPrefixIdx}.use_cache:true\n"
          kwlStringBuilder << "object${objectPrefixIdx}.use_shared_handlers:true\n"
          kwlStringBuilder << "object${objectPrefixIdx}.create_histogram:true\n"
          kwlStringBuilder << "object${objectPrefixIdx}.id:${++idStart}\n"
          ++objectPrefixIdx
          kwlStringBuilder << "object${objectPrefixIdx}.type:ossimHistogramRemapper\n"
          kwlStringBuilder << "object${objectPrefixIdx}.id:${++idStart}\n"
          kwlStringBuilder << "object${objectPrefixIdx}.stretch_mode:${stretchMode}\n"
          ++objectPrefixIdx
	}
	else
	{
          //kwlStringBuilder << "object${objectPrefixIdx}.type:ossimCacheTileSource\n"
          //kwlStringBuilder << "object${objectPrefixIdx}.id:${++idStart}\n"
          //++objectPrefixIdx
          kwlStringBuilder << "object${objectPrefixIdx}.type:ossimImageHistogramSource\n"
          //kwlStringBuilder << "object${objectPrefixIdx}.area_of_interest.rect:(${x},${y},256,256,LH)\n"
	  //kwlStringBuilder << "object${objectPrefixIdx}.area_of_interest.rect:(${x},${y},${cache_tile_size},${cache_tile_size},LH)\n"
          kwlStringBuilder << "object${objectPrefixIdx}.id:${++idStart}\n"
          ++objectPrefixIdx
          kwlStringBuilder << "object${objectPrefixIdx}.type:ossimHistogramRemapper\n"
          kwlStringBuilder << "object${objectPrefixIdx}.id:${++idStart}\n"
          kwlStringBuilder << "object${objectPrefixIdx}.stretch_mode:${stretchMode}\n"
          kwlStringBuilder << "object${objectPrefixIdx}.input_connection1:${idStart - 2}\n"
          kwlStringBuilder << "object${objectPrefixIdx}.input_connection2:${idStart - 1}\n"
          ++objectPrefixIdx
	}
      }
      if ( ( brightness != 0 ) || ( contrast != 1 ) )
      {
          kwlStringBuilder << "object${objectPrefixIdx}.type:ossimBrightnessContrastSource\n"
          kwlStringBuilder << "object${objectPrefixIdx}.brightness: ${brightness ?: 0.0}\n"
          kwlStringBuilder << "object${objectPrefixIdx}.contrast: ${contrast ?: 1.0}\n"
	  kwlStringBuilder << "object${objectPrefixIdx}.id:${++idStart}\n"
          ++objectPrefixIdx
      }

/*

      if ( maxBands == 2 )
      {
        kwlStringBuilder << "object${objectPrefixIdx}.type:ossimBandSelector\n"
        kwlStringBuilder << "object${objectPrefixIdx}.bands:(0)\n"
        kwlStringBuilder << "object${objectPrefixIdx}.id:${++idStart}\n"
        ++objectPrefixIdx
      }
      else if ( maxBands > 3 )
      {
        kwlStringBuilder << "object${objectPrefixIdx}.type:ossimBandSelector\n"
        kwlStringBuilder << "object${objectPrefixIdx}.bands:(0,1,2)\n"
        kwlStringBuilder << "object${objectPrefixIdx}.id:${++idStart}\n"
        ++objectPrefixIdx
      }
      else
      {
        //++idStart
      }
*/
      kwlStringBuilder << "object${objectPrefixIdx}.type:ossimScalarRemapper\n"
      kwlStringBuilder << "object${objectPrefixIdx}.id:${++idStart}\n"
      ++objectPrefixIdx

      if (threads == true && !viewportStretch)
      {
        kwlStringBuilder << "object${objectPrefixIdx}.type:ossimMultiThreadSequencer\n"
        kwlStringBuilder << "object${objectPrefixIdx}.num_threads:${grailsApplication.config.threads.number?:4}\n"
        kwlStringBuilder << "object${objectPrefixIdx}.cache_tile_size:${cache_tile_size}\n"
        kwlStringBuilder << "object${objectPrefixIdx}.use_cache:true\n"
        kwlStringBuilder << "object${objectPrefixIdx}.use_shared_handlers:true\n"
        kwlStringBuilder << "object${objectPrefixIdx}.create_histogram:false\n"
        kwlStringBuilder << "object${objectPrefixIdx}.id:${++idStart}\n"
      }

      //kwlStringBuilder << "object${objectPrefixIdx}.type:ossimRectangleCutFilter\n"
      //kwlStringBuilder << "object${objectPrefixIdx}.rect:(${rect.x},${rect.y},${rect.width},${rect.height},lh)\n"
      //kwlStringBuilder << "object${objectPrefixIdx}.cut_type:null_outside\n"
      //kwlStringBuilder << "object${objectPrefixIdx}.id:${++idStart}\n"
      //++objectPrefixIdx
     // kwlStringBuilder << "object${objectPrefixIdx}.type:ossimCacheTileSource\n"
     // kwlStringBuilder << "object${objectPrefixIdx}.id:${++idStart}\n"
     // ++objectPrefixIdx

      kwlString = kwlStringBuilder.toString()
      //kwlString = kwlStringBuilder.toString();
      // println kwlString
      //	println "*"*40


      def chipChain = new joms.oms.Chain();
      chipChain.loadChainKwlString( kwlString )

      //chipChain.print()
      //println "-"*40
      chipChain.connectMyInputTo( rasterChain )
      result = imageChainService.grabOptimizedImageFromChain( chipChain, params )
      chipChain.deleteChain()
      rasterChain.deleteChain()
      chipChain.delete()
      rasterChain.delete()
      chipChain = null
      rasterChain = null
    }
    result
  }

  BufferedImage getPixelsOld(Rectangle rect,
                             String inputFile,
                             int entry,
                             def inputBandCount,
                             BigDecimal scale,
                             def params)
  {

    def sharpenMode = params.sharpen_mode ?: ""
    def bands = params?.bands ?: ""
    def rotate = params?.rotate ?: "0.0"
    int viewableBandCount = 1
    if ( sharpenMode.equals( "light" ) )
    {
      params.sharpen_width = "3"
      params.sharpen_sigma = ".5"
    }
    else if ( sharpenMode.equals( "heavy" ) )
    {
      params.sharpen_width = "5"
      params.sharpen_sigma = "1"
    }
    if ( inputBandCount >= 3 )
    {
      viewableBandCount = 3
    }
    def bandSelectorCount = bands ? bands.split( "," ).length : 0
    if ( bandSelectorCount > 0 )
    {
      if ( bandSelectorCount >= 3 )
      {
        viewableBandCount = 3;
      }
      else
      {
        viewableBandCount = 1;
      }
    }

    byte[] data = new byte[rect.width * rect.height * 3]
    def kwl = new ossimKeywordlist();
    params.each { name, value ->
      kwl.add( name, value as String )
    }
    kwl.add( "viewable_bands", "${viewableBandCount}" )
    kwl.add( "rotate", "${rotate}" )

    WmsMap.getUnprojectedMap(
        inputFile,
        entry,
        scale,
        rect.x, rect.x + rect.width - 1, rect.y, rect.y + rect.height - 1,
        data,
        kwl
    )
    DataBuffer dataBuffer = new DataBufferByte( data, data.size() )
    int pixelStride = viewableBandCount
    int lineStride = viewableBandCount * rect.width
    int[] bandOffsets = null;
    if ( viewableBandCount == 1 )
    {
      bandOffsets = [0] as int[]
    }
    else
    {
      bandOffsets = [0, 1, 2] as int[]
    }
    def image;
    if ( viewableBandCount == 1 )
    {
      image = Utility.convertToColorIndexModel( dataBuffer, rect.width as Integer, rect.height as Integer, false )
    }
    else
    {
      Point location = null
      WritableRaster raster = WritableRaster.createInterleavedRaster(
          dataBuffer,
          rect.width as Integer,
          rect.height as Integer,
          lineStride,
          pixelStride,
          bandOffsets,
          location )

      ColorModel colorModel = omsImageSource.createColorModel( raster.sampleModel )

      boolean isRasterPremultiplied = true
      Hashtable<?, ?> properties = null

      image = new BufferedImage(
          colorModel,
          raster,
          isRasterPremultiplied,
          properties )
    }
    return image
  }

  def computeUpIsUp(String filename, Integer entryId)
  {
    Double upIsUp = 0.0

    def imageSpaceModel = new ImageModel()
    if ( imageSpaceModel.setModelFromFile( filename, entryId as Integer ) )
    {
      upIsUp = imageSpaceModel.upIsUpRotation();
      imageSpaceModel.destroy()
      imageSpaceModel.delete()
    }

    return upIsUp
  }

  def computeIlluminationIsUp(String filename, Integer entryId)
  {
      Double illumIsUp = 0.0

      def imageSpaceModel = new ImageModel()
      if ( imageSpaceModel.setModelFromFile(filename, entryId as Integer) )
      {
          illumIsUp = imageSpaceModel.illuminationIsUp();
          imageSpaceModel.destroy()
          imageSpaceModel.delete()
      }

      return illumIsUp
  }

