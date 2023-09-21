import { Option } from '@jetbrains-internal/tcci-react-ui-components';

import { FormFields } from '../App/appConstants';
import { AwsConnection } from '../App/AwsConnection/AvailableAwsConnectionsConstants';

export type ConfigWrapper = {
  config: Config;
};

export type Config = {
  readOnly: boolean;
  storageTypes: string;
  storageNames: string;
  containersPath: string;
  distributionPath: string;
  publicKey: string;
  projectId: string;
  isNewStorage: boolean;
  cloudfrontFeatureOn: boolean;
  transferAccelerationOn: boolean;
  selectedStorageType: string;
  selectedStorageName: string;
  storageSettingsId: string;
  environmentNameValue: string;
  serviceEndpointValue: string;
  awsRegionName: string;
  showDefaultCredentialsChain: boolean;
  isDefaultCredentialsChain: boolean;
  credentialsTypeValue: string;
  accessKeyIdValue: string;
  secretAcessKeyValue: string;
  iamRoleArnValue: string;
  externalIdValue: string;
  bucketNameWasProvidedAsString: string;
  bucket: string;
  bucketPathPrefix: string;
  useCloudFront: boolean;
  cloudFrontUploadDistribution: string;
  cloudFrontDownloadDistribution: string;
  cloudFrontPublicKeyId: string;
  cloudFrontPrivateKey: string;
  usePresignUrlsForUpload: boolean;
  forceVirtualHostAddressing: boolean;
  verifyIntegrityAfterUpload: boolean;
  enableAccelerateMode: boolean;
  multipartUploadThreshold: string;
  multipartUploadPartSize: string;
  chosenAwsConnectionId: string;
  availableAwsConnectionsControllerUrl: string;
  availableAwsConnectionsControllerResource: string;
};

// Note: when changing types here fix related code in parametersUtils.tsx
export interface S3FormInput {
  [FormFields.STORAGE_TYPE]: Option;
  [FormFields.STORAGE_NAME]: string;
  [FormFields.STORAGE_ID]: string;
  [FormFields.AWS_CONNECTION_TYPE]: string;
  [FormFields.AWS_ENVIRONMENT_TYPE]: Option<number>;
  [FormFields.AWS_CONNECTION_ID]: Option<AwsConnection>;
  [FormFields.CUSTOM_AWS_ENDPOINT_URL]: string;
  [FormFields.CUSTOM_AWS_REGION]: string;
  [FormFields.CREDENTIALS_TYPE]: string;
  [FormFields.USE_DEFAULT_CREDENTIAL_PROVIDER_CHAIN]: boolean;
  [FormFields.ACCESS_KEY_ID]: string;
  [FormFields.SECRET_ACCESS_KEY]: string;
  [FormFields.IAM_ROLE_ARN]: string;
  [FormFields.EXTERNAL_ID]: string;
  [FormFields.S3_BUCKET_LIST_OR_NAME]: Option<number>;
  [FormFields.S3_BUCKET_NAME]: string | Option;
  [FormFields.S3_BUCHET_PATH_PREFIX]: string;
  [FormFields.CLOUD_FRONT_TOGGLE]: boolean;
  [FormFields.CLOUD_FRONT_UPLOAD_DISTRIBUTION]: DistributionItem;
  [FormFields.CLOUD_FRONT_DOWNLOAD_DISTRIBUTION]: DistributionItem;
  [FormFields.CLOUD_FRONT_PUBLIC_KEY_ID]: Option;
  [FormFields.CLOUD_FRONT_FILE_WITH_PRIVATE_KEY]: string;
  [FormFields.CLOUD_FRONT_PRIVATE_KEY]: string;
  [FormFields.CONNECTION_PRESIGNED_URL_TOGGLE]: boolean;
  [FormFields.CONNECTION_FORCE_VHA_TOGGLE]: boolean;
  [FormFields.CONNECTION_VERIFY_IAU_TOGGLE]: boolean;
  [FormFields.CONNECTION_TRANSFER_ACCELERATION_TOGGLE]: boolean;
  [FormFields.CONNECTION_MULTIPART_THRESHOLD]: string;
  [FormFields.CONNECTION_MULTIPART_CHUNKSIZE]: string;
  [FormFields.CONNECTION_MULTIPART_CUSTOMIZE_FLAG]: boolean;
  [FormFields.S3_TRANSFER_ACCELERATION_AVAILABLE]: boolean;
}

export type IFormInput = Partial<S3FormInput>;

export interface DistributionItem extends Option {
  publicKeys: string[] | null;
}

export interface CredentialType extends Option<number> {
  keyData: string;
  details: string;
}
