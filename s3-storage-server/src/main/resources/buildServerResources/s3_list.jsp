<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>

<%--@elvariable id="artifacts" type="java.util.Collection<jetbrains.buildServer.artifacts.ExternalArtifact>"--%>

<h3>Artifacts Stored in S3:</h3>
<ul>
<c:forEach var="a" items="${artifacts}">
    <li><a href="<c:out value="${a.url}"/>"><c:out value="${a.path}"/></a></li>
</c:forEach>
</ul>