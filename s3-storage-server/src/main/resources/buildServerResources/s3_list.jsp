<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>

<h3>List of artifacts Stored in S3 bucket</h3>
<ul>
<c:forEach var="entry" items="${pathsWithUrls}">
    <li><a href="<c:out value="${entry.value}"/>"><c:out value="${entry.key}"/></a></li>
</c:forEach>
</ul>