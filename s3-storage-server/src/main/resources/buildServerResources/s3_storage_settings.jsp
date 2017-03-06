<%@ page import="jetbrains.buildServer.artifacts.s3.S3Constants" %>
<%@ taglib prefix="props" tagdir="/WEB-INF/tags/props" %>
<%@ taglib prefix="l" tagdir="/WEB-INF/tags/layout" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="bs" tagdir="/WEB-INF/tags" %>

<style type="text/css">
    .runnerFormTable {
        margin-top: 1em;
    }
</style>

<table class="runnerFormTable">
    <jsp:include page="editAWSCommonParams.jsp"/>
    <l:settingsGroup title="S3 Parameters">
        <tr>
            <th><label for="storage.s3.bucket.name">S3 bucket name: <l:star/></label></th>
            <td><props:textProperty name="<%=S3Constants.S3_BUCKET_NAME%>" className="longField" maxlength="256"/>
                <span class="smallNote">S3 bucket name</span>
                <span class="error" id="error_storage.s3.bucket.name"></span>
            </td>
        </tr>
    </l:settingsGroup>
</table>