
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="intprop" uri="/WEB-INF/functions/intprop" %>

<jsp:useBean id="params" class="jetbrains.buildServer.artifacts.s3.web.S3ParametersProvider"/>

<c:set var="s3StorageReactUiEnabled" value="${intprop:getBooleanOrTrue(params.enabledReactUi)}"/>

<c:choose>
  <c:when test="${s3StorageReactUiEnabled}">
    <jsp:include page="s3_storage_settings_react_ui.jsp" />
  </c:when>
  <c:otherwise>
    <jsp:include page="s3_storage_settings_old_ui.jsp" />
  </c:otherwise>
</c:choose>
