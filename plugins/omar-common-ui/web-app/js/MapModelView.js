OMAR.models.Map = Backbone.Model.extend({

});

var zoomInButton;
var zoomFullResScale;
var aoiLayer;
var bounds;

var convert = new CoordinateConversion();
OMAR.views.Map = Backbone.View.extend({
    el:"#mapContainer",
    initialize:function(params){
        this.setElement(this.el);
        if(params.theme)
        {
            this.theme = params.theme;
        }
        else
        {
            this.theme = null;
        }
        this.mapEl = $(this.el).find("#map")[0];
        this.toolBar = $(this.el).find("#mapToolBar")[0];
    },
    reset:function()
    {
        if(this.map)
        {
            this.map.destroy();
            this.map = null;
        }
        if(this.mapEl)
        {
            this.map = new OpenLayers.Map({
                div: this.mapEl,
                theme:this.theme,
                layers: [
                    new OpenLayers.Layer.WMS( "OpenLayers WMS",
                        "http://vmap0.tiles.osgeo.org/wms/vmap0",
                        {layers: 'basic'} )
                ],
                controls: [
                    new OpenLayers.Control.Navigation({
                        dragPanOptions: {
                            enableKinetic: true
                        }
                    }),
                    new OpenLayers.Control.PanZoom(),
                    new OpenLayers.Control.Attribution(),
                    new OpenLayers.Control.Scale(),
                    new OpenLayers.Control.ScaleLine(),
                    new OpenLayers.Control.Graticule({
                        visible:false,
                        numPoints:2,
                        layerName:"DD Graticule",
                        labelled:true,
                        labelFormat:"dd",
                        lineSymbolizer:{strokeColor:"#4169E1", strokeOpacity:"0.7", strokeWidth:"1"},
                        labelSymbolizer:{fontColor:"#4169E1", fontOpacity:"0.7"}
                    }),
                    new OpenLayers.Control.Graticule({
                        visible:false,
                        numPoints:2,
                        layerName:"DMS Graticule",
                        labelled:true,
                        labelFormat:"dms",
                        lineSymbolizer:{strokeColor:"#4169E1", strokeOpacity:"0.7", strokeWidth:"1"},
                        labelSymbolizer:{fontColor:"#4169E1", fontOpacity:"0.7"}
                    })
                ],
                center: [0, 0],
                zoom: 3
            });

            this.map.addControl(new OpenLayers.Control.LayerSwitcher());
            
            this.map.events.register("moveend", this, this.setCenter);
            this.map.events.register("moveend", this, this.setExtent);
            this.map.events.register("mousemove", this, this.setMouse);
            
            this.map.zoomToMaxExtent();

            this.setupAoiLayer();
            this.setupToolbar();

        }
    },

    setupAoiLayer:function()
    {
        aoiLayer = new OpenLayers.Layer.Vector( "Bound Box" );
        aoiLayer.events.register( "featureadded", aoiLayer, this.setAoiLayer );

        var boundBox = new OpenLayers.Control.DrawFeature( aoiLayer, OpenLayers.Handler.RegularPolygon,
                {handlerOptions:{sides:4, irregular:true}} );

        this.map.addLayer( aoiLayer );
        this.map.addControl( boundBox );
    },
    setAoiLayer:function ( e )
    {
        var geom = e.feature.geometry;
        bounds = geom.getBounds();
        var feature = new OpenLayers.Feature.Vector( geom );

       
            alert(bounds.bottom + ", " + bounds.left);
            alert(bounds.top + ", " + bounds.right);
     
        

        aoiLayer.destroyFeatures();
        aoiLayer.addFeatures( feature, {silent:true} );
    },
   
    setupToolbar:function()
    {
        var panButton = new OpenLayers.Control.MouseDefaults(
            {
                title: 'Click button to activate. Once activated, drag the mouse to pan.'
            }
        );
        var zoomBoxButton =  new OpenLayers.Control.ZoomBox(
            {
                alwaysZoom: true
            }
        );
        zoomInButton = new OpenLayers.Control.ZoomIn(
            {
                title: 'Click button to zoom in.',
                trigger: this.zoomIn
            }
        );
        var zoomOutButton = new OpenLayers.Control.ZoomOut(
            {
                title: 'Click button to zoom out.',
                trigger: this.zoomOut
            }
        );
        var zoomInFullResButton = new OpenLayers.Control.Button(
            {
                title: 'Click button to zoom to full resolution.',
                displayClass: 'olControlZoomToLayer',
                trigger: this.zoomInFullRes
            }
        );
        var zoomMaxExtentButton = new OpenLayers.Control.ZoomToMaxExtent(
            {
                title: 'Click button to zoom to the max extent of the map.',
                trigger: this.zoomMaxExtent
            }
        );
        var boundBoxButton = new OpenLayers.Control.DrawFeature(aoiLayer, OpenLayers.Handler.RegularPolygon,
            {
                handlerOptions: {sides: 4, irregular: true},
                title: 'Click button to activate. Once activated, drag the mouse to define a bound box.'
            }
        );
        var clearAoiButton = new OpenLayers.Control.Button(
            {
                title: 'Click button to clear the bound box.',
                displayClass: 'olControlClearAreaOfInterest',
                trigger: this.clearBoundBox
            }
        );

        var pathMeasurementButton = new OpenLayers.Control.Measure(OpenLayers.Handler.Path, 
            {
                title: 'Click button to activate. Once acitivated, click points on the map to create a path that you wish to measure. When you are done creating your path, double click to end.',
                displayClass: 'olControlMeasureDistance',
                displaySystem: 'metric',
                geodesic: true,
                persist: true,
                eventListeners:{
                    measure:function (evt)
                    {
                        alert(evt.measure);
                    }
            }} );

        var polygonMeasurementButton = new OpenLayers.Control.Measure(OpenLayers.Handler.Polygon, 
            {
                title: 'Click button to activate. Once acitivated, click points on the map to create a polygon that you wish to measure. When you are done creating your polygon, double click to end.',
                displayClass: 'olControlMeasureArea',
                displaySystem: 'metric',
                geodesic: true,
                persist: true,
                eventListeners:{
                    measure:function (evt)
                    {
                        alert(evt.measure);
                    }
            }} );
        
        var panel = new OpenLayers.Control.Panel(
            {
                div: this.toolBar,
                defaultControl: panButton,
                displayClass: 'olControlPanel'
            }
        );

        panel.addControls(
            [
                panButton,
                zoomBoxButton,
                zoomInButton,
                zoomOutButton,
                zoomInFullResButton,
                zoomMaxExtentButton,
                boundBoxButton,
                clearAoiButton,
                pathMeasurementButton,
                polygonMeasurementButton
            ]
        );

        this.map.addControl(panel);
    },

    zoomIn:function()
    {
        this.map.zoomIn();
    },
    zoomOut:function()
    {
        this.map.zoomOut();
    },
    zoomInFullRes:function()
    {
        var fullRes = this.map.getZoomForResolution(zoomFullResScale, true);

        this.map.zoomTo(fullRes);
    },
    zoomMaxExtent:function()
    {
        this.map.zoomToMaxExtent();
    },
    clearBoundBox:function()
    {
        alert("clear the model here.");
    },

    setCenter:function()
    {
        if(this.pointModel)
        {
            this.pointModel.off("change", this.pointModelChanged, this);
            var center = this.map.getCenter();
            this.pointModel.set({"lat":center.lat, "lon":center.lon});
            this.pointModel.on("change", this.pointModelChanged, this);
        }
    },
    setExtent:function()
    {
        if(this.bboxModel)
        {
            this.bboxModel.off("change", this.bboxMapChanged, this);
            var extent = this.map.getExtent();
            this.bboxModel.set({"minx":extent.left, "miny":extent.bottom,
                                "maxx":extent.right, "maxy":extent.top});
            this.bboxModel.on("change", this.bboxMapChanged, this);
        }
    },
    setMouse:function(evt)
    {
        var mouse = this.map.getLonLatFromViewPortPx(new OpenLayers.Pixel(evt.xy.x, evt.xy.y));

        var ddMouse = document.getElementById("ddMouse");
        var dmsMouse = document.getElementById("dmsMouse");
        var mgrsMouse = document.getElementById("mgrsMouse");
        
        if (mouse.lat < "90" && mouse.lat > "-90" && mouse.lon < "180" && mouse.lon > "-180")
        {
            ddMouse.innerHTML = "<b>DD:</b> " + mouse.lat + ", " + mouse.lon;
            dmsMouse.innerHTML = "<b>DMS:</b> " + convert.ddToDms(mouse.lat, mouse.lon);
            mgrsMouse.innerHTML = "<b>MGRS:</b> " + convert.ddToMgrs(mouse.lat, mouse.lon);
          
        }
        else
        {
            ddMouse.innerHTML = "<b>DD:</b> Outside of geographic extent.";
            dmsMouse.innerHTML = "<b>DMS:</b> Outside of geographic extent.";
            mgrsMouse.innerHTML = "<b>MGRS:</b> Outside of geographic extent.";
        }
    },
    setBboxModel:function(bboxModel)
    {
        if(this.bboxModel)
        {
            // may want to unregister change listener of old model
            this.bboxModel.off("change", this.bboxMapChanged,this);
        }
        this.bboxModel = bboxModel;
        if(this.bboxModel)
        {
            this.bboxModel.on("change", this.bboxMapChanged, this);
        }
    },
    
    bboxMapChanged:function()
    {
        alert(this.bboxModel.get("minx"));




    },
    render:function()
    {
       this.reset();
    }
});

