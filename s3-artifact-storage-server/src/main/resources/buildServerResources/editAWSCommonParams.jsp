

<%@ taglib prefix="props" tagdir="/WEB-INF/tags/props" %>
<%@ taglib prefix="l" tagdir="/WEB-INF/tags/layout" %>
<%@ taglib prefix="intprop" uri="/WEB-INF/functions/intprop"%>

<%@include file="constantsAWSCommonParams.jspf" %>

<c:set var="regionName" value="${propertiesBean.properties[region_name_param]}"/>
<c:set var="showDefaultCredentialsChain" value="${not intprop:getBooleanOrTrue(default_cred_chain_hidden) and intprop:getBoolean(default_cred_chain_enabled)}"/>
<c:choose>
    <c:when test="${empty param.requireEnvironment or true eq param.requireEnvironment}">
        <props:selectSectionProperty name="${environment_name_param}" title="${environment_name_label}:">
            <props:selectSectionPropertyContent value="" caption="<Default>"/>
            <props:selectSectionPropertyContent value="${environment_type_custom}" caption="Custom">
                <tr>
                    <th><label for="${service_endpoint_param}">${service_endpoint_label}: <l:star/></label></th>
                    <td>
                        <props:textProperty name="${service_endpoint_param}" className="longField"/>
                        <span class="smallNote">Specify the URL for AWS service</span>
                        <span class="error" id="error_${service_endpoint_param}"></span>
                    </td>
                </tr>
                <tr>
                    <th><label for="${region_name_param}">${region_name_label}: <l:star/></label></th>
                    <td>
                        <props:textProperty name="${region_name_param}" className="longField" maxlength="256"
                                            value="${empty regionName ? region_name_default : regionName}"/>
                        <span class="error" id="error_${region_name_param}"></span>
                    </td>
                </tr>
            </props:selectSectionPropertyContent>
        </props:selectSectionProperty>
    </c:when>
    <c:otherwise>
        <c:choose>
            <c:when test="${not empty param.requireRegion and false eq param.requireRegion}">
                <props:hiddenProperty name="${region_name_param}" value="${empty regionName ? region_name_default : regionName}"/>
            </c:when>
            <c:otherwise>
                <tr>
                    <th><label for="${region_name_param}">${region_name_label}: <l:star/></label></th>
                    <td>
                        <props:selectProperty name="${region_name_param}" className="longField" enableFilter="true">
                            <props:option value="${null}">-- Select region --</props:option>
                            <c:forEach var="region" items="${allRegions.keySet()}">
                                <props:option value="${region}"><c:out value="${allRegions[region]}"/></props:option>
                            </c:forEach>
                        </props:selectProperty>
                        <span class="smallNote">All resources must be located in this region</span><span class="error" id="error_${region_name_param}"></span>
                    </td>
                </tr>
            </c:otherwise>
        </c:choose>
    </c:otherwise>
</c:choose>

<l:settingsGroup title="AWS Security Credentials">
    <tr>
        <th>
            <label for="${credentials_type_param}">${credentials_type_label}: <l:star/></label>
            <br/>
            <div style="font-weight: normal">
                <a href="https://console.aws.amazon.com/iam" target="_blank" rel="noopener noreferrer">Open IAM Console</a>
            </div>
        </th>
        <td><props:radioButtonProperty name="${credentials_type_param}" value="${access_keys_option}" id="${access_keys_option}" onclick="awsCommonParamsUpdateVisibility()"/>
            <label for="${access_keys_option}">${access_keys_label}</label>
            <span class="smallNote">Use pre-configured AWS account access keys</span>
            <br/>
            <props:radioButtonProperty name="${credentials_type_param}" value="${temp_credentials_option}" id="${temp_credentials_option}" onclick="awsCommonParamsUpdateVisibility()"/>
            <label for="${temp_credentials_option}">${temp_credentials_label}</label>
            <span class="smallNote">Get temporary access keys via AWS STS</span>
            <span class="error" id="error_${credentials_type_param}"></span>
        </td>
    </tr>
    <tr id="${iam_role_arn_param}_row">
        <th><label for="${iam_role_arn_param}">${iam_role_arn_label}: <l:star/></label></th>
        <td><props:textProperty name="${iam_role_arn_param}" className="longField" maxlength="256"/>
            <span class="smallNote">Pre-configured IAM role with necessary permissions</span><span class="error" id="error_${iam_role_arn_param}"></span>
        </td>
    </tr>
    <tr id="${external_id_param}_row">
        <th><label for="${external_id_param}">${external_id_label}: </label></th>
        <td><props:textProperty name="${external_id_param}" className="longField" maxlength="256"/>
            <span class="smallNote">External ID is strongly recommended to be used in role trust relationship condition</span><span class="error" id="error_${external_id_param}"></span>
        </td>
    </tr>
    <c:if test="${showDefaultCredentialsChain or param.enableDefaultCredentialsChain}">
        <tr>
            <th><label for="${use_default_cred_chain_param}">${use_default_cred_chain_label}: </label></th>
            <td><props:checkboxProperty name="${use_default_cred_chain_param}" onclick="awsCommonParamsUpdateVisibility()"/></td>
        </tr>
    </c:if>
    <tr id="${access_key_id_param}_row">
        <th><label for="${access_key_id_param}">${access_key_id_label}: <l:star/></label></th>
        <td><props:textProperty name="${access_key_id_param}" className="longField" maxlength="256" noAutoComplete="true"/>
            <span class="smallNote">AWS account access key ID</span><span class="error" id="error_${access_key_id_param}"></span>
        </td>
    </tr>
    <tr id="${secret_access_key_param}_row">
        <th class="nowrap"><label for="${secure_secret_access_key_param}">${secret_access_key_label}: <l:star/></label></th>
        <td><props:passwordProperty name="${secure_secret_access_key_param}" className="longField" maxlength="256"/>
            <span class="smallNote">AWS account secret access key</span><span class="error" id="error_${secure_secret_access_key_param}"></span>
        </td>
    </tr>
</l:settingsGroup>

<script type="application/javascript">
    window.awsCommonParamsUpdateVisibility = function () {
        if ($j(BS.Util.escapeId('${access_keys_option}')).is(':checked')) {
            BS.Util.hide('${iam_role_arn_param}_row', '${external_id_param}_row');
        } else {
            BS.Util.show('${iam_role_arn_param}_row', '${external_id_param}_row');
        }

        if ($j(BS.Util.escapeId('${use_default_cred_chain_param}')).is(':checked')) {
            BS.Util.hide('${access_key_id_param}_row', '${secret_access_key_param}_row');
        } else {
            BS.Util.show('${access_key_id_param}_row', '${secret_access_key_param}_row');
        }
        BS.VisibilityHandlers.updateVisibility('runnerParams');
    };

    awsCommonParamsUpdateVisibility();
</script>