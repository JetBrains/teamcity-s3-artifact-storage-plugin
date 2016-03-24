<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<%--@elvariable id="urlsToNames" type="java.util.Map<String, String>"--%>
<div style="margin-top: 10px; font-weight: bold;">Artifacts on S3:</div>
<c:forEach var="entry" items="${urlsToNames}">
    <div><a href="<c:out value="${entry.key}"/>"><c:out value="${entry.value}"/></a></div>
</c:forEach>