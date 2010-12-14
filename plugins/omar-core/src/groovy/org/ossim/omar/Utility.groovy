package org.ossim.omar

import org.apache.commons.collections.map.CaseInsensitiveMap
import java.awt.image.BufferedImage
import java.awt.image.ColorModel
import java.awt.image.WritableRaster
import javax.imageio.ImageTypeSpecifier
import java.awt.image.SampleModel
import java.awt.image.IndexColorModel
import java.awt.image.DataBuffer;

class Utility
{
  static def createTypeMap(def featureClass)
  {
    def fields = featureClass.declaredFields
    def typeMap = [:]

    for ( field in fields )
    {
      if ( field.name != "metaClass" )
      {
        typeMap[field.name] = field.type
      }
    }

    return typeMap
  }
  static void removeEmptyParams(def params)
  {
    def nullMap = params?.findAll {entry -> (entry.value == "" || entry.value == "null")}
    nullMap?.each {params?.remove(it.key)}
  }
  static void simpleCaseInsensitiveBind(def target, def paramMap)
  {
    def keys = target.properties.keySet()
    def tempParams = new CaseInsensitiveMap(paramMap)

    keys.each{
      def value = tempParams.get(it)
      if(value)
      {
        target.setProperty("${it}", value)
      }
    }
  }
  static def convertToColorIndexModel(def dataBuffer, def width, def height, def transparentFlag)
  {
    ImageTypeSpecifier isp = ImageTypeSpecifier.createGrayscale(8, DataBuffer.TYPE_BYTE, false);
    ColorModel colorModel
    SampleModel sampleModel = isp.getSampleModel(width, height)
    if ( !transparentFlag )
    {
      colorModel = isp.getColorModel();
    }
    else
    {
      int[] lut = new int[256]
      (0..<lut.length).each {i ->
        lut[i] = ((0xff << 24) | (i << 16) | (i << 8) | (i));
      }
      lut[0] = 0xff000000
      colorModel = new IndexColorModel(8, lut.length, lut, 0, true, 0, DataBuffer.TYPE_BYTE)
    }
    WritableRaster raster = WritableRaster.createWritableRaster(sampleModel, dataBuffer, null)
    return new BufferedImage(colorModel, raster, false, null);
   
  }
  /**
   * This will extract all WMS paramters this includes GetMap, GetCapabilities, and
   * GetFeatureInfo REQUESTS.  It will do this in a case insensitive way and return
   * a new map
   */
  static Map keepOnlyWMSParams(def map)
  {
    def tempParams = new CaseInsensitiveMap()
    map.each { tempParams.put(it.key, it.value)}

    return tempParams.subMap(["version", "request", "layers", "styles",
        "srs", "crs", "bbox", "width", "height", "format",
        "transparent", "bgcolor", "exceptions", "time",
        "elevation", "updatesequence", "query_layers",
        "info_format", "i", "j"
    ])
  }

  static Map keepOnlyParams(def map, def params)
  {
    def tempParams = new CaseInsensitiveMap()
    map.each { tempParams.put(it.key, it.value)}
    def listOfParams = ["version", "request", "layers", "styles",
        "srs", "bbox", "width", "height", "format",
        "transparent", "bgcolor", "exceptions", "time",
        "elevation"
    ]
    if ( params )
    {
      params.each {
//        println "adding ${it}"
        listOfParams.add(it)
      }
    }

    return tempParams.subMap(listOfParams)
  }

  public static def parseSearchTag(def name, def value)
  {
    def nameValue = "${name}${value}"

    def pattern = /(.*\.)?(.*)=(.*)/
    def matcher = nameValue =~ pattern

    def results = [:]

    if ( matcher )
    {
      if ( matcher[0][1] )
      {
        results["property"] = matcher[0][1] - "."
        results["tag"] = matcher[0][2]
        results["content"] = matcher[0][3]
      }
      else
      {
        results["property"] = matcher[0][2]
        results["value"] = matcher[0][3]
      }
    }
    else
    {
      results["property"] = name
      results["value"] = value
    }

    return results
  }

}