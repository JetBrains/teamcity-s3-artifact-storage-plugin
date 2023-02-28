export enum FormFields {
  STORAGE_TYPE = 'storageType',
  STORAGE_ID = 'storageSettingsId',
  STORAGE_NAME = 'prop:storage_name',
  AWS_ENVIRONMENT_TYPE = 'prop:aws_environment',
  CUSTOM_AWS_ENDPOINT_URL = 'prop:aws_service_endpoint',
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
  CONNECTION_MULTIPART_CHUNKSIZE = 'prop:storage_s3_upload_multipart-chunksize'
}

function enumKeys<O extends object, K extends keyof O = keyof O>(obj: O): K[] {
  return Object.keys(obj) as K[];
}

export const keyToFormDataName = (key: string): string => key.replace(/_/g, '.').replace(/-/g, '_');

export const responseErrorIdToFormField = (errorId: string): FormFields | null => {
  const candidate = errorId.replace(/_/g, '-').replace(/\./g, '_');

  const r = enumKeys(FormFields).find(k => FormFields[k].endsWith(candidate));
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
  BUCKET_LOCATION = 'bucketLocation'
}

export const errorIdToFieldName = (id: string): FormFields | FormFields[] | null => {
  switch (id) {
    case FetchResourceIds.BUCKETS:
      return FormFields.S3_BUCKET_NAME;
    case FetchResourceIds.DISTRIBUTIONS:
      return [FormFields.CLOUD_FRONT_UPLOAD_DISTRIBUTION, FormFields.CLOUD_FRONT_DOWNLOAD_DISTRIBUTION];
    case FetchResourceIds.PUBLIC_KEYS:
      return FormFields.CLOUD_FRONT_PUBLIC_KEY_ID;
    default:
      return responseErrorIdToFormField(id);
  }
};
