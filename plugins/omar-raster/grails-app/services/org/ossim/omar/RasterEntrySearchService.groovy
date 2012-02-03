package org.ossim.omar
//import javax.jws.WebParam

import org.hibernate.FetchMode as FM
import org.hibernate.CacheMode as CM
//import org.ossim.omar.RasterEntryMetadata

import org.hibernate.FetchMode
import org.hibernate.CacheMode
import org.hibernate.criterion.*
//import org.ossim.postgis.Geometry

import com.vividsolutions.jts.geom.Polygon
import org.hibernate.ScrollableResults
import org.springframework.beans.factory.InitializingBean
import java.awt.Polygon

import geoscript.workspace.PostGIS
import geoscript.filter.Filter

class RasterEntrySearchService implements InitializingBean
{
  def grailsApplication

  //static expose = ['xfire']

  static transactional = false

  def propertyNames

  List<RasterEntryQuery> runQuery(RasterEntryQuery rasterEntryQuery, Map<String, String> params)
  {
    def max = null;
    if ( params?.max != null ) max = (params.max as Integer);
    if ( max < 1 ) return null;
    def criteriaBuilder = RasterEntry.createCriteria();
    def x = {
      createAlias("rasterDataSet", "rds")
      if ( max )
      {
        setMaxResults(max)
      }
      if ( params?.offset )
      {
        setFirstResult(params.offset as Integer)
      }
      if ( params?.sort && params?.order )
      {
        if ( params?.sort == "id" || params?.sort in propertyNames )
        {
          def sortColumn = params?.sort
          def order = params?.order
          def ordering = (order == "asc") ? Order.asc(sortColumn) : Order.desc(sortColumn)

          addOrder(ordering)
        }

        //setFetchMode("rasterEntry", FetchMode.JOIN)
        join "rasterEntry"
      }
    }

    def criteria = criteriaBuilder.buildCriteria(x)
    def clause = rasterEntryQuery?.createClause()
    if ( clause )
    {
      criteria.add(clause)
    }

    def rasterEntries = criteria.list()

    if ( rasterEntries )
    {
      RasterFile.withCriteria {
        eq("type", "main")
        inList("rasterDataSet", rasterEntries.rasterDataSet)
      }
    }

    return rasterEntries
  }



  List<Polygon> getGeometries(RasterEntryQuery rasterEntryQuery, Map<String, String> params)
  {
    def max = null;
    if ( params?.max != null ) max = (params.max as Integer);
    if ( max < 1 ) return null;
    def criteriaBuilder = RasterEntry.createCriteria();
    def x =
      {
        projections { property("groundGeom") }
        firstResult(params.offset as Integer)
        maxResults(max)
        cacheMode(CacheMode.GET)
      }
    def criteria = criteriaBuilder.buildCriteria(x)
    criteria.add(rasterEntryQuery?.createClause())

    return criteria.list()
    /*
    def criteria = RasterEntry.createCriteria();

    criteria.setProjection(Projections.property("groundGeom"))

    if ( params?.offset )
    {
      criteria.setFirstResult(params.offset as Integer)
    }

    if ( params?.max )
    {
      criteria.setMaxResults(params.max as Integer)
    }

    criteria.setCacheMode(CacheMode.GET)
    rasterEntryQuery.addToCriteria(criteria)

    def geometries = criteria.instance.list()

    return geometries
    */
  }

  void scrollGeometries(RasterEntryQuery rasterEntryQuery, Map<String, String> params, Closure closure)
  {
    def max = null;
    if ( params?.max ) max = (params.max as Integer);
    if ( max < 1 ) return;
    def criteriaBuilder = RasterEntry.createCriteria();

    def x = {
      projections { property("groundGeom") }

      if ( max )
      {
        maxResults(max)
      }

      if ( params?.offset )
      {
        firstResult(params.offset as Integer)
      }
      cacheMode(CacheMode.GET)
    }

    def criteria = criteriaBuilder.buildCriteria(x)

    criteria.add(rasterEntryQuery?.createClause())

    def results = criteria.scroll()
    def status = results.first()

    while ( status )
    {
      def geom = results.get(0)

      closure.call(geom)

      status = results.next()
    }

    results.close()
  }

  int getCount(RasterEntryQuery rasterEntryQuery)
  {
    def criteriaBuilder = RasterEntry.createCriteria();
    def x =
      {
        projections { rowCount()}
      }
    def criteria = criteriaBuilder.buildCriteria(x)
    criteria.add(rasterEntryQuery?.createClause())
    def totalCount = criteria.list().get(0) as int
    return totalCount
  }


  def getWmsImageLayers(String[] layerNames)
  {
    def layers = null

    if ( layerNames )
    {
      def c = {
        or {
          layerNames?.each {name ->
            if ( name )
            {
              try
              {
                eq('id', java.lang.Long.valueOf(name))
              }
              catch (java.lang.Exception e)
              {
                eq('title', name)
                eq('indexId', name)
              }
            }
          }
        }
      }

      layers = RasterEntry.createCriteria().list(c)
    }

    return layers
  }


  def getWmsImageLayers(String filterText)
  {
    def layers = null

    if ( filterText )
    {
      def layerName = 'raster_entry'
      def username = grailsApplication.config.dataSource.username
      def password = grailsApplication.config.dataSource.password
      def database = grailsApplication.config.dataSource.url - 'jdbc:postgresql_postGIS:'

      def workspace = new PostGIS([user: username, password: password], database)
      def layerNames = workspace[layerName]?.getFeatures(new Filter(filterText))?.collect { it.id.split('\\.')[-1] }

      layers = getWmsImageLayers(layerNames as String[])
      workspace?.close()
    }

    return layers
  }

  def findRasterEntries(def rasterIdList)
  {
    def rasterEntries = RasterEntry.createCriteria().list() {
      rasterIdList.each() {name ->
        if ( name ==~ /\d+/ )
        {
          eq('id', Long.valueOf(name))
        }
        else
        {
          or {
            eq('indexId', name)
            eq('title', name)
          }
        }
      }
    }

    return rasterEntries
  }

  void afterPropertiesSet()
  {
    propertyNames = grailsApplication.getDomainClass("org.ossim.omar.RasterEntry")?.properties.name
  }
}
