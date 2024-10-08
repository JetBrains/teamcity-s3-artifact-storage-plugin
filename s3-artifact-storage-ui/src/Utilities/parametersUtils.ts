import { Option } from '@jetbrains-internal/tcci-react-ui-components';

import { FormFields, keyToFormDataName } from '../App/appConstants';
import { Config, DistributionItem, IFormInput } from '../types';
import { AWS_S3, S3_COMPATIBLE } from '../App/Storage/components/StorageType';
import { PASSWORD_STUB } from '../hooks/useS3Form';

function valueOrDefault(
  condition: () => boolean,
  getter: () => string,
  defaultVal: any = ''
) {
  let r;
  if (condition()) {
    r = getter();
  } else {
    r = defaultVal;
  }

  return r;
}

function handleS3BucketListOrName(rkey: string, value: any) {
  const v = value as Option<number>;
  return {
    [rkey]: valueOrDefault(
      () => v.key !== 0,
      () => v.label
    ),
  };
}

function handleS3BucketName(rkey: string, value: any) {
  const v = value as Option;
  return { [rkey]: v.label };
}

function handleAwsEnvType(rkey: string, value: any) {
  const v = value as Option<number>;
  return {
    [rkey]: valueOrDefault(
      () => v.key !== 0,
      () => v.label
    ),
  };
}

function handleCfPublicKey(rkey: string, value: any) {
  const v = value as Option;
  return { [rkey]: v.key };
}

function handleCfDistribution(rkey: string, value: any) {
  const v = value as DistributionItem;
  return { [rkey]: v.key };
}
function handleChosenAwsConnection(rkey: string, value: any) {
  const v = value as Option;
  return { [rkey]: v.key };
}

export function serializeParameters(
  formData: IFormInput,
  appProps: Config
): { [k: string]: string } {
  const awsConnectionKey = formData[FormFields.AWS_CONNECTION_ID]?.key;

  if (!formData[FormFields.CONNECTION_MULTIPART_CUSTOMIZE_FLAG]) {
    formData[FormFields.CONNECTION_MULTIPART_THRESHOLD] = '';
    formData[FormFields.CONNECTION_MULTIPART_CHUNKSIZE] = '';
  }
  if (!formData[FormFields.CLOUD_FRONT_TOGGLE]) {
    formData[FormFields.CLOUD_FRONT_DOWNLOAD_DISTRIBUTION] = undefined;
    formData[FormFields.CLOUD_FRONT_UPLOAD_DISTRIBUTION] = undefined;
    formData[FormFields.CLOUD_FRONT_PUBLIC_KEY_ID] = undefined;
    formData[FormFields.CLOUD_FRONT_PRIVATE_KEY] = undefined;
  }
  if (formData[FormFields.STORAGE_TYPE]?.key === AWS_S3 && awsConnectionKey) {
    formData[FormFields.ACCESS_KEY_ID] = undefined;
    formData[FormFields.SECRET_ACCESS_KEY] = undefined;
    formData[FormFields.CREDENTIALS_TYPE] = undefined;
    formData[FormFields.CUSTOM_AWS_ENDPOINT_URL] = undefined;
  } else if (formData[FormFields.STORAGE_TYPE]?.key === S3_COMPATIBLE) {
    formData[FormFields.AWS_CONNECTION_ID] = undefined;
    formData[FormFields.CUSTOM_AWS_REGION] = undefined;
  } else if (
    !awsConnectionKey &&
    formData[FormFields.CUSTOM_AWS_ENDPOINT_URL]?.length === 0
  ) {
    formData[FormFields.CUSTOM_AWS_ENDPOINT_URL] = undefined;
  }

  return Object.keys(formData)
    .map((key) => {
      // @ts-ignore
      const value = formData[key];
      const rkey = keyToFormDataName(key);

      if (value || key === FormFields.CONNECTION_VERIFY_IAU_TOGGLE) {
        if (typeof value === 'object') {
          if (key === FormFields.S3_BUCKET_LIST_OR_NAME) {
            return handleS3BucketListOrName(rkey, value);
          }
          if (key === FormFields.S3_BUCKET_NAME) {
            return handleS3BucketName(rkey, value);
          }
          if (key === FormFields.STORAGE_TYPE) {
            const v = value as Option;
            return { [rkey]: v.key };
          }
          if (key === FormFields.AWS_ENVIRONMENT_TYPE) {
            return handleAwsEnvType(rkey, value);
          }
          if (key === FormFields.CLOUD_FRONT_PUBLIC_KEY_ID) {
            return handleCfPublicKey(rkey, value);
          }
          if (
            key === FormFields.CLOUD_FRONT_DOWNLOAD_DISTRIBUTION ||
            key === FormFields.CLOUD_FRONT_UPLOAD_DISTRIBUTION
          ) {
            return handleCfDistribution(rkey, value);
          }
          if (key === FormFields.AWS_CONNECTION_ID) {
            if (!value.key) {
              return { [rkey]: '' };
            } else {
              return handleChosenAwsConnection(rkey, value);
            }
          }
        }

        if (key === FormFields.SECRET_ACCESS_KEY) {
          const val = value as string;

          if (val === PASSWORD_STUB) {
            return { [rkey]: appProps.secretAcessKeyValue };
          } else {
            return { [rkey]: encodeSecret(value, appProps.publicKey) };
          }
        }

        return { [rkey]: value };
      } else {
        return { [rkey]: '' };
      }
    })
    .reduce((acc, cur) => ({ ...cur, ...acc }), {
      projectId: appProps.projectId,
    });
}

export function encodeSecret(value: string, publicKey: string): string {
  return window.BS.Encrypt.encryptData(value, publicKey);
}
