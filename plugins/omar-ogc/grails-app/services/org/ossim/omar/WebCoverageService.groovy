package org.ossim.omar
import joms.oms.Chain
import joms.oms.ossimString
import joms.oms.WmsView
import joms.oms.Util
import joms.oms.ossimDpt
import joms.oms.ossimDrect
import org.springframework.beans.factory.InitializingBean
class WebCoverageService implements InitializingBean{

    static transactional = true
    def grailsApplication
    def temporaryDirectory
	def rasterChainService
    def getCoverage(def rasterEntries, def wcsCommand) {

        def coverageList = wcsCommand?.coverage?.split(",")
        def srcChains    = []
        //def wmsQuery  = new WMSQuery()
        /*
        def params    = wcsCommand.toMap();
        params.layers = params.coverage;
        if(params.layers&&params.layers.toLowerCase() == "raster_entry")
        {
            params.layers = ""
        }
        // for now I will hard code a max mosaic size
        //
        def max    = params.max?params.max as Integer:10
        if(max > 10) max = 10
        Utility.simpleCaseInsensitiveBind(wmsQuery, params)
        wmsQuery.max = max

        // for now we will sort by the date field if no layers are given
        //
        if(!wmsQuery.layers)
        {
            wmsQuery.sort  = wmsQuery.sort?:"acquisitionDate"
            wmsQuery.order = wmsQuery.order?:"desc"
        }
        */
        def crs = wcsCommand?.response_crs?wcsCommand?.response_crs:wcsCommand?.crs
        def requestFormat = wcsCommand?.format?.toLowerCase()
        def sharpenWidth = wcsCommand?.sharpen_width ?: null
        def sharpenSigma = wcsCommand?.sharpen_sigma ?: null
        def stretchMode = wcsCommand?.stretch_mode ? wcsCommand?.stretch_mode.toLowerCase(): null
        def stretchModeRegion = wcsCommand?.stretch_mode_region ?:null
        def bands = wcsCommand?.bands ?: ""
        def bounds = wcsCommand.bounds
        def wmsView = new WmsView()
        def defaultOutputName = "coverage"
		def wcsParams = wcsCommand.toMap()
        if(!wmsView.setProjection(crs))
        {
            log.error("Unsupported projection ${crs}")
            return null
        }
        if(!bounds)
        {
            log.error("Invalid Bounds found.  We currently require a BBOX along with either WIDTH HEIGHT or RESX RESY")
            return null
        }
        if(!wmsView.setViewDimensionsAndImageSize(bounds.minx, bounds.miny, bounds.maxx, bounds.maxy, bounds.width, bounds.height))
        {
            log.error("Unable to set the dimensions for the view bounds")
            return null
        }
       // def rasterEntries = wmsQuery.getRasterEntriesAsList();
        def objectPrefixIdx = 0
//        if(rasterEntries)
//        {
//            rasterEntries = rasterEntries?.reverse()
//        }
        def kwlString = null
        def imageRect = wmsView.getViewImageRect()
        def midPoint  = imageRect.midPoint()
        def x         = (int)(midPoint.x+0.5)
        def y         = (int)(midPoint.y+0.5)
        x            -= (bounds.width*0.5);
        y            -= (bounds.height*0.5);
        def w         = bounds.width
        def h         = bounds.height
		wcsParams.viewGeom = wmsView.getImageGeometry()
        rasterEntries.each{rasterEntry->
			def chainMap = rasterChainService.createRasterEntryChain(rasterEntry, wcsParams)
           // Util.setAllViewGeometries(chain.getChain(), wmsView.getImageGeometry().get(), false);
            //chainMap.chain.print()
            if(chainMap.chain&&(chainMap.chain.getChain()!=null))
            {
                srcChains.add(chainMap.chain)
            }
        }
        if(srcChains)
        {
            wcsParams.viewGeom = null

            def connectionId = 10000
            objectPrefixIdx = 0
            // now establish mosaic and cut to match the output dimensions
            kwlString = "type:ossimImageChain\n"
            kwlString += "object${objectPrefixIdx}.type:ossimImageMosaic\n"
            ++objectPrefixIdx
            if(requestFormat.contains("uint8")||
               requestFormat.contains("jpeg")||
                    ((stretchModeRegion == "viewport")&&
                     (stretchMode!="none"))
               )
            {
                kwlString += "object${objectPrefixIdx}.type:ossimScalarRemapper\n"
                kwlString += "object${objectPrefixIdx}.id:${connectionId}\n"
                ++connectionId
                ++objectPrefixIdx
            }
            kwlString += "object${objectPrefixIdx}.type:ossimRectangleCutFilter\n"
            kwlString += "object${objectPrefixIdx}.rect:(${x},${y},${w},${h},lh)\n"
            kwlString += "object${objectPrefixIdx}.cut_type:null_outside\n"
            kwlString += "object${objectPrefixIdx}.id:${connectionId}\n"
            ++objectPrefixIdx
            if((stretchModeRegion == "viewport")&&
                    (stretchMode!="none"))
            {
                kwlString += "object${objectPrefixIdx}.type:ossimImageHistogramSource\n"
                kwlString += "object${objectPrefixIdx}.id:${connectionId+1}\n"
                ++objectPrefixIdx
                kwlString += "object${objectPrefixIdx}.type:ossimHistogramRemapper\n"
                kwlString += "object${objectPrefixIdx}.id:${connectionId+2}\n"
                kwlString += "object${objectPrefixIdx}.stretch_mode:${stretchMode}\n"
                kwlString += "object${objectPrefixIdx}.input_connection1:${connectionId}\n"
                kwlString += "object${objectPrefixIdx}.input_connection2:${connectionId+1}\n"
                ++objectPrefixIdx
                connectionId+=2
            }
        }
        else
        {
            kwlString = "type:ossimMemoryImageSource\n"
            if(wcsParams.width&&wcsParams.height)
            {
                kwlString += "rect:(0,0,${wcsParams.width},${wcsParams.height},lh)\n"
                kwlString += "scalar_type:ossim_uint8\n"
                kwlString += "number_bands:1\n"
            }
        }
        def mosaic = new joms.oms.Chain();
        mosaic.loadChainKwlString(kwlString)
        srcChains.each{srcChain->
            mosaic.connectMyInputTo(srcChain)
        }
		
		def writer = rasterChainService.createWriterChain([format:wcsParams.format, 
			                                               temporaryDirectory:"${temporaryDirectory}",
														   filenamePrefix:"wcs"])
		def contentType = ""
        if(writer.chain&&(writer.chain.getChain() != null))
        {
            writer.chain.connectMyInputTo(mosaic)
            writer.chain.executeChain()
			contentType = writer.contentType
        }
        else
        {
            log.error("Unable to create a writer with the format ${wcsParams.format}")
        }
        writer.chain.deleteChain()
        mosaic.deleteChain()
        writer.chain = null
        mosaic = null
        srcChains.each{srcChain->
            srcChain.deleteChain()
        }
        srcChains = null
		
        [file:writer.file, outputName: "${defaultOutputName}${writer.ext}", contentType: "${contentType}"]
    }
    public void afterPropertiesSet()
    {
      temporaryDirectory = grailsApplication.config.export.workDir
    }
}
