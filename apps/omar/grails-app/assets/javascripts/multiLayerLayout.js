/**
 * Created by IntelliJ IDEA.
 * User: sbortman
 * Date: 1/30/12
 * Time: 4:57 PM
 * To change this template use File | Settings | File Templates.
 */

//= require yui/yahoo-dom-event/yahoo-dom-event.js
//= require yui/element/element-min.js
//= require yui/layout/layout-min.js
//= require yui/container/container_core-min.js
//= require yui/menu/menu-min.js
//= require OpenLayers.js
//= require mapwidget.js
//= require coord.js
//= require omar-misc.js
//= require_self

(function ()
{
    var Dom = YAHOO.util.Dom,
    Event = YAHOO.util.Event;

    Event.addListener(window, 'resize', bodyOnResize);

    Event.onDOMReady( function ()
    {
        var outerLayout = new YAHOO.widget.Layout( {
            //minWidth:256,
            //minHeight:128,
            units:[
                { position:'top', height:50, body:'top1', scroll:null, zIndex:2 },
                /*{ position:'right', header:'', width:200, resize:true, gutter:'0px', collapse:true, scroll:true, body:'right1', animate:false },*/
                { position:'bottom', height:25, body:'bottom1' },
                /*{ position:'left', header:'', width:200, resize:true, body:'left1', gutter:'0px', collapse:true, scroll:true, animate:false },*/
                { position:'center'}//, minWidth:400, minHeight:200 }
            ]
        } );
        outerLayout.on('resize', function(evt) {
           // console.log("resize");
            bodyOnResize();

        });
        outerLayout.on( 'render', function ()
        {
            var el = outerLayout.getUnitByPosition( 'center' ).get( 'wrap' );
            var innerLayout = new YAHOO.widget.Layout( el, {
                parent:outerLayout,
                //minWidth:256,
                //minHeight:128,
                units:[
                    { position:'top', height:35, proxy:false, body:'top2' },
                    { position:'bottom', height:30, proxy:false, body:'bottom2' },
                    { position:'center', body:'center2', gutter:'0px' }
                ]
            } );
            innerLayout.render();
            Dom.setStyle( document.body, 'visibility', 'visible' );
            init();
        } );
        outerLayout.render();
        outerLayout.getUnitByPosition( "left" ).on( "collapse", function ()
        {
            bodyOnResize();
        } );

        outerLayout.getUnitByPosition( "left" ).on( "expand", function ()
        {
            bodyOnResize();
        } );

        outerLayout.getUnitByPosition( "right" ).on( "collapse", function ()
        {
            bodyOnResize();
        } );

        outerLayout.getUnitByPosition( "right" ).on( "expand", function ()
        {
            bodyOnResize();
        } );
    } );
})();

var bodyOnResize = function ()
{
    //alert(changeMapSize);
    var Dom = YAHOO.util.Dom;
    var region = Dom.getRegion( 'center2' );
    var width = region.width;
    var height = region.height;
    //alert(width+","+height);
    //changeMapSize( width + 'px', height + 'px' );
    //alert(omar);
    changeMapSize();
};
