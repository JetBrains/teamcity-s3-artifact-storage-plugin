<%@ taglib prefix="props" tagdir="/WEB-INF/tags/props" %>
<%@ taglib prefix="intprop" uri="/WEB-INF/functions/intprop"%>
<%@ taglib prefix="l" tagdir="/WEB-INF/tags/layout" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="bs" tagdir="/WEB-INF/tags" %>
<%@ taglib prefix="admin" tagdir="/WEB-INF/tags/admin" %>

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

<c:set var="cloudfrontFeatureOn" value="${intprop:getBoolean('teamcity.s3.use.cloudfront.enabled')}"/>
<c:set var="cloudfrontDistributionSelect" value="cloudfrontDistributionSelect"/>
<c:set var="cloudFrontDistributionAutoOption" value="-- Create Automatically --"/>
<c:set var="cloudfrontKeyPairSelect" value="cloudfrontKeyPairSelect"/>
<c:set var="cloudFrontKeyPairEmptyOption" value="-- Select key pair --"/>

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
        <th class="noBorder"><label for="${bucketNameStringInput}">S3 bucket name: <l:star/></label></th>
        <td class="noBorder">
          <props:textProperty name="${bucketNameStringInput}" id="${bucketNameStringInput}" className="longField" value="${propertiesBean.properties[params.bucketName]}"/>
          <span class="smallNote">Specify the bucket name</span>
        </td>
      </tr>
    </props:selectSectionPropertyContent>
  </props:selectSectionProperty>
  <c:if test="${pathPrefixesFeatureOn or propertiesBean.properties[params.pathPrefix]}">
    <tr>
      <th><label for="${params.pathPrefix}">S3 path prefix: </label></th>
      <td>
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
</l:settingsGroup>
<c:if test="${cloudfrontFeatureOn}">
  <l:settingsGroup title="CloudFront Settings">
    <tr class="noBorder">
      <th><label for="${params.cloudFrontEnabled}">Use CloudFront to download artifacts: </label></th>
      <td>
        <props:checkboxProperty name="${params.cloudFrontEnabled}" id="${params.cloudFrontEnabled}" value="${propertiesBean.properties[params.cloudFrontEnabled]}"/>
      </td>
    </tr>
    <tbody id="${params.cloudFrontSettingsGroup}">
    <c:set var="distribution" value="${propertiesBean.properties[params.cloudFrontDistribution]}"/>
    <tr>
      <th><label for="${cloudfrontDistributionSelect}">Select CloudFront distribution: </label></th>
      <td>
        <props:selectProperty name="${cloudfrontDistributionSelect}" id="${cloudfrontDistributionSelect}" className="longField">
          <props:option value="">${cloudFrontDistributionAutoOption}</props:option>
          <c:if test="${not empty distribution}">
            <props:option value="${distribution}"><c:out value="${distribution}"/></props:option>
          </c:if>
        </props:selectProperty>
        <i class="icon-refresh" title="Reload distributions" id="distributions-refresh"></i>
        <props:hiddenProperty name="${params.cloudFrontDistribution}" id="${params.cloudFrontDistribution}" value="${propertiesBean.properties[params.cloudFrontDistribution]}"/>
        <span class="smallNote">Specify CloudFront distribution to use. Select "Create Automatically" and new distribution will be created for this project</span>
        <span class="error" id="error_${params.cloudFrontDistribution}" style="margin-top: -1em; margin-bottom: 1em;"></span>
        <span class="error" id="error_distributions" style="margin-top: -1em; margin-bottom: 1em;"></span>
      </td>
    </tr>
    <c:set var="publicKeyId" value="${propertiesBean.properties[params.cloudFrontPublicKeyId]}"/>
    <tr>
      <th><label for="${cloudfrontKeyPairSelect}">Select CloudFront public key: <l:star/></label></th>
      <td>
        <props:selectProperty name="${cloudfrontKeyPairSelect}" id="${cloudfrontKeyPairSelect}" className="longField">
          <props:option value="">${cloudFrontKeyPairEmptyOption}</props:option>
        </props:selectProperty>
        <i class="icon-refresh" title="Reload public keys" id="publicKeys-refresh"></i>
        <props:hiddenProperty name="${params.cloudFrontPublicKeyId}" id="${params.cloudFrontPublicKeyId}" value="${propertiesBean.properties[params.cloudFrontPublicKeyId]}"/>
        <span class="smallNote">Specify CloudFront public key to use.</span>
        <span class="error" id="error_${params.cloudFrontPublicKeyId}" style=></span>
        <span class="error" id="error_publicKeys"></span>
      </td>
    </tr>
    <tr class="auth uploadedKey">
      <th>
        <label for="teamcitySshKey">Select Private SSH key: <l:star/></label>
      </th>
      <td>
        <admin:sshKeys projectId="${propertiesBean.properties['projectId']}"/>
        <span class="error" id="error_${params.cloudFrontPrivateSshKey}"></span>
      </td>
    </tr>
    </tbody>
  </l:settingsGroup>
</c:if>
<l:settingsGroup title="Connection Settings">
  <tr class="advancedSetting">
    <th>Options:</th>
    <td>
      <props:checkboxProperty name="${params.usePresignUrlsForUpload}"/><label for="${params.usePresignUrlsForUpload}">Use Pre-Signed URLs for upload</label><br/>
      <props:checkboxProperty name="${params.forceVirtualHostAddressing}"/><label for="${params.forceVirtualHostAddressing}">Force Virtual Host Addressing</label>
    </td>
  </tr>
  <c:if test="${intprop:getBooleanOrTrue('teamcity.internal.storage.s3.upload.presignedUrl.multipart.enabled')}">
    <tr class="advancedSetting noBorder">
      <th><label for="${params.multipartUploadThreshold}">Multipart upload threshold:</label></th>
      <td>
        <props:textProperty name="${params.multipartUploadThreshold}"/>
        <span class="error" id="error_${params.multipartUploadThreshold}"></span>
        <bs:smallNote>Initiates multipart upload for files larger than the specified value. Minimum value is 5MB. Allowed suffixes: <it>KB, MB, GB, TB</it>. Leave empty to use the default value. <bs:help file="Configuring+Artifacts+Storage#multipartUpload"/></bs:smallNote>
      </td>
    </tr>
    <tr class="advancedSetting noBorder">
      <th><label for="${params.multipartUploadPartSize}">Multipart upload part size:</label></th>
      <td>
        <props:textProperty name="${params.multipartUploadPartSize}"/>
        <span class="error" id="error_${params.multipartUploadPartSize}"></span>
        <bs:smallNote>Specify the maximum allowed part size. Minimum value is 5MB. Allowed suffixes: <it>KB, MB, GB, TB</it>. Leave empty to use the default value. <bs:help file="Configuring+Artifacts+Storage#multipartUpload"/></bs:smallNote>
      </td>
    </tr>
  </c:if>
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
    var $distributionSelect= $j(BS.Util.escapeId('${cloudfrontDistributionSelect}'));
    var $distributionInput= $j(BS.Util.escapeId('${params.cloudFrontDistribution}'));
    var $publicKeySelect= $j(BS.Util.escapeId('${cloudfrontKeyPairSelect}'));
    var $publicKeyInput= $j(BS.Util.escapeId('${params.cloudFrontPublicKeyId}'));
    var publicKeys = [];
    var distributions = [];

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

    function addOptionToSelector(selector, name, value) {
      selector.append($j("<option></option>").attr("value", value).text(name));
    }

    function parseResourceListFromResponse($response, selector) {
      var list = [];
      $response.find(selector).each(function () {
        list.push($j(this));
      });
      return list;
    }

    function redrawBucketSelector(bucketList, selectedBucket) {
      $bucketSelect.empty();
      addOptionToSelector($bucketSelect, "-- Select bucket --", "");
      var selectedValueExistsInList = false;
      $j.each(bucketList, function (i, bucket) {
        if (selectedBucket && bucket === selectedBucket) {
          selectedValueExistsInList = true;
        }
        addOptionToSelector($bucketSelect, bucket, bucket)
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

        var bucketList = parseResourceListFromResponse($response, "buckets:eq(0) bucket").map(b => b.text());
        var selectedBucket = saveSelectedBucket();
        redrawBucketSelector(bucketList, selectedBucket);
        $realBucketInput.change();
      }).always(function () {
        BS.ErrorsAwareListener.onCompleteSave(BS.EditStorageForm, "<errors/>", true);
        $refreshButton.removeClass('icon-spin');
      });
    }

    function getSelectedDistributionName() {
      return $distributionInput.val();
    }

    function updateSelectedDistributionName(value) {
      $distributionInput.val(value).change();
    }

    function getSelectedKeyGroup() {
      return $publicKeyInput.val();
    }

    function updateSelectedKeyGroup(value) {
      $publicKeyInput.val(value).change();
    }

    function redrawDistributionSelector(selectedDistribution) {
      $distributionSelect.empty();
      addOptionToSelector($distributionSelect, '${cloudFrontDistributionAutoOption}', "");
      var selectedValueExistsInList = false;
      $j.each(distributions, function (i, distribution) {
        if (selectedDistribution && distribution.id === selectedDistribution) {
          selectedValueExistsInList = true;
        }
        addOptionToSelector($distributionSelect, distribution.id, distribution.id)
      });
      if (selectedDistribution) {
        if (selectedValueExistsInList) {
          $distributionSelect.val(selectedDistribution);
        }
        $distributionInput.val(selectedDistribution)
      }
      BS.enableJQueryDropDownFilter('${cloudfrontDistributionSelect}', {});
    }

    function redrawKeyGroupSelector() {
      const selectedKeyGroup = getSelectedKeyGroup();
      const selectedDistributionName = getSelectedDistributionName();
      const selectedDistribution = distributions.find(d => d.id === selectedDistributionName);

      $publicKeySelect.empty();
      addOptionToSelector($publicKeySelect, '${cloudFrontKeyPairEmptyOption}', "");
      var keys = publicKeys;
      if(selectedDistribution != null){
        keys = keys.filter(k => selectedDistribution.publicKeys.includes(k.id))
      }
      var selectedValueExistsInList = false;
      keys.forEach(publicKey => {
        if (selectedKeyGroup && publicKey.id === selectedKeyGroup) {
          selectedValueExistsInList = true;
        }
        addOptionToSelector($publicKeySelect, publicKey.name, publicKey.id)
      });

      if (selectedKeyGroup) {
        if (selectedValueExistsInList) {
          $publicKeySelect.val(selectedKeyGroup);
        }
        $publicKeyInput.val(selectedKeyGroup)
      }
      BS.enableJQueryDropDownFilter('${cloudfrontKeyPairSelect}', {});
    }

    function loadDistributionList() {
      if (!$j(useDefaultCredentialProviderChain).is(':checked') && (!$j(keyId).val() || !$j(keySecret).val())) {
        return;
      }
      BS.ErrorsAwareListener.onBeginSave(BS.EditStorageForm);

      var parameters = BS.EditStorageForm.serializeParameters() + '&resource=distributions';
      var $refreshButton = $j('#distributions-refresh').addClass('icon-spin');
      $j.post(window['base_uri'] + '${params.containersPath}', parameters).then(function (response) {
        var $response = $j(response);
        if (displayErrorsFromResponseIfAny($response)) {
          distributions = [];
          redrawDistributionSelector("");
          return;
        }

        distributions = parseResourceListFromResponse($response, "distributions:eq(0) distribution").map(d => {
          const id = d.find("id").text();
          const publicKeys = d.find("publicKey").map((i, e) => $j(e).text()).get()
          return {id, publicKeys};
        });
        var selectedDistribution = getSelectedDistributionName();
        redrawDistributionSelector(selectedDistribution);
        $distributionInput.change();
      }).always(function () {
        BS.ErrorsAwareListener.onCompleteSave(BS.EditStorageForm, "<errors/>", true);
        $refreshButton.removeClass('icon-spin');
      });
    }

    function loadPublicKeyList() {
      if (!$j(useDefaultCredentialProviderChain).is(':checked') && (!$j(keyId).val() || !$j(keySecret).val())) {
        return;
      }
      BS.ErrorsAwareListener.onBeginSave(BS.EditStorageForm);

      var parameters = BS.EditStorageForm.serializeParameters() + '&resource=publicKeys';
      var $refreshButton = $j('#publicKeys-refresh').addClass('icon-spin');
      $j.post(window['base_uri'] + '${params.containersPath}', parameters).then(function (response) {
        var $response = $j(response);
        if (displayErrorsFromResponseIfAny($response)) {
          redrawKeyGroupSelector();
          return;
        }

        publicKeys = parseResourceListFromResponse($response, "publicKeys:eq(0) publicKey").map(g => {
          const id = g.find("id").text();
          const name = g.find("name").text();
          return {id, name};
        });

        redrawKeyGroupSelector()
      }).always(function () {
        BS.ErrorsAwareListener.onCompleteSave(BS.EditStorageForm, "<errors/>", true);
        $refreshButton.removeClass('icon-spin');
      });
    }

    function updateCloudFrontVisibility(){
      if ($j(BS.Util.escapeId('${params.cloudFrontEnabled}')).is(':checked')) {
        BS.Util.show('${params.cloudFrontSettingsGroup}');
      } else {
        BS.Util.hide('${params.cloudFrontSettingsGroup}');
      }
    }

    $j(document).on('change', keyId + ', ' + keySecret, function () {
      loadBucketList();
      loadDistributionList();
      loadPublicKeyList();
    });
    $j(document).on('ready', function () {
      redrawBucketSelector([], "");
      loadBucketList();
      loadPublicKeyList();
      loadDistributionList();
      updateCloudFrontVisibility();
    });
    $j(document).on('click', '#buckets-refresh', function () {
      loadBucketList();
    });
    $j(document).on('click', '#distributions-refresh', function () {
      loadDistributionList();
    });
    $j(document).on('click', '#publicKeys-refresh', function () {
      loadPublicKeyList();
    });
    $j(document).on('change', useDefaultCredentialProviderChain, function () {
      loadBucketList();
      loadPublicKeyList();
      loadDistributionList();
    });
    $j(document).on('change', '#${bucketNameSelect}, #${bucketNameStringInput}', function () {
      var bucketName = $j(this).val();
      if (bucketName) {
        updateSelectedBucket(bucketName);
      }
    });
    $j(document).on('change', '#${cloudfrontDistributionSelect}', function () {
      var distributionName = $j(this).val();
      updateSelectedDistributionName(distributionName === ""? null : distributionName);
      redrawKeyGroupSelector()
    });

    $j(document).on('change', '#${cloudfrontKeyPairSelect}', function () {
      var publicKeyId = $j(this).val();
      updateSelectedKeyGroup(publicKeyId === ""? null : publicKeyId);
    });
    $j(BS.Util.escapeId('${params.cloudFrontEnabled}')).change(function () {
      updateCloudFrontVisibility()
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
