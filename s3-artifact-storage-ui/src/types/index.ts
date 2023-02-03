import {Option} from '@teamcity-cloud-integrations/react-ui-components';

import {FormFields} from '../App/appConstants';
import {DistributionItem} from '../App/CloudFrontSettings';

export type ConfigWrapper = {
  config: Config
}

export type Config = {
  storageTypes: string,
  storageNames: string,
  containersPath: string,
  distributionPath: string,
  publicKey: string,
  projectId: string,
  isNewStorage: boolean,
  cloudfrontFeatureOn: boolean,
  transferAccelerationOn: boolean,
  selectedStorageName: string,
  storageSettingsId: string,
  environmentNameValue: string,
  serviceEndpointValue: string,
  awsRegionName: string,
  showDefaultCredentialsChain: boolean,
  isDefaultCredentialsChain: boolean,
  credentialsTypeValue: string,
  accessKeyIdValue: string,
  secretAcessKeyValue: string,
  iamRoleArnValue: string,
  externalIdValue: string,
  bucketNameWasProvidedAsString: string,
  bucket: string,
  bucketPathPrefix: string,
  useCloudFront: boolean,
  cloudFrontUploadDistribution: string,
  cloudFrontDownloadDistribution: string,
  cloudFrontPublicKeyId: string,
  cloudFrontPrivateKey: string,
  usePresignUrlsForUpload: boolean,
  forceVirtualHostAddressing: boolean,
  enableAccelerateMode: boolean,
  multipartUploadThreshold: string,
  multipartUploadPartSize: string,
}

// Note: when changing types here fix related code in parametersUtils.tsx
export interface IFormInput {
  [FormFields.STORAGE_TYPE]: Option | null;
  [FormFields.STORAGE_NAME]: string;
  [FormFields.STORAGE_ID]: string;
  [FormFields.AWS_ENVIRONMENT_TYPE]: Option<number>;
  [FormFields.CUSTOM_AWS_ENDPOINT_URL]: string | null | undefined;
  [FormFields.CUSTOM_AWS_REGION]: string | null | undefined;
  [FormFields.CREDENTIALS_TYPE]: string;
  [FormFields.USE_DEFAULT_CREDENTIAL_PROVIDER_CHAIN]: boolean | null | undefined;
  [FormFields.ACCESS_KEY_ID]: string | undefined;
  [FormFields.SECRET_ACCESS_KEY]: string | undefined;
  [FormFields.IAM_ROLE_ARN]: string | undefined;
  [FormFields.EXTERNAL_ID]: string | null | undefined;
  [FormFields.S3_BUCKET_LIST_OR_NAME]: Option<number> | null;
  [FormFields.S3_BUCKET_NAME]: string | Option<number> | null;
  [FormFields.S3_BUCHET_PATH_PREFIX]: string | null | undefined;
  [FormFields.CLOUD_FRONT_TOGGLE]: boolean | null | undefined;
  [FormFields.CLOUD_FRONT_UPLOAD_DISTRIBUTION]: DistributionItem | null | undefined;
  [FormFields.CLOUD_FRONT_DOWNLOAD_DISTRIBUTION]: DistributionItem | null | undefined;
  [FormFields.CLOUD_FRONT_PUBLIC_KEY_ID]: Option | null | undefined;
  [FormFields.CLOUD_FRONT_FILE_WITH_PRIVATE_KEY]: string | null | undefined;
  [FormFields.CLOUD_FRONT_PRIVATE_KEY]: string | null | undefined;
  [FormFields.CONNECTION_PRESIGNED_URL_TOGGLE]: boolean | null | undefined;
  [FormFields.CONNECTION_FORCE_VHA_TOGGLE]: boolean | null | undefined;
  [FormFields.CONNECTION_TRANSFER_ACCELERATION_TOGGLE]: boolean | null | undefined;
  [FormFields.CONNECTION_MULTIPART_THRESHOLD]: string | null | undefined;
  [FormFields.CONNECTION_MULTIPART_CHUNKSIZE]: string | null | undefined;
}
