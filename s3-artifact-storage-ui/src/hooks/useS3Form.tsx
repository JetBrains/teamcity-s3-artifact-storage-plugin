import {useForm} from 'react-hook-form';

import {useMemo} from 'react';

import {Option} from '@teamcity-cloud-integrations/react-ui-components';

import {FormFields} from '../App/appConstants';
import {AWS_ENV_TYPE_ARRAY} from '../App/AwsEnvironment';
import {AWS_CREDENTIALS_TYPE_ARRAY} from '../App/AwsSecurityCredentials';
import {S3_BUCKET_FROM_LIST_OR_BY_NAME_ARRAY} from '../App/S3Parameters';
import {Config, IFormInput} from '../types';

export default function useS3Form(
  config: Config,
  storageOptions: Option[]
) {
  const s3BucketListOrNameOption = useMemo(() => {
    const listOrName = S3_BUCKET_FROM_LIST_OR_BY_NAME_ARRAY.find(
      ({label}) => label === config.bucketNameWasProvidedAsString);

    if (config.bucketNameWasProvidedAsString && listOrName) {
      return listOrName;
    } else {
      return S3_BUCKET_FROM_LIST_OR_BY_NAME_ARRAY[0];
    }
  }, [config]);

  const defaultValues = useMemo<IFormInput>(() => (
    {
      [FormFields.STORAGE_TYPE]: storageOptions[0],
      [FormFields.STORAGE_NAME]: config.selectedStorageName || 'New S3 Storage',
      [FormFields.STORAGE_ID]: config.storageSettingsId || 'newS3Storage',
      [FormFields.AWS_ENVIRONMENT_TYPE]: config.environmentNameValue
        ? AWS_ENV_TYPE_ARRAY[1]
        : AWS_ENV_TYPE_ARRAY[0],
      [FormFields.CUSTOM_AWS_ENDPOINT_URL]: config.serviceEndpointValue,
      [FormFields.CUSTOM_AWS_REGION]: config.awsRegionName,
      [FormFields.USE_DEFAULT_CREDENTIAL_PROVIDER_CHAIN]: config.isDefaultCredentialsChain,
      [FormFields.CREDENTIALS_TYPE]: config.credentialsTypeValue || AWS_CREDENTIALS_TYPE_ARRAY[0].keyData,
      [FormFields.ACCESS_KEY_ID]: config.accessKeyIdValue,
      [FormFields.SECRET_ACCESS_KEY]: config.secretAcessKeyValue,
      [FormFields.IAM_ROLE_ARN]: config.iamRoleArnValue,
      [FormFields.EXTERNAL_ID]: config.externalIdValue,
      [FormFields.S3_BUCKET_LIST_OR_NAME]: s3BucketListOrNameOption,
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
    }),
  [config.accessKeyIdValue, config.awsRegionName, config.bucket,
    config.bucketPathPrefix, config.cloudFrontPrivateKey,
    config.credentialsTypeValue, config.enableAccelerateMode,
    config.environmentNameValue, config.externalIdValue,
    config.forceVirtualHostAddressing, config.iamRoleArnValue,
    config.isDefaultCredentialsChain, config.multipartUploadPartSize,
    config.multipartUploadThreshold, config.secretAcessKeyValue,
    config.selectedStorageName, config.serviceEndpointValue,
    config.storageSettingsId, config.useCloudFront,
    config.usePresignUrlsForUpload, s3BucketListOrNameOption,
    storageOptions]);

  return useForm<IFormInput>({defaultValues});
}
