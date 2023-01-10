import {useCallback, useEffect, useState} from 'react';

import {loadDistributionList} from '../Utilities/fetchDistributions';
import {loadPublicKeyList} from '../Utilities/fetchPublicKeys';
import {DistributionItem} from '../App/CloudFrontSettings';
import {FormFields} from '../App/appConstants';
import {AWS_ENV_TYPE_ARRAY} from '../App/AwsEnvironment';
import {AWS_CREDENTIALS_TYPE_ARRAY} from '../App/AwsSecurityCredentials';
import {Config, IFormInput} from '../types';
import {Option} from '../FormComponents/FormSelect';

function configAsFormInput(config: Config): IFormInput {
  return {
    [FormFields.STORAGE_TYPE]: null,
    [FormFields.STORAGE_NAME]: config.selectedStorageName || 'New S3 Storage',
    [FormFields.STORAGE_ID]: config.storageSettingsId || 'newS3Storage',
    [FormFields.AWS_ENVIRONMENT_TYPE]: config.environmentNameValue
      ? AWS_ENV_TYPE_ARRAY[1]
      : AWS_ENV_TYPE_ARRAY[0],
    [FormFields.CUSTOM_AWS_ENDPOINT_URL]: config.serviceEndpointValue,
    [FormFields.CUSTOM_AWS_REGION]: config.awsRegionName,
    [FormFields.USE_DEFAULT_CREDENTIAL_PROVIDER_CHAIN]: config.isDefaultCredentialsChain,
    [FormFields.CREDENTIALS_TYPE]: config.credentialsTypeValue || AWS_CREDENTIALS_TYPE_ARRAY[0].value,
    [FormFields.ACCESS_KEY_ID]: config.accessKeyIdValue,
    [FormFields.SECRET_ACCESS_KEY]: config.secretAcessKeyValue,
    [FormFields.IAM_ROLE_ARN]: config.iamRoleArnValue,
    [FormFields.EXTERNAL_ID]: config.externalIdValue,
    [FormFields.S3_BUCKET_LIST_OR_NAME]: null,
    [FormFields.S3_BUCKET_NAME]: config.bucket,
    [FormFields.S3_BUCHET_PATH_PREFIX]: config.bucketPathPrefix,
    [FormFields.CLOUD_FRONT_TOGGLE]: config.useCloudFront,
    [FormFields.CLOUD_FRONT_UPLOAD_DISTRIBUTION]: null,
    [FormFields.CLOUD_FRONT_DOWNLOAD_DISTRIBUTION]: null,
    [FormFields.CLOUD_FRONT_PUBLIC_KEY_ID]: null,
    [FormFields.CLOUD_FRONT_PRIVATE_KEY]: config.cloudFrontPrivateKey,
    [FormFields.CONNECTION_PRESIGNED_URL_TOGGLE]: config.usePresignUrlsForUpload,
    [FormFields.CONNECTION_FORCE_VHA_TOGGLE]: config.forceVirtualHostAddressing,
    [FormFields.CONNECTION_TRANSFER_ACCELERATION_TOGGLE]: config.enableAccelerateMode,
    [FormFields.CONNECTION_MULTIPART_THRESHOLD]: config.multipartUploadThreshold,
    [FormFields.CONNECTION_MULTIPART_CHUNKSIZE]: config.multipartUploadPartSize,
    [FormFields.CLOUD_FRONT_FILE_WITH_PRIVATE_KEY]: null
  };
}

export default function useDistributionInfo(config: Config) {
  const [initialUploadDistribution, setInitialUploadDistribution] = useState<DistributionItem | null>(null);
  const [initialDownloadDistribution, setInitialDownloadDistribution] = useState<DistributionItem | null>(null);
  const [publicKey, setPublicKey] = useState<Option | null>(null);
  const [loading, setLoading] = useState(true);

  const getDistributionsInfo = useCallback(async () => {
    const allValues = configAsFormInput(config);
    const [useDefaultCredentialProviderChain, keyId, keySecret] =
      [config.isDefaultCredentialsChain, config.accessKeyIdValue, config.secretAcessKeyValue];

    const {distributions} = await loadDistributionList(
      {
        appProps: config,
        allValues,
        useDefaultCredentialProviderChain,
        keyId,
        keySecret
      }
    );

    const {publicKeys} = await loadPublicKeyList(
      {
        appProps: config,
        allValues,
        useDefaultCredentialProviderChain,
        keyId,
        keySecret
      }
    );

    if (distributions) {
      const distributionsInfo = distributions.filter(d => d.enabled).reduce<DistributionItem[]>((acc, cur) => {
        acc.push({
          label: cur.description!,
          key: cur.id,
          publicKeys: cur.publicKeys
        });
        return acc;
      }, []);
      const allKeysFromDistributions = distributionsInfo.flatMap(dd => dd.publicKeys).filter(it => it);
      const pkInfo = publicKeys?.filter(it => allKeysFromDistributions.findIndex(k => k === it.id) > -1).map(it => ({
        key: it.id,
        label: it.name
      })) || [];

      return {distributionsInfo, pkInfo};
    } else {
      return {distributionsInfo: [], pkInfo: []};
    }
  }, [config]);

  useEffect(() => {
    setLoading(true);
    getDistributionsInfo().then(({distributionsInfo, pkInfo}) => {
      const uld = distributionsInfo.find(it => it.key === config.cloudFrontUploadDistribution) || null;
      const dld = distributionsInfo.find(it => it.key === config.cloudFrontDownloadDistribution) || null;
      const pk = pkInfo.find(it => it.key === config.cloudFrontPublicKeyId) || null;
      setInitialUploadDistribution(uld);
      setInitialDownloadDistribution(dld);
      setPublicKey(pk);
      setLoading(false);
    });
  },
  [config, getDistributionsInfo]);

  return {initialUploadDistribution, initialDownloadDistribution, publicKey, loading};
}
