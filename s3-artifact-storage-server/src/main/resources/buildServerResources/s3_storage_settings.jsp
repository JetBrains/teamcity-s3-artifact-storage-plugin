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
                    <props:option value="">-- Select bucket --</props:option>
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
    <tr id="${params.numberOfRetriesOnError}_row">
      <th><label for="${params.numberOfRetriesOnError}">Number of retries on error: </label></th>
      <td>
        <props:textProperty name="${params.numberOfRetriesOnError}" className="longField" maxlength="3"/>
      </td>
    </tr>
  <tr id="${params.retryDelayOnErrorMs}_row">
    <th><label for="${params.retryDelayOnErrorMs}">Delay in milliseconds before the next retry: </label></th>
    <td>
      <props:textProperty name="${params.retryDelayOnErrorMs}" className="longField" maxlength="6"/>
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
    var keyId = BS.Util.escapeId('aws.access.key.id');
    var keySecret = BS.Util.escapeId('secure:aws.secret.access.key');
    var useDefaultCredentialProviderChain = BS.Util.escapeId('aws.use.default.credential.provider.chain');
    var $bucketRegion = $j(BS.Util.escapeId('aws.region.name'));
    var $bucketSelector = $j(BS.Util.escapeId('${params.bucketName}'));

    function getErrors($response) {
        var $errors = $response.find("errors:eq(0) error");
        if ($errors.length) {
            return $j.map($errors, function (error) {
                return $j(error).text();
            }).join(", ");
        }

        return "";
    }

    function loadBuckets() {
        if (!$j(useDefaultCredentialProviderChain).is(':checked') && (!$j(keyId).val() || !$j(keySecret).val())) {
            return
        }

        var parameters = BS.EditStorageForm.serializeParameters() + '&resource=buckets';
        var $refreshButton = $j('#buckets-refresh').addClass('icon-spin');
        $j.post(window['base_uri'] + '${params.containersPath}', parameters)
                .then(function (response) {
                    var $response = $j(response);
                    var errors = getErrors($response);
                    $j(BS.Util.escapeId('error_${params.bucketName}')).text(errors);
                    if (errors) {
                      return
                    }

                    // Save selected option
                    var value = $bucketSelector.val();
                    if (value && !bucketLocations[value]) {
                        bucketLocations[value] = $bucketRegion.val();
                    }

                    // Redraw selector
                    $bucketSelector.empty();
                    $bucketSelector.append($j("<option></option>").attr("value", "").text("-- Select bucket --"));
                    $response.find("buckets:eq(0) bucket").each(function () {
                      var $this = $j(this);
                      var name = $this.text();
                      $bucketSelector.append($j("<option></option>").attr("value", name).text(name));
                    });

                    if (value) {
                        $bucketSelector.val(value);
                    }

                    $bucketSelector.change();
                })
                .always(function () {
                    $refreshButton.removeClass('icon-spin');
                });
    }

    $j(document).on('change', keyId + ', ' + keySecret, function () {
        loadBuckets();
    });
    $j(document).on('ready', function () {
        loadBuckets();
    });
    $j(document).on('click', '#buckets-refresh', function () {
        loadBuckets();
    });
    $j(document).on('change', useDefaultCredentialProviderChain, function () {
        loadBuckets();
    });

    $bucketSelector.change(function () {
        var bucketName = $j(this).val();
        if (!bucketName) {
          return
        }

        var location = bucketLocations[bucketName];
        if (location) {
          $bucketRegion.val(location);
          return
        }

        var parameters = BS.EditStorageForm.serializeParameters() + '&resource=bucketLocation';

        var $refreshButton = $j('#buckets-refresh').addClass('icon-spin');
        $j.post(window['base_uri'] + '${params.containersPath}', parameters)
            .then(function (response) {
              var $response = $j(response);
              var errors = getErrors($response);
              $j(BS.Util.escapeId('error_${params.bucketName}')).text(errors);
              if (errors) {
                return
              }

              // Save selected option
              var value = $bucketSelector.val();
              if (value && !bucketLocations[value]) {
                bucketLocations[value] = $bucketRegion.val();
              }

              var $bucket = $response.find("bucket");
              var name = $bucket.attr("name");

              var location = $bucket.attr("location");
              bucketLocations[name] = location;
              $bucketRegion.val(location);
            })
            .always(function () {
              $refreshButton.removeClass('icon-spin');
            });
    });
</script>
