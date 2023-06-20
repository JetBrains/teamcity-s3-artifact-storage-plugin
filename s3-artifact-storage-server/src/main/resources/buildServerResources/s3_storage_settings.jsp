<%--
  ~ Copyright 2000-2022 JetBrains s.r.o.
  ~
  ~ Licensed under the Apache License, Version 2.0 (the "License");
  ~ you may not use this file except in compliance with the License.
  ~ You may obtain a copy of the License at
  ~
  ~ http://www.apache.org/licenses/LICENSE-2.0
  ~
  ~ Unless required by applicable law or agreed to in writing, software
  ~ distributed under the License is distributed on an "AS IS" BASIS,
  ~ WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  ~ See the License for the specific language governing permissions and
  ~ limitations under the License.
  --%>
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
