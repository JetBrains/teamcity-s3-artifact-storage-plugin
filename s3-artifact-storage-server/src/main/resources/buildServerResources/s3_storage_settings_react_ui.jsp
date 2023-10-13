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
<%@ taglib prefix="util" uri="/WEB-INF/functions/util" %>
<%@ taglib prefix="bs" tagdir="/WEB-INF/tags" %>
<%@ taglib prefix="afn" uri="/WEB-INF/functions/authz" %>

<jsp:useBean id="propertiesBean" scope="request" type="jetbrains.buildServer.controllers.BasePropertiesBean"/>
<jsp:useBean id="params" class="jetbrains.buildServer.artifacts.s3.web.S3ParametersProvider"/>

<%@ page import="jetbrains.buildServer.clouds.amazon.connector.utils.parameters.AwsCloudConnectorConstants" %>
<%@ page import="jetbrains.buildServer.util.StringUtil" %>
<%@ page import="jetbrains.buildServer.util.amazon.AWSCommonParams" %>
<%@ page import="jetbrains.buildServer.util.amazon.AWSRegions" %>

<c:set var="region_name_param" value="<%=AWSCommonParams.REGION_NAME_PARAM%>"/>
<c:set var="region_name_default" value="<%=AWSRegions.DEFAULT_REGION%>"/>
<c:set var="default_cred_chain_disabled" value="<%= AWSCommonParams.DEFAULT_CREDENTIALS_PROVIDER_CHAIN_DISABLED_PARAM %>"/>
<c:set var="default_cred_chain_hidden" value="<%= AWSCommonParams.DEFAULT_CREDENTIALS_PROVIDER_CHAIN_HIDDEN_PARAM %>"/>
<c:set var="service_endpoint_param" value="<%=AWSCommonParams.SERVICE_ENDPOINT_PARAM%>"/>
<c:set var="environment_name_param" value="<%=AWSCommonParams.ENVIRONMENT_NAME_PARAM%>"/>
<c:set var="credentials_type_param" value="<%=AWSCommonParams.CREDENTIALS_TYPE_PARAM%>"/>
<c:set var="access_key_id_param" value="<%=AWSCommonParams.ACCESS_KEY_ID_PARAM%>"/>
<c:set var="secure_secret_access_key_param" value="<%=AWSCommonParams.SECURE_SECRET_ACCESS_KEY_PARAM%>"/>
<c:set var="iam_role_arn_param" value="<%=AWSCommonParams.IAM_ROLE_ARN_PARAM%>"/>
<c:set var="external_id_param" value="<%=AWSCommonParams.EXTERNAL_ID_PARAM%>"/>

<c:set var="regionName" value="${propertiesBean.properties[region_name_param]}"/>
<c:set var="cloudfrontFeatureOn" value="${intprop:getBooleanOrTrue('teamcity.s3.use.cloudfront.enabled')}"/>
<c:set var="transferAccelerationOn" value="${intprop:getBooleanOrTrue(params.transferAccelerationEnabled)}"/>
<c:set var="showDefaultCredentialsChain" value="${not intprop:getBoolean(default_cred_chain_disabled) and not intprop:getBoolean(default_cred_chain_hidden)}"/>
<c:set var="isDefaultCredentialsChain" value="${Boolean.parseBoolean(propertiesBean.properties[AWSCommonParams.USE_DEFAULT_CREDENTIAL_PROVIDER_CHAIN_PARAM])}"/>
<c:set var="service_endpoint_value" value="${propertiesBean.properties[service_endpoint_param]}"/>
<c:set var="environment_name_value" value="${propertiesBean.properties[environment_name_param]}"/>
<c:set var="credentials_type_value" value="${propertiesBean.properties[credentials_type_param]}"/>
<c:set var="access_key_id_value" value="${propertiesBean.properties[access_key_id_param]}"/>
<c:set var="secret_acess_key_value" value="${propertiesBean.properties[secure_secret_access_key_param]}"/>
<c:set var="iam_role_arn_value" value="${propertiesBean.properties[iam_role_arn_param]}"/>
<c:set var="external_id_value" value="${propertiesBean.properties[external_id_param]}"/>
<c:set var="bucket" value="${propertiesBean.properties[params.bucketName]}"/>

<c:set var="avail_connections_controller_url" value="<%=AwsCloudConnectorConstants.AVAIL_AWS_CONNECTIONS_CONTROLLER_URL%>"/>
<c:set var="avail_connections_rest_resource_name" value="<%=AwsCloudConnectorConstants.AVAIL_AWS_CONNECTIONS_REST_RESOURCE_NAME%>"/>

<%--@elvariable id="availableStorages" type="java.util.List<jetbrains.buildServer.serverSide.artifacts.ArtifactStorageType>"--%>
<%--@elvariable id="newStorage" type="String"--%>
<%--@elvariable id="selectedStorageType" type="jetbrains.buildServer.serverSide.artifacts.ArtifactStorageType"--%>
<%--@elvariable id="selectedStorageName" type="String"--%>
<%--@elvariable id="storageSettingsId" type="String"--%>
<%--@elvariable id="project" type="jetbrains.buildServer.serverSide.SProject"--%>
<%--@elvariable id="publicKey" type="java.lang.String"--%>

<c:set var="canEditProject" value="${afn:permissionGrantedForProject(project, 'EDIT_PROJECT')}"/>
<c:set var="projectIsReadOnly" value="${project.readOnly}"/>

<div id="edit-s3-storage-root"></div>
<c:set var="frontendDefaultUrl"><c:url value='${teamcityPluginResourcesPath}bundle.js'/></c:set>
<c:set var="overrideBundleUrl" value="${intprop:getProperty('teamcity.plugins.SakuraUI-Plugin.bundleUrl', '')}"/>

<c:set var="frontendCode">
  <c:choose>
    <c:when test="${!StringUtil.isEmpty(overrideBundleUrl)}">
      <c:out value="${overrideBundleUrl}/bundle.js"/>
    </c:when>
    <c:otherwise>
      <c:out value='${frontendDefaultUrl}'/>
    </c:otherwise>
  </c:choose>
</c:set>

<c:set var="storageTypes" value="${util:arrayToString(availableStorages.stream().map(it->it.getType()).toArray())}"/>
<c:set var="storageNames" value="${util:arrayToString(availableStorages.stream().map(it->it.getName()).toArray())}"/>
<c:set var="distributionPath" value="${params.pluginPath}cloudFront/createDistribution.html"/>
<c:set var="awsRegionName" value="${empty regionName ? region_name_default : regionName}"/>

<script type="text/javascript">
  const config = {
    readOnly: "<bs:forJs>${projectIsReadOnly || !canEditProject}</bs:forJs>" === "true",

    storageTypes: "<bs:forJs>${storageTypes}</bs:forJs>",
    storageNames: "<bs:forJs>${storageNames}</bs:forJs>",
    containersPath: "<bs:forJs>${params.containersPath}</bs:forJs>",
    distributionPath: "<bs:forJs>${distributionPath}</bs:forJs>",
    publicKey: "<bs:forJs>${publicKey}</bs:forJs>",
    projectId: "<bs:forJs>${project.externalId}</bs:forJs>",
    isNewStorage: "<bs:forJs>${Boolean.parseBoolean(newStorage)}</bs:forJs>" === "true",
    cloudfrontFeatureOn: "<bs:forJs>${cloudfrontFeatureOn}</bs:forJs>" === "true",

    selectedStorageType: "<bs:forJs>${selectedStorageType.type}</bs:forJs>",
    selectedStorageName: "<bs:forJs>${selectedStorageName}</bs:forJs>",
    storageSettingsId: "<bs:forJs>${storageSettingsId}</bs:forJs>",

    environmentNameValue: "<bs:forJs>${environment_name_value}</bs:forJs>",
    serviceEndpointValue: "<bs:forJs>${service_endpoint_value}</bs:forJs>",
    awsRegionName: "<bs:forJs>${awsRegionName}</bs:forJs>",

    showDefaultCredentialsChain: "<bs:forJs>${showDefaultCredentialsChain}</bs:forJs>" === "true",
    isDefaultCredentialsChain: "<bs:forJs>${isDefaultCredentialsChain}</bs:forJs>" === "true",

    credentialsTypeValue: "<bs:forJs>${credentials_type_value}</bs:forJs>",
    accessKeyIdValue: "<bs:forJs>${access_key_id_value}</bs:forJs>",
    secretAcessKeyValue: "<bs:forJs>${secret_acess_key_value}</bs:forJs>",
    iamRoleArnValue: "<bs:forJs>${iam_role_arn_value}</bs:forJs>",
    externalIdValue: "<bs:forJs>${external_id_value}</bs:forJs>",

    bucketNameWasProvidedAsString: "<bs:forJs>${propertiesBean.properties[params.bucketNameWasProvidedAsString]}</bs:forJs>",
    bucket: "<bs:forJs>${bucket}</bs:forJs>",
    bucketPathPrefix: "<bs:forJs>${propertiesBean.properties[params.pathPrefix]}</bs:forJs>",

    useCloudFront: "<bs:forJs>${propertiesBean.properties[params.cloudFrontEnabled]}</bs:forJs>" === "true",
    cloudFrontUploadDistribution: "<bs:forJs>${propertiesBean.properties[params.cloudFrontUploadDistribution]}</bs:forJs>",
    cloudFrontDownloadDistribution: "<bs:forJs>${propertiesBean.properties[params.cloudFrontDownloadDistribution]}</bs:forJs>",
    cloudFrontPublicKeyId: "<bs:forJs>${propertiesBean.properties[params.cloudFrontPublicKeyId]}</bs:forJs>",
    cloudFrontPrivateKey: "<bs:forJs>${propertiesBean.properties[params.cloudFrontPrivateKey]}</bs:forJs>",

    usePresignUrlsForUpload: "<bs:forJs>${propertiesBean.properties[params.usePresignUrlsForUpload]}</bs:forJs>" === "true",
    forceVirtualHostAddressing: "<bs:forJs>${propertiesBean.properties[params.forceVirtualHostAddressing]}</bs:forJs>" === "true",
    verifyIntegrityAfterUpload: "<bs:forJs>${propertiesBean.properties[params.verifyIntegrityAfterUpload]}</bs:forJs>" !== "false",
    transferAccelerationOn: "<bs:forJs>${Boolean.parseBoolean(transferAccelerationOn)}</bs:forJs>" === "true",
    enableAccelerateMode: "<bs:forJs>${propertiesBean.properties[params.enableAccelerateMode]}</bs:forJs>" === "true",
    multipartUploadThreshold: "<bs:forJs>${propertiesBean.properties[params.multipartUploadThreshold]}</bs:forJs>",
    multipartUploadPartSize: "<bs:forJs>${propertiesBean.properties[params.multipartUploadPartSize]}</bs:forJs>",

    availableAwsConnectionsControllerUrl: "<bs:forJs>${avail_connections_controller_url}</bs:forJs>",
    availableAwsConnectionsControllerResource: "<bs:forJs>${avail_connections_rest_resource_name}</bs:forJs>",
    chosenAwsConnectionId: "<bs:forJs>${propertiesBean.properties[params.chosenAwsConnectionId]}</bs:forJs>",
  };

  var loadJS = function (url, implementationCode, location) {
    var scriptTag = document.createElement('script');
    scriptTag.src = url;
    scriptTag.onload = implementationCode;
    location.appendChild(scriptTag);
  };

  var callback = function () {
    renderEditS3Storage(config);
  };

  loadJS("<bs:forJs>${frontendCode}</bs:forJs>", callback, document.body);

  // collapse old UI in advance
  function collapseOldUi(selector) {
    const allWithClass = Array.from(document.querySelectorAll(selector));

    allWithClass.forEach(element => {
      element.setAttribute('style', 'visibility: collapse; display: none;');
    });
  }

  collapseOldUi('#storageParamsInner table.runnerFormTable, #saveButtons');
</script>
