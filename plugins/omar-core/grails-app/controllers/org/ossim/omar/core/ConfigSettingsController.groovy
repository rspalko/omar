package org.ossim.omar.core

import grails.converters.JSON

class ConfigSettingsController {
    def grailsApplication
    def federationConfigSettingsService
    def index() {
    }

    def action(){
        def result =[:]
        def method = request?.method?.toUpperCase()

        def configService = grailsApplication.mainContext.getBean("${params.settingsName}ConfigSettingsService")

        switch(method)
        {
            case "GET":
                def record = configService.getSettingsRecord();
                result = [name: record.name, settings: record.settings];
                render (result as JSON)
                break;

            case "PUT":
                def json = request.JSON
                def record
                if (json?.settings)
                {
                    record = configService.updateSettings(json.settings.toString())
                }
                if (record)
                {
                    result = [name: record.name, settings: record.settings];
                }
                render (result as JSON)
                break;
        }
        null
    }
}
