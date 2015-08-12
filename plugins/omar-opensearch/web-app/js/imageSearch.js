/**
 * Created by gpotts on 8/11/15.
 */
var WfsTypeNameModel = Backbone.Model.extend({
    idAttribute:"typeName",
    defaults:{
        typeName:"omar:raster_entry"
    },
    initialize:function(params)
    {
    }
});

OMAR.views.imageSearch = Backbone.View.extend({
    el:"#ResultsView",
    initialize:function(params)
    {
        this.wfsTypeName = new WfsTypeNameModel();
        this.dataModelView = new OMAR.views.DataModelView({
            wfsTypeNameModel:this.wfsTypeName,
            el:"#ResultsView",
            containerEl:"#ResultsView"
        });

    },
    render:function()
    {
       // this.dataModelView.render();
        this.dataModelView.wfsUrlChanged();

    }
});

OMAR.imageSearch = null;
OMAR.pages.imageSearch = (function($, params){
    if(!OMAR.imageSearch)
    {
        OMAR.imageSearch = new OMAR.views.imageSearch(params);
    }
    else
    {

    }
    return OMAR.imageSearch;
});

$(document).ready(function () {
    $.ajaxSetup({cache: false}); // turn cache off for ajax

    if(!OMAR.imageSearch) {
        init();
    }
    $(window).resize(function(){
        OMAR.imageSearch.dataModelView.resizeView();
    });
    $("body").css({"visibility":"visible"});
});


