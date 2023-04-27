import { useForm } from 'react-hook-form';
import { useMemo } from 'react';

import {
  AWS_CREDENTIALS_TYPE_ARRAY,
  AWS_ENV_TYPE_ARRAY,
  FormFields,
  S3_BUCKET_FROM_LIST_OR_BY_NAME_ARRAY,
} from '../App/appConstants';
import { IFormInput } from '../types';
import { useAppContext } from '../contexts/AppContext';
import { useAwsConnectionsContext } from '../contexts/AwsConnectionsContext';

import useStorageOptions from './useStorageOptions';

export default function useS3Form() {
  const config = useAppContext();
  const { connectionOptions, withFake } = useAwsConnectionsContext();
  const storageOptions = useStorageOptions();
  const s3BucketListOrNameOption = useMemo(() => {
    const listOrName = S3_BUCKET_FROM_LIST_OR_BY_NAME_ARRAY.find(
      ({ label }) => label === config.bucketNameWasProvidedAsString
    );

    if (config.bucketNameWasProvidedAsString && listOrName) {
      return listOrName;
    } else {
      return S3_BUCKET_FROM_LIST_OR_BY_NAME_ARRAY[0];
    }
  }, [config]);

  const storageTypeValue = storageOptions.find(
    (st) => st.key === config.selectedStorageType
  );

  const awsConnectionValue = useMemo(() => {
    if (connectionOptions) {
      if (config.isNewStorage || withFake) {
        return connectionOptions[0];
      } else {
        return connectionOptions.find(
          ({ key }) => key.id === config.chosenAwsConnectionId
        );
      }
    }

    return undefined;
  }, [config, connectionOptions]);
  const multipartCustomizeFlagValue = !!(
    config.multipartUploadThreshold || config.multipartUploadPartSize
  );

  const forceVirtualHostAddressingValue = config.isNewStorage
    ? true
    : config.forceVirtualHostAddressing;

  const defaultValues = useMemo<IFormInput>(
    () => ({
      [FormFields.STORAGE_TYPE]: storageTypeValue,
      [FormFields.STORAGE_NAME]: config.selectedStorageName,
      [FormFields.STORAGE_ID]: config.storageSettingsId || 'newS3Storage',
      [FormFields.AWS_CONNECTION_ID]: awsConnectionValue,
      [FormFields.SESSION_DURATION]: config.sessionDuration,
      [FormFields.AWS_ENVIRONMENT_TYPE]: config.environmentNameValue
        ? AWS_ENV_TYPE_ARRAY[1]
        : AWS_ENV_TYPE_ARRAY[0],
      [FormFields.CUSTOM_AWS_ENDPOINT_URL]: config.serviceEndpointValue,
      [FormFields.CUSTOM_AWS_REGION]: config.awsRegionName,
      [FormFields.USE_DEFAULT_CREDENTIAL_PROVIDER_CHAIN]:
        config.isDefaultCredentialsChain,
      [FormFields.CREDENTIALS_TYPE]:
        config.credentialsTypeValue || AWS_CREDENTIALS_TYPE_ARRAY[0].keyData,
      [FormFields.ACCESS_KEY_ID]: config.accessKeyIdValue,
      [FormFields.SECRET_ACCESS_KEY]: config.secretAcessKeyValue,
      [FormFields.IAM_ROLE_ARN]: config.iamRoleArnValue,
      [FormFields.EXTERNAL_ID]: config.externalIdValue,
      [FormFields.S3_BUCKET_LIST_OR_NAME]: s3BucketListOrNameOption,
      [FormFields.S3_BUCKET_NAME]: config.bucket,
      [FormFields.S3_BUCHET_PATH_PREFIX]: config.bucketPathPrefix,
      [FormFields.CLOUD_FRONT_TOGGLE]: config.useCloudFront,
      [FormFields.CLOUD_FRONT_PRIVATE_KEY]: config.cloudFrontPrivateKey,
      [FormFields.CONNECTION_PRESIGNED_URL_TOGGLE]:
        config.usePresignUrlsForUpload,
      [FormFields.CONNECTION_FORCE_VHA_TOGGLE]: forceVirtualHostAddressingValue,
      [FormFields.CONNECTION_TRANSFER_ACCELERATION_TOGGLE]:
        config.enableAccelerateMode,
      [FormFields.CONNECTION_MULTIPART_THRESHOLD]:
        config.multipartUploadThreshold,
      [FormFields.CONNECTION_MULTIPART_CHUNKSIZE]:
        config.multipartUploadPartSize,
      [FormFields.CONNECTION_MULTIPART_CUSTOMIZE_FLAG]:
        multipartCustomizeFlagValue,
    }),
    [
      storageTypeValue,
      config,
      s3BucketListOrNameOption,
      awsConnectionValue,
      multipartCustomizeFlagValue,
      forceVirtualHostAddressingValue,
    ]
  );

  return useForm<IFormInput>({ defaultValues });
}
