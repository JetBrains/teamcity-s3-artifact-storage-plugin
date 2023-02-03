import {Option} from '@teamcity-cloud-integrations/react-ui-components';

import {FormFields, keyToFormDataName} from '../App/appConstants';
import {DistributionItem} from '../App/CloudFrontSettings';
import {Config, IFormInput} from '../types';

function valueOrDefault(condition: () => boolean, getter: () => string, defaultVal: any = '') {
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
  return {[rkey]: valueOrDefault(() => v.key !== 0, () => v.label)};
}

function handleS3BucketName(rkey: string, value: any) {
  const v = value as Option<number>;
  return {[rkey]: v.label};
}

function handleAwsEnvType(rkey: string, value: any) {
  const v = value as Option<number>;
  return {[rkey]: valueOrDefault(() => v.key !== 0, () => v.label)};
}

function handleCfPublicKey(rkey: string, value: any) {
  const v = value as Option;
  return {[rkey]: v.key};
}

function handleCfDistribution(rkey: string, value: any) {
  const v = value as DistributionItem;
  return {[rkey]: v.key};
}

export function serializeParameters(params: IFormInput, appProps: Config): { [k: string]: string } {
  return Object.keys(params).map(key => {
    // @ts-ignore
    const value = params[key];
    const rkey = keyToFormDataName(key);

    if (value) {
      if (typeof value === 'object') {
        if (key === FormFields.S3_BUCKET_LIST_OR_NAME) {
          return handleS3BucketListOrName(rkey, value);
        }
        if (key === FormFields.S3_BUCKET_NAME) {
          return handleS3BucketName(rkey, value);
        }
        if (key === FormFields.STORAGE_TYPE) {
          const v = value as Option;
          return {[rkey]: v.key};
        }
        if (key === FormFields.AWS_ENVIRONMENT_TYPE) {
          return handleAwsEnvType(rkey, value);
        }
        if (key === FormFields.CLOUD_FRONT_PUBLIC_KEY_ID) {
          return handleCfPublicKey(rkey, value);
        }
        if (key === FormFields.CLOUD_FRONT_DOWNLOAD_DISTRIBUTION ||
            key === FormFields.CLOUD_FRONT_UPLOAD_DISTRIBUTION) {
          return handleCfDistribution(rkey, value);
        }
      }

      if (key === FormFields.SECRET_ACCESS_KEY) {
        return {[rkey]: encodeSecret(value, appProps.publicKey)};
      }

      return {[rkey]: value};
    } else {
      return {[rkey]: ''};
    }
  }).reduce((acc, cur) => ({...cur, ...acc}), {projectId: appProps.projectId});
}

function encodeSecret(value: string, publicKey: string): string {
  return window.BS.Encrypt.encryptData(value, publicKey);
}

