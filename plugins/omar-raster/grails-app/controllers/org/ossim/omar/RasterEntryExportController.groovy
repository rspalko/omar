package org.ossim.omar

import org.ossim.omar.core.DateUtil


class RasterEntryExportController
{
  def exportService
  def rasterEntrySearchService
  def grailsApplication

  def index = {
  }

  def export = {
    def format = params.format
    def queryParams = new RasterEntryQuery()

    bindData(queryParams, params)

    queryParams.startDate = DateUtil.initializeDate("startDate", params)
    queryParams.endDate = DateUtil.initializeDate("endDate", params)

    def objects = rasterEntrySearchService.runQuery(queryParams, params)

//    def fields = ["id", "acquisitionDate", "groundGeom"] as String[]
//    def labels = ["id", "acquisition_date", "ground_geom"] as String[]

//    def domainClass = grailsApplication.getArtefact("Domain", "org.ossim.omar.RasterEntry")
//    def fields = domainClass.properties*.name
//    def labels = domainClass.properties*.naturalName

    def fields = grailsApplication.config.export.rasterEntry.fields
    def labels = grailsApplication.config.export.rasterEntry.labels
    def formatters = grailsApplication.config.export.rasterEntry.formatters

    def (file, mimeType) = exportService.export(
            format,
            objects,
            fields,
            labels,
            formatters,
            [featureClass: RasterEntry.class]
    )

    response.setHeader("Content-disposition", "attachment; filename=${file.name}");
    response.contentType = mimeType
    response.outputStream << file.newInputStream()
    response.outputStream.flush()
  }
}
