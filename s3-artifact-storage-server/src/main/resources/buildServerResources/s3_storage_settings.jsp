<%@ taglib prefix="props" tagdir="/WEB-INF/tags/props" %>
<%@ taglib prefix="l" tagdir="/WEB-INF/tags/layout" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="bs" tagdir="/WEB-INF/tags" %>
<jsp:useBean id="propertiesBean" scope="request" type="jetbrains.buildServer.controllers.BasePropertiesBean"/>
<jsp:useBean id="params" class="jetbrains.buildServer.artifacts.s3.web.S3ParametersProvider"/>

<style type="text/css">
    .runnerFormTable {
        margin-top: 1em;
    }
</style>

<jsp:include page="editAWSCommonParams.jsp">
    <jsp:param name="requireRegion" value="${false}"/>
    <jsp:param name="requireEnvironment" value="${true}"/>
</jsp:include>

<l:settingsGroup title="S3 Parameters">
    <tr>
        <th><label for="${params.bucketName}">S3 bucket name: <l:star/></label></th>
        <td>
            <div class="posRel">
                <c:set var="bucket" value="${propertiesBean.properties[params.bucketName]}"/>
                <props:selectProperty name="${params.bucketName}" className="longField">
                    <c:if test="${not empty bucket}">
                        <props:option value="${bucket}"><c:out value="${bucket}"/></props:option>
                    </c:if>
                </props:selectProperty>
                <i class="icon-refresh" title="Reload buckets" id="buckets-refresh"></i>
            </div>
            <span class="smallNote">Existing S3 bucket to store artifacts</span>
            <span class="error" id="error_${params.bucketName}"></span>
        </td>
    </tr>
    <tr>
        <th>Options:</th>
        <td>
            <props:checkboxProperty name="${params.usePresignUrlsForUpload}"/>Use Pre-Signed URLs for upload<br/>
            <props:checkboxProperty name="${params.useSignatureVersion4}"/>Use Signature Version 4 in AWS KMS encryption
        </td>
    </tr>
</l:settingsGroup>

<script type="text/javascript">
    var bucketLocations = {};
    var $bucketRegion = $j(BS.Util.escapeId('aws.region.name'));
    var $bucketSelector = $j(BS.Util.escapeId('${params.bucketName}'));

    function getErrors($response) {
        var $errors = $response.find("errors:eq(0) error");
        if ($errors.length) {
            return $j.map($errors, function(error) {
                return $j(error).text();
            }).join(", ");
        }

        return "";
    }

    function loadBuckets() {
        var parameters = BS.EditStorageForm.serializeParameters();
        var $refreshButton = $j('#buckets-refresh').addClass('icon-spin');

        $j.post(window['base_uri'] + '${params.containersPath}', parameters)
                .then(function (response) {
                    var $response = $j(response);
                    var errors = getErrors($response);
                    $j(BS.Util.escapeId('error_${params.bucketName}')).text(errors);

                    // Get list of locations for buckets
                    bucketLocations = {};
                    $response.find("buckets:eq(0) bucket").each(function () {
                        var $this = $j(this);
                        var bucketName = $this.text();
                        bucketLocations[bucketName] = $this.attr('location');
                    });

                    // Save selected option
                    var value = $bucketSelector.val();
                    if (value && !bucketLocations[value]) {
                        bucketLocations[value] = $bucketRegion.val();
                    }

                    // Redraw selector
                    $bucketSelector.empty();
                    for (var name in bucketLocations) {
                        $bucketSelector.append($j("<option></option>").attr("value", name).text(name));
                    }

                    if (value) {
                        $bucketSelector.val(value);
                    }

                    $bucketSelector.change();
                })
                .always(function () {
                    $refreshButton.removeClass('icon-spin');
                });
    }

    var selectors = BS.Util.escapeId('aws.access.key.id') + ', ' +
            BS.Util.escapeId('secure:aws.secret.access.key');
    $j(document).on('change', selectors, function () {
        loadBuckets();
    });
    $j(document).on('ready', function () {
        loadBuckets();
    });
    $j(document).on('click', '#buckets-refresh', function () {
        loadBuckets();
    });
    
    $bucketSelector.change(function () {
        var bucketName = $j(this).val();
        var location = bucketLocations[bucketName];
        if (location) {
            $bucketRegion.val(location);
        }
    });
</script>
