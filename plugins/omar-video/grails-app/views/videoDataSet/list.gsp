<html>
<head>
  <meta http-equiv="Content-Type" content="text/html; charset=UTF-8"/>
  <meta name="layout" content="main8"/>
  <title>VideoDataSet List</title>
  <resource:tabView/>
</head>
<body>
<content tag="north">
  <div class="nav">
    <span class="menuButton"><g:link class="home" uri="/">Home</g:link></span>
    <g:ifAllGranted role="ROLE_ADMIN">
      <span class="menuButton"><g:link class="create" action="create">New VideoDataSet</g:link></span>
    </g:ifAllGranted>
    <span class="menuButton"><g:link action="search">Search</g:link></span></div>
</div>
</content>
<content tag="center">
  <h1>VideoDataSet List</h1>
  <g:if test="${flash.message}">
    <div class="message">${flash.message}</div>
  </g:if>
  <richui:tabView id="tabView">
    <richui:tabLabels>
      <richui:tabLabel title="Video" selected="true"/>
      <richui:tabLabel title="File"/>
    </richui:tabLabels>
    <richui:tabContents>
      <richui:tabContent>
        <div class="list">
          <table>
            <thead>
            <tr>

              <g:sortableColumn property="id" title="Id" params="${[repositoryId:params.repositoryId]}"/>

              <g:sortableColumn property="width" title="Width" params="${[repositoryId:params.repositoryId]}"/>

              <g:sortableColumn property="height" title="Height" params="${[repositoryId:params.repositoryId]}"/>

              <%--
              <g:sortableColumn property="startDate" title="Start Date"/>
              <g:sortableColumn property="endDate" title="End Date"/>
              --%>

              <th>Start Date</th>
              <th>End Date</th>

              <th>Min Lon</th>
              <th>Min Lat</th>
              <th>Max Lon</th>
              <th>Max Lat</th>
              <th>Thumbnail</th>

            </tr>
            </thead>
            <tbody>
            <g:each in="${videoDataSetList}" status="i" var="videoDataSet">
              <tr class="${(i % 2) == 0 ? 'odd' : 'even'}">

                <td><g:link action="show" id="${videoDataSet.id}">${fieldValue(bean: videoDataSet, field: 'id')}</g:link></td>

                <td>${fieldValue(bean: videoDataSet, field: 'width')}</td>

                <td>${fieldValue(bean: videoDataSet, field: 'height')}</td>

                <td>${fieldValue(bean: videoDataSet, field: 'startDate')}</td>

                <td>${fieldValue(bean: videoDataSet, field: 'endDate')}</td>


                <g:set var="bounds" value="${videoDataSet?.groundGeom?.bounds}"/>
                <td>${bounds?.minLon?.encodeAsHTML()}</td>
                <td>${bounds?.minLat?.encodeAsHTML()}</td>
                <td>${bounds?.maxLon?.encodeAsHTML()}</td>
                <td>${bounds?.maxLat?.encodeAsHTML()}</td>


                <td>
                  <a href="${createLink(controller: 'videoStreaming', action: 'show', id: videoDataSet.id)}">
                    <img src="${createLink(controller: 'thumbnail', action: 'frame', id: videoDataSet.id, params: [size: 128])}" alt="Show Frame"/>
                  </a>
                </td>

              </tr>
            </g:each>
            </tbody>
          </table>
        </div>
      </richui:tabContent>
      <richui:tabContent>
        <div class="list">
          <table>
            <thead>
            <tr>

              <g:sortableColumn property="id" title="Id" params="${[repositoryId:params.repositoryId]}"/>

              <th>Filename</th>
              <th>Thumbnail</th>

            </tr>
            </thead>
            <tbody>
            <g:each in="${videoDataSetList}" status="i" var="videoDataSet">
              <tr class="${(i % 2) == 0 ? 'odd' : 'even'}">

                <td><g:link action="show" id="${videoDataSet.id}">${fieldValue(bean: videoDataSet, field: 'id')}</g:link></td>

                <td>
                  <g:ifAllGranted role="ROLE_DOWNLOAD">
                    <a href=${grailsApplication.config.image.download.prefix}${videoDataSet.mainFile?.name?.encodeAsHTML()}>
                  </g:ifAllGranted>

                  ${videoDataSet.mainFile?.name?.encodeAsHTML()}

                  <g:ifAllGranted role="ROLE_DOWNLOAD">
                    </a>
                  </g:ifAllGranted>
                </td>

                <td>
                  <a href="${createLink(controller: 'videoStreaming', action: 'show', id: videoDataSet.id)}">
                    <img src="${createLink(controller: 'thumbnail', action: 'frame', id: videoDataSet.id, params: [size: 128])}" alt="Show Frame"/>
                  </a>
                </td>

              </tr>
            </g:each>
            </tbodycenter>
          </table>
        </div>

      </richui:tabContent>
    </richui:tabContents>
  </richui:tabView>
</content>
<content tag="south">
  <div class="paginateButtons">
    <g:paginate total="${videoDataSetList.totalCount}" params="${[repositoryId:params.repositoryId]}"/>
  </div>
</content>
</body>
</html>
