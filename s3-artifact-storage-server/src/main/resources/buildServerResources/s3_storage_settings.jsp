<%@ taglib prefix="props" tagdir="/WEB-INF/tags/props" %>
<%@ taglib prefix="intprop" uri="/WEB-INF/functions/intprop"%>
<%@ taglib prefix="l" tagdir="/WEB-INF/tags/layout" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="bs" tagdir="/WEB-INF/tags" %>
<%--
  ~ Copyright 2000-2021 JetBrains s.r.o.
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

<jsp:useBean id="propertiesBean" scope="request" type="jetbrains.buildServer.controllers.BasePropertiesBean"/>
<jsp:useBean id="params" class="jetbrains.buildServer.artifacts.s3.web.S3ParametersProvider"/>
<c:set var="bucketNameSelect" value="bucketNameSelect"/>
<c:set var="bucketNameStringInput" value="bucketNameStringInput"/>
<c:set var="pathPrefixesFeatureOn" value="${intprop:getBooleanOrTrue('teamcity.internal.storage.s3.bucket.prefix.enable')}"/>

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

  <c:set var="bucket" value="${propertiesBean.properties[params.bucketName]}"/>
  <props:selectSectionProperty name="${params.bucketNameWasProvidedAsString}" title="Specify S3 bucket:">
    <props:selectSectionPropertyContent value="" caption="Choose from list">
      <tr class="non_serializable_form_elements_container noBorder">
        <th class="noBorder"><label for="${bucketNameSelect}">S3 bucket name: <l:star/></label></th>
        <td class="noBorder">
          <div class="posRel">
            <props:selectProperty name="${bucketNameSelect}" id="${bucketNameSelect}" className="longField">
              <props:option value="">-- Select bucket --</props:option>
              <c:if test="${not empty bucket}">
                <props:option value="${bucket}"><c:out value="${bucket}"/></props:option>
              </c:if>
            </props:selectProperty>
            <i class="icon-refresh" title="Reload buckets" id="buckets-refresh"></i>
          </div>
          <span class="smallNote">Existing S3 bucket to store artifacts</span>
        </td>
      </tr>
    </props:selectSectionPropertyContent>
    <props:selectSectionPropertyContent value="${true}" caption="Specify name">
      <tr class="non_serializable_form_elements_container noBorder">
        <th class="noBorder"><label for="bucketNameStringInput">S3 bucket name: <l:star/></label></th>
        <td class="noBorder">
          <props:textProperty name="${bucketNameStringInput}" id="${bucketNameStringInput}" className="longField" value="${propertiesBean.properties[params.bucketName]}"/>
          <span class="smallNote">Specify the bucket name</span>
        </td>
      </tr>
    </props:selectSectionPropertyContent>
  </props:selectSectionProperty>
  <c:if test="${pathPrefixesFeatureOn or propertiesBean.properties[params.pathPrefix]}">
    <tr class="noBorder">
      <th class="noBorder"><label for="${params.pathPrefix}">S3 path prefix: </label></th>
      <td class="noBorder">
        <props:textProperty name="${params.pathPrefix}" id="${params.pathPrefix}" className="longField" value="${propertiesBean.properties[params.pathPrefix]}"/>
        <span class="smallNote">Specify the path prefix</span>
        <span class="error" id="error_${params.pathPrefix}"></span>
      </td>
    </tr>
  </c:if>
  <tr class="noBorder">
    <th class="noBorder"></th>
    <td class="noBorder">
      <props:hiddenProperty name="${params.bucketName}" id="${params.bucketName}" value="${propertiesBean.properties[params.bucketName]}"/>
      <span class="error" id="error_${params.bucketName}" style="margin-top: -1em; margin-bottom: 1em;"></span>
      <span class="error" id="error_bucketLocation" style="margin-top: -1em; margin-bottom: 1em;"></span>
      <span class="error" id="error_buckets" style="margin-top: -1em; margin-bottom: 1em;"></span>
    </td>
  </tr>
  <tr>
    <th>Options:</th>
    <td>
      <props:checkboxProperty name="${params.usePresignUrlsForUpload}"/>Use Pre-Signed URLs for upload<br/>
      <props:checkboxProperty name="${params.useSignatureVersion4}"/>Use Signature Version 4 in AWS KMS encryption<br/>
      <props:checkboxProperty name="${params.forceVirtualHostAddressing}"/>Force Virtual Host Addressing
    </td>
  </tr>
</l:settingsGroup>

<script type="text/javascript">
  $j(document).ready(function () {
    var bucketLocations = {};
    var keyId = BS.Util.escapeId('aws.access.key.id');
    var keySecret = BS.Util.escapeId('secure:aws.secret.access.key');
    var useDefaultCredentialProviderChain = BS.Util.escapeId('aws.use.default.credential.provider.chain');
    var $bucketRegion = $j(BS.Util.escapeId('aws.region.name'));
    var $bucketSelect = $j(BS.Util.escapeId('${bucketNameSelect}'));
    var $bucketString = $j(BS.Util.escapeId('${bucketNameStringInput}'));
    var $realBucketInput = $j(BS.Util.escapeId('${params.bucketName}'));

    function parseErrors($response) {
      var $errors = $response.find("errors:eq(0) error");
      if (!$errors.length) {
        return null;
      } else {
        var result = {};
        $j.each($errors, function (i, error) {
          var $error = $j(error);
          result[$error.attr('id')] = $error.text();
        });
        return result;
      }
    }

    function displayErrorsFromResponseIfAny($response) {
      var errors = parseErrors($response);
      $j.each(errors, function (k, v) {
        BS.EditStorageForm.showError(k, v);
      });
      return errors;
    }

    function saveSelectedBucket() {
      var value = getSpecifiedBucketName();
      if (value && !bucketLocations[value]) {
        bucketLocations[value] = $bucketRegion.val();
      }
      return value;
    }

    function getSpecifiedBucketName() {
      return $realBucketInput.val();
    }

    function updateSelectedBucket(value) {
      $realBucketInput.val(value).change();
    }

    function addOptionToBucketSelector(name, value) {
      $bucketSelect.append($j("<option></option>").attr("value", value).text(name));
    }

    function redrawBucketSelector(bucketList, selectedBucket) {
      $bucketSelect.empty();
      addOptionToBucketSelector("-- Select bucket --", "");
      var selectedValueExistsInList = false;
      $j.each(bucketList, function (i, bucket) {
        if (selectedBucket && bucket === selectedBucket) {
          selectedValueExistsInList = true;
        }
        addOptionToBucketSelector(bucket, bucket)
      });
      if (selectedBucket) {
        if (selectedValueExistsInList) {
          $bucketSelect.val(selectedBucket);
        }
        $bucketString.val(selectedBucket);
        $realBucketInput.val(selectedBucket);
      }
      BS.enableJQueryDropDownFilter('${bucketNameSelect}', {});
    }

    function parseBucketListFromResponse($response) {
      var bucketList = [];
      $response.find("buckets:eq(0) bucket").each(function () {
        bucketList.push($j(this).text());
      });
      return bucketList;
    }

    function loadBucketList() {
      if (!$j(useDefaultCredentialProviderChain).is(':checked') && (!$j(keyId).val() || !$j(keySecret).val())) {
        return;
      }
      bucketLocations = {};
      BS.ErrorsAwareListener.onBeginSave(BS.EditStorageForm);

      var parameters = BS.EditStorageForm.serializeParameters() + '&resource=buckets';
      var $refreshButton = $j('#buckets-refresh').addClass('icon-spin');
      $j.post(window['base_uri'] + '${params.containersPath}', parameters).then(function (response) {
        var $response = $j(response);
        if (displayErrorsFromResponseIfAny($response)) {
          redrawBucketSelector([], "");
          return;
        }

        var bucketList = parseBucketListFromResponse($response);
        var selectedBucket = saveSelectedBucket();
        redrawBucketSelector(bucketList, selectedBucket);
        $realBucketInput.change();
      }).always(function () {
        BS.ErrorsAwareListener.onCompleteSave(BS.EditStorageForm, "<errors/>", true);
        $refreshButton.removeClass('icon-spin');
      });
    }

    $j(document).on('change', keyId + ', ' + keySecret, function () {
      loadBucketList();
    });
    $j(document).on('ready', function () {
      redrawBucketSelector([], "");
      loadBucketList();
    });
    $j(document).on('click', '#buckets-refresh', function () {
      loadBucketList();
    });
    $j(document).on('change', useDefaultCredentialProviderChain, function () {
      loadBucketList();
    });
    $j(document).on('change', '#${bucketNameSelect}, #${bucketNameStringInput}', function () {
      var bucketName = $j(this).val();
      if (bucketName) {
        updateSelectedBucket(bucketName);
      }
    });

    $realBucketInput.change(function () {
      var bucketName = getSpecifiedBucketName();
      if (!bucketName) {
        return;
      }

      var location = bucketLocations[bucketName];
      if (location) {
        BS.EditStorageForm.clearErrors();
        $bucketRegion.val(location);
        return;
      }

      var parameters = BS.EditStorageForm.serializeParameters() + '&resource=bucketLocation';

      var $refreshButton = $j('#buckets-refresh').addClass('icon-spin');
      $j.post(window['base_uri'] + '${params.containersPath}', parameters).then(function (response) {
        var $response = $j(response);
        if (displayErrorsFromResponseIfAny($response)) {
          return;
        }

        saveSelectedBucket();

        var $bucket = $response.find("bucket");
        var name = $bucket.attr("name");

        var location = $bucket.attr("location");
        bucketLocations[name] = location;
        $bucketRegion.val(location);
      }).always(function () {
        $refreshButton.removeClass('icon-spin');
      });
    });
  });
</script>
