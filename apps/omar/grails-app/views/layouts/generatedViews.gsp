<html>
<head>
  <title><g:layoutTitle default="Grails"/></title>
  <style>
  body {
    margin: 0;
    padding: 0;
    visibility: hidden;
  }
  </style>

  <meta content="yes" name="apple-mobile-web-app-capable"/>
  <meta content="minimum-scale=1.0, width=device-width, user-scalable=no" name="viewport"/>

  <link rel="stylesheet" type="text/css" href="${omar.bundle(contentType: 'text/css', files: [
      resource(dir: 'css', file: 'main.css'),
      resource(dir: 'css', file: 'omar-2.0.css')
  ])}"/>

  <link rel="stylesheet" type="text/css" href="${resource(plugin: 'richui', dir: 'js/yui/reset-fonts-grids', file: 'reset-fonts-grids.css')}"/>
  <link rel="stylesheet" type="text/css" href="${resource(plugin: 'richui', dir: 'js/yui/assets/skins/sam', file: 'skin.css')}"/>

  <g:layoutHead/>
</head>

<body class="yui-skin-sam">

<div id="top">
  <omar:securityClassificationBanner/>
</div>

<div id="center">
  <g:pageProperty name="page.content"/>
</div>

<div id="bottom">
  <omar:securityClassificationBanner/>
</div>

<script type='text/javascript' src='${omar.bundle(contentType: "text/javascript", files: [
    resource(dir: "js", file: "application.js"),
    resource(plugin: "richui", dir: "js/yui/yahoo-dom-event", file: "yahoo-dom-event.js"),
    resource(plugin: "richui", dir: "js/yui/element", file: "element-min.js"),
    resource(plugin: "richui", dir: "js/yui/layout", file: "layout-min.js")
])}'></script>


<g:javascript>
  (function()
  {
    var Dom = YAHOO.util.Dom;
    var Event = YAHOO.util.Event;

    Event.onDOMReady( function()
    {
      var layout = new YAHOO.widget.Layout( {
        units: [
          { position: 'top', height: 25, body: 'top' },
          { position: 'center', body: 'center', scroll: true },
          { position: 'bottom', height: 25, body: 'bottom' }
        ]
      } );

      YAHOO.util.Dom.setStyle( document.body, 'visibility', 'visible' );

      layout.render();
    } );
  })();
</g:javascript>
</body>
</html>