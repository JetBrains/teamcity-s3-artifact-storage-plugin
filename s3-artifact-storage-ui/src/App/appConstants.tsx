import {Option} from '@teamcity-cloud-integrations/react-ui-components';

import {CredentialType} from '../types';

export enum FormFields {
  STORAGE_TYPE = 'storageType',
  STORAGE_ID = 'storageSettingsId',
  STORAGE_NAME = 'prop:storage_name',
  AWS_ENVIRONMENT_TYPE = 'prop:aws_environment',
  AWS_CONNECTION_ID = 'prop:awsConnectionId',
  SESSION_DURATION = 'prop:awsSessionDuration',
  CUSTOM_AWS_ENDPOINT_URL = 'prop:aws_service_endpoint',
  // CUSTOM_AWS_REGION = 'prop:awsRegionName',
  CUSTOM_AWS_REGION = 'prop:aws_region_name',
  CREDENTIALS_TYPE = 'prop:aws_credentials_type',
  USE_DEFAULT_CREDENTIAL_PROVIDER_CHAIN = 'prop:aws_use_default_credential_provider_chain',
  ACCESS_KEY_ID = 'prop:aws_access_key_id',
  SECRET_ACCESS_KEY = 'prop:encrypted:secure:aws_secret_access_key',
  IAM_ROLE_ARN = 'prop:aws_iam_role_arn',
  EXTERNAL_ID = 'prop:aws_external_id',
  S3_BUCKET_LIST_OR_NAME = 'prop:storage_s3_bucket_name_wasProvidedAsString',
  S3_BUCKET_NAME = 'prop:storage_s3_bucket_name',
  S3_BUCHET_PATH_PREFIX = 'prop:storage_s3_bucket_prefix',
  CLOUD_FRONT_TOGGLE = 'prop:storage_s3_cloudfront_enabled',
  CLOUD_FRONT_UPLOAD_DISTRIBUTION = 'prop:storage_s3_cloudfront_upload_distribution',
  CLOUD_FRONT_DOWNLOAD_DISTRIBUTION = 'prop:storage_s3_cloudfront_download_distribution',
  CLOUD_FRONT_PUBLIC_KEY_ID = 'prop:storage_s3_cloudfront_publicKeyId',
  CLOUD_FRONT_FILE_WITH_PRIVATE_KEY = 'file:cloudFrontPrivateKeyUpload',
  CLOUD_FRONT_PRIVATE_KEY = 'prop:secure:storage_s3_cloudfront_privateKey',
  CONNECTION_PRESIGNED_URL_TOGGLE = 'prop:storage_s3_upload_presignedUrl_enabled',
  CONNECTION_FORCE_VHA_TOGGLE = 'prop:storage_s3_forceVirtualHostAddressing',
  CONNECTION_TRANSFER_ACCELERATION_TOGGLE = 'prop:storage_s3_accelerateModeEnabled',
  CONNECTION_MULTIPART_THRESHOLD = 'prop:storage_s3_upload_multipart-threshold',
  CONNECTION_MULTIPART_CHUNKSIZE = 'prop:storage_s3_upload_multipart-chunksize',
  CONNECTION_MULTIPART_CUSTOMIZE_FLAG = 'customizeMultipartUpload',
  S3_TRANSFER_ACCELERATION_AVAILABLE = 's3TransferAcceleration',
}

function enumKeys<O extends object, K extends keyof O = keyof O>(obj: O): K[] {
  return Object.keys(obj) as K[];
}

export const keyToFormDataName = (key: string): string =>
  key.replace(/_/g, '.').replace(/-/g, '_');

export const responseErrorIdToFormField = (
  errorId: string
): FormFields | null => {
  const candidate = errorId.replace(/_/g, '-').replace(/\./g, '_');

  if (FormFields.CLOUD_FRONT_PRIVATE_KEY.endsWith(candidate)) {
    // there is no field for private key
    return null;
  }

  const r = enumKeys(FormFields).find((k) => FormFields[k].endsWith(candidate));
  if (r) {
    return FormFields[r];
  } else {
    return null;
  }
};

export enum FetchResourceIds {
  BUCKETS = 'buckets',
  DISTRIBUTIONS = 'distributions',
  PUBLIC_KEYS = 'publicKeys',
  BUCKET_LOCATION = 'bucketLocation',
  S3_TRANSFER_ACCELERATION_AVAILABILITY = 's3TransferAccelerationAvailability',
  VALIDATE_CLOUD_FRONT_KEYS = 'validateCfKeys',
  // some specific error keys that we must match to fields
  S3_CLOUDFRONT_UPLOAD_DISTRIBUTION = 'storage.s3.cloudfront.upload.distribution',
  S3_CLOUDFRONT_DOWNLOAD_DISTRIBUTION = 'storage.s3.cloudfront.download.distribution',
  S3_CLOUDFRONT_PUBLIC_KEY_ID = 'storage.s3.cloudfront.publicKeyId',
  S3_CLOUDFRONT_PRIVATE_KEY = 'secure:storage.s3.cloudfront.privateKey',
  S3_CLOUDFRONT_CREATE_DISTRIBUTIONS = 'storage.s3.cloudfront.create.distributions',
}

export const errorIdToFieldName = (id: string): string | string[] | null => {
  switch (id) {
    case FetchResourceIds.BUCKETS:
      return FormFields.S3_BUCKET_NAME;
    case FetchResourceIds.S3_CLOUDFRONT_UPLOAD_DISTRIBUTION:
      return FormFields.CLOUD_FRONT_UPLOAD_DISTRIBUTION;
    case FetchResourceIds.S3_CLOUDFRONT_DOWNLOAD_DISTRIBUTION:
      return FormFields.CLOUD_FRONT_DOWNLOAD_DISTRIBUTION;
    case FetchResourceIds.DISTRIBUTIONS:
    case FetchResourceIds.S3_CLOUDFRONT_CREATE_DISTRIBUTIONS:
      return [
        FormFields.CLOUD_FRONT_UPLOAD_DISTRIBUTION,
        FormFields.CLOUD_FRONT_DOWNLOAD_DISTRIBUTION,
      ];
    case FetchResourceIds.PUBLIC_KEYS:
    case FetchResourceIds.S3_CLOUDFRONT_PRIVATE_KEY:
    case FetchResourceIds.S3_CLOUDFRONT_PUBLIC_KEY_ID:
    case FetchResourceIds.VALIDATE_CLOUD_FRONT_KEYS:
      return FormFields.CLOUD_FRONT_PUBLIC_KEY_ID;
    case FetchResourceIds.BUCKET_LOCATION:
      return [FormFields.AWS_CONNECTION_ID, FormFields.ACCESS_KEY_ID];
    default:
      return responseErrorIdToFormField(id);
  }
};

export const AWS_ENV_TYPE_ARRAY: Array<Option<number>> = [
  { label: '<Default>', key: 0 },
  { label: 'Custom', key: 1 },
];

const accessKeysType = 'aws.access.keys';
const tempCredsType = 'aws.temp.credentials';

export const AWS_CREDENTIALS_TYPE_ARRAY: CredentialType[] = [
  {
    label: 'Access keys',
    key: 0,
    keyData: accessKeysType,
    details: 'Use pre-configured AWS account access keys',
  },
  {
    label: 'Temporary credentials',
    key: 1,
    keyData: tempCredsType,
    details: 'Get temporary access keys via AWS STS',
  },
];

export const S3_BUCKET_FROM_LIST_OR_BY_NAME_ARRAY: Option<number>[] = [
  { label: 'Choose from list', key: 0 },
  { label: 'Specify name', key: 1 },
];
