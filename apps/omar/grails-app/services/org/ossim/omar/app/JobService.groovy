package org.ossim.omar.app

import org.apache.commons.collections.map.CaseInsensitiveMap
import org.ossim.omar.Job
import org.ossim.omar.JobStatus
import org.ossim.omar.chipper.FetchDataCommand
import org.ossim.omar.core.HttpStatus
import org.ossim.omar.core.Utility
import org.apache.commons.io.FilenameUtils
import com.budjb.rabbitmq.RabbitMessageBuilder
import org.ossim.oms.job.AbortMessage

class JobService {
  def springSecurityService
  def grailsApplication
  def diskCacheService
  static columnNames = [
          'id','jobId', 'jobDir', 'type', 'name', 'username', 'status', 'statusMessage', 'percentComplete', 'submitDate', 'startDate', 'endDate'
  ]

  def createTableModel()
  {
    def clazz = Job.class
    def domain = grailsApplication.getDomainClass( clazz.name )

    def tempColumnNames = columnNames.clone()
    tempColumnNames.remove("jobDir")
    def columns = tempColumnNames?.collect {column->
      def property = ( column == 'id' ) ? domain?.identifier : domain?.getPersistentProperty( column )
      def sortable = !(property?.name in ["type"])
      [field: property?.name, type: property?.type, title: property?.naturalName, sortable: sortable]
    }
    columns.remove("jobDir")
    def tableModel = [
            columns: [columns]
    ]
    //  println tableModel
    return tableModel
  }
  def cancel(def params)
  {
    def result = [httpStatus:HttpStatus.OK, message:""]
    def caseInsensitiveParams = new CaseInsensitiveMap(params)

    if(caseInsensitiveParams.jobId)
    {
      def jobId = caseInsensitiveParams.jobId
      def jobCallback
      def jobStatus
      Job.withTransaction{
        def job     = Job.findByJobId(jobId)
        jobCallback = job?.jobCallback
        jobStatus   = job?.status
      }

      if(jobCallback)
      {
        if(jobStatus==JobStatus.RUNNING)
        {
          try{
            def messageBuilder = new RabbitMessageBuilder()
            messageBuilder.send(jobCallback, new AbortMessage(jobId:jobId).toJsonString())
          }
          catch(e)
          {
            println "CAUGHT EXCEPTION ---------------- ${e}"
            result.httpStatus = HttpStatus.BAD_REQUEST
            result.message=e.toString()
          }
        }
        else
        {
          result.httpStatus = HttpStatus.BAD_REQUEST
          result.message="Job ${jobId} not running.  Can only cancel running jobs."
        }
      }
      else
      {
        result.httpStatus = HttpStatus.BAD_REQUEST
        result.message="Job ${jobId} currently has no callback to allow for canceling"
      }
    }
    else
    {
      result.httpStatus = HttpStatus.BAD_REQUEST
      result.message="Can't cancel. No job id given."
    }
    result
  }
  def remove(def params)
  {
    def result = [success:false]
    def jobArchive
    def jobDir
    def row

    try{
      Job.withTransaction {

        if(params?.id != null) row = Job.findById(params?.id?.toInteger());
        else if(params?.jobId) row = Job.findByJobId(params.jobId);
        if(row)
        {
          jobArchive = row.getArchive()
          jobDir = row.jobDir as File
          if (!jobArchive?.exists()) {
            jobArchive = jobDir
          }
          row.delete(flush:true)
          result.success = true;
        }
      }
    }
    catch(e)
    {
      result.success = false;
      result.message = e.toString()
    }
    if(result.success)
    {
      try {
        if (jobArchive?.exists()) {
          if (jobArchive?.isDirectory()) {
            jobArchive?.deleteDir()
          } else {
            jobArchive?.delete()
          }
        }
        if(jobDir.exists())
        {
          if (jobDir?.isDirectory()) {
            jobDir?.deleteDir()
          } else {
            jobDir?.delete()
          }
        }
      }
      catch(e)
      {
        //println "ERROR!!!!!!!!!!!!!!!! ${e}"
        result.success = false;
        result.message = e.toString()
      }
    }
    result
  }

  def updateJob(def jsonObj) {
    try{
      if(jsonObj.jobId != null)
      {

        // println "json"

        Job.withTransaction{
          def record = Job.findByJobId(jsonObj.jobId)
          if(record)
          {
            // println "WILL UPDATE ${jsonObj.jobId} WITH NEW STATUS === ${jsonObj.status}"

            def status = "${jsonObj.status?.toUpperCase()}"

            if(jsonObj.statusMessage != null) record.statusMessage = jsonObj.statusMessage

            if(jsonObj.status != null)
            {
              record.status  = JobStatus."${status}"
              switch(record.status)
              {
                case JobStatus.READY:
                  record.submitDate = new Date()
                  break
                case JobStatus.CANCELED:
                case JobStatus.FINISHED:
                case JobStatus.FAILED:
                  record.endDate = new Date()
                  break
                case JobStatus.RUNNING:
                  record.startDate = new Date()
                  break
              }
            }
            if(jsonObj.percentComplete != null) record.percentComplete = jsonObj.percentComplete
            if(jsonObj.jobCallback != null) record.jobCallback = jsonObj.jobCallback

            record.save(flush:true)
          }
          else
          {
            println "Job ID not found: ${jsonObj.jobId}"
          }
        }
      }
      else
      {
        println "Job ID NULL????????????????????????? ${jsonObj.jobId}"
      }
    }
    catch(e)
    {
      println "ERROR!!!!!!!!!!!!!!!!!! ${e}"
    }
  }
  def getByAllJobIds(def jobIds)
  {
    def splitArray = jobIds.split(",")
    def rows
    //  println "(${splitArray.collect{"'${it}'" }.join(',')}"
    Job.withTransaction{
      def tempRows = Job.withCriteria {
        sqlRestriction "(job_id in (${splitArray.collect{"'${it}'" }.join(',')}))"
      }
      rows = tempRows.collect { row ->
        columnNames.inject( [:] ) { a, b -> a[b] = row[b].toString(); a }
      }
    }
    [total: rows.length, rows: rows]
  }
  def getByJobId(def jobId)
  {
    def result = [:]

    if(jobId)
    {
      Job.withTransaction{
        result = Job.findByJobId(jobId)
      }
    }

    result
  }
  def download(def params, def response)
  {
    //println params
    def caseInsensitiveParams = new CaseInsensitiveMap(params)
    def httpStatus = HttpStatus.OK
    def errorMessage = ""
    def archive
    def jobStatus
    def jobFound
    def contentType = "text/plain"
    if(caseInsensitiveParams.jobId)
    {
      def job
      Job.withTransaction {
        job = Job.findByJobId(caseInsensitiveParams.jobId)
        archive = job?.getArchive()
        jobStatus = job?.status
        jobFound = job != null
      }
      if(jobFound) {
        if (jobStatus == JobStatus.FINISHED) {
          archive = job?.getArchive()
         // println "ARCHIVE ====== ${archive}"
          if (archive) {
            def ext = FilenameUtils.getExtension(archive.toString()).toLowerCase()

           // println "EXT === ${ext}"
            switch (ext) {
              case "zip":
                contentType = "application/octet-stream"
                break
              case "tgz":
                contentType = "application/x-compressed"
                break
            }
          } else {
            httpStatus = HttpStatus.NOT_FOUND
            errorMessage = "ERROR: Archive for Job ${caseInsensitiveParams.jobId} is no longer present"
          }
        } else {
          httpStatus = HttpStatus.NOT_FOUND
          errorMessage = "ERROR: Can only download finished jobs.  The current status is ${job?.status.toString()}"
        }
      }
      else
      {
        httpStatus = HttpStatus.NOT_FOUND
        errorMessage = "ERROR: Job ${caseInsensitiveParams.jobId} not found"
      }


      if(errorMessage)
      {
        response.status = httpStatus
        response.contentType = contentType
        response.sendError(response.status, errorMessage)
        //response.outputStream.write(errorMessage.bytes)
      }
      else
      {
        try
        {

          if(archive.isFile())
          {
            response.setHeader("Accept-Ranges", "bytes");
            def tempFile = archive as File
            response.setContentLength((int)tempFile.length());
          }
          response.status = httpStatus
          response.setHeader( "Content-disposition", "attachment; filename=${archive.name}" )
          Utility.writeFileToOutputStream(archive, response.outputStream)
        }
        catch(e)
        {
          response.status = HttpStatus.BAD_REQUEST
          errorMessage = "ERROR: ${e}"
          response.contentType = "text/plain"
          response.outputStream.write(errorMessage.bytes)
        }
      }

    }
  }

  def getData(FetchDataCommand cmd)
  {

    //println params

//    def max = ( params?.rows as Integer ) ?: 10
//    def offset = ( ( params?.page as Integer ?: 1 ) - 1 ) * max
//    def sort = params?.sort ?: 'id'
//    def dir = params?.order ?: 'asc'
//    def x = [max: max, offset: offset, sort: sort, dir: dir]
//
//    println x
    def total = 0
    def rows  = [:]
    Job.withTransaction{
      total = Job.createCriteria().count {
        if ( cmd.filter )
        {
          sqlRestriction cmd.filter
        }
      }

      def tempRows = Job.withCriteria {
        if ( cmd.filter )
        {
          sqlRestriction cmd.filter
        }
        //      projections {
        //        columnNames.each {
        //          property(it)
        //        }
        //      }
        maxResults( cmd.rows )
        order( cmd.sort, cmd.order )
        firstResult( ( cmd.page - 1 ) * cmd.rows )
      }
      rows = tempRows.collect { row ->
        columnNames.inject( [:] ) { a, b -> a[b] = row[b].toString(); a }
      }
    }

    return [total: total, rows: rows]
  }

}