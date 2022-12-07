import {React, utils} from '@jetbrains/teamcity-api';
import {useCallback, useMemo, useState} from 'react';
import {FormProvider} from 'react-hook-form';

import Button from '@jetbrains/ring-ui/components/button/button';

import Theme, {ThemeProvider} from '@jetbrains/ring-ui/components/global/theme';

import {FormRow} from '../FormComponents/FormRow';

import FormInput from '../FormComponents/FormInput';

import {displayErrorsFromResponseIfAny, ResponseErrors} from '../Utilities/responseParser';

import {serializeParameters} from '../Utilities/parametersUtils';

import {post} from '../Utilities/fetchHelper';

import {FieldRow} from '../FormComponents/FieldRow';

import {FieldColumn} from '../FormComponents/FieldColumn';

import useS3Form from '../hooks/useS3Form';

import useJspContainer from '../hooks/useJspContainer';

import StorageType, {StorageTypeSelectItem} from './StorageType';


import AwsEnvironment, {AwsEnvType} from './AwsEnvironment';
import AwsSecurityCredentials from './AwsSecurityCredentials';
import S3Parameters, {BucketNameType, S3BucketNameSwitchType} from './S3Parameters';
import {errorIdToFieldName, FormFields} from './appConstants';
import CloudFrontSettings, {DistributionItem, PublicKeyItem} from './CloudFrontSettings';
import ConnectionSettings from './ConnectionSettings';


import {App as AppMainStyle, formControlButtons} from './styles.css';


export type ConfigWrapper = {
  config: Config
}

export interface Option {
  key: string,
  label: string
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
  [FormFields.STORAGE_TYPE]: StorageTypeSelectItem;
  [FormFields.STORAGE_NAME]: string;
  [FormFields.STORAGE_ID]: string;
  [FormFields.AWS_ENVIRONMENT_TYPE]: AwsEnvType;
  [FormFields.CUSTOM_AWS_ENDPOINT_URL]: string | null | undefined;
  [FormFields.CUSTOM_AWS_REGION]: string | null | undefined;
  [FormFields.CREDENTIALS_TYPE]: string;
  [FormFields.USE_DEFAULT_CREDENTIAL_PROVIDER_CHAIN]: boolean | null | undefined;
  [FormFields.ACCESS_KEY_ID]: string | undefined;
  [FormFields.SECRET_ACCESS_KEY]: string | undefined;
  [FormFields.IAM_ROLE_ARN]: string | undefined;
  [FormFields.EXTERNAL_ID]: string | null | undefined;
  [FormFields.S3_BUCKET_LIST_OR_NAME]: S3BucketNameSwitchType;
  [FormFields.S3_BUCKET_NAME]: string | BucketNameType | null;
  [FormFields.S3_BUCHET_PATH_PREFIX]: string | null | undefined;
  [FormFields.CLOUD_FRONT_TOGGLE]: boolean | null | undefined;
  [FormFields.CLOUD_FRONT_UPLOAD_DISTRIBUTION]: DistributionItem | null | undefined;
  [FormFields.CLOUD_FRONT_DOWNLOAD_DISTRIBUTION]: DistributionItem | null | undefined;
  [FormFields.CLOUD_FRONT_PUBLIC_KEY_ID]: PublicKeyItem | null | undefined;
  [FormFields.CLOUD_FRONT_FILE_WITH_PRIVATE_KEY]: string | null | undefined;
  [FormFields.CLOUD_FRONT_PRIVATE_KEY]: string | null | undefined;
  [FormFields.CONNECTION_PRESIGNED_URL_TOGGLE]: boolean | null | undefined;
  [FormFields.CONNECTION_FORCE_VHA_TOGGLE]: boolean | null | undefined;
  [FormFields.CONNECTION_TRANSFER_ACCELERATION_TOGGLE]: boolean | null | undefined;
  [FormFields.CONNECTION_MULTIPART_THRESHOLD]: string | null | undefined;
  [FormFields.CONNECTION_MULTIPART_CHUNKSIZE]: string | null | undefined;
}

function App({config}: ConfigWrapper) {
  const storageTypes = useMemo(() => config.storageTypes.split(/[\[\],]/).map(it => it.trim()).filter(it => !!it),
    [config]);
  const storageNames = useMemo(() => config.storageNames.split(/[\[\],]/).map(it => it.trim()).filter(it => !!it),
    [config]);
  const storageOptions = useMemo(() => storageTypes.reduce<Option[]>((acc, next, i) => {
    acc.push({key: next, label: storageNames[i]});
    return acc;
  }, []),
  [storageTypes, storageNames]);

  const [halt, setHalt] = useState(false);
  useJspContainer({halt, selector: '#storageParamsInner table.runnerFormTable'});
  const formMethods = useS3Form(config, storageOptions);

  const {
    handleSubmit,
    control,
    setError,
    clearErrors
  } = formMethods;

  const doReset = (option: StorageTypeSelectItem | null) => {
    setHalt(true);
    let ind = 0;
    if (option) {
      ind = storageOptions.findIndex(e => e.key === option.key);
      ind = ind < 0 ? 0 : ind;
    }
    // @ts-ignore
    $('editStorageForm').storageType.selectedIndex = ind;
    // @ts-ignore
    $('editStorageForm')['-ufd-teamcity-ui-storageType'].value = option?.label || storageOptions[0].label;
    // @ts-ignore
    $('storageParams').updateContainer();
  };

  const close = () => {
    document.location.href =
      `${utils.resolveRelativeURL('/admin/editProject.html')}?projectId=${config.projectId}&tab=artifactsStorage`;
  };

  const setErrors = useCallback((errors: ResponseErrors | null) => {
    if (errors) {
      Object.keys(errors).forEach(key => {
        const message: string = errors[key].message;
        const fieldName = errorIdToFieldName(key);
        if (fieldName) {
          if (Array.isArray(fieldName)) {
            fieldName.forEach(fn => setError(fn, {type: 'custom', message}));
          } else {
            setError(fieldName, {type: 'custom', message});
          }
        }
      });
    } else {
      clearErrors();
    }
  }, [setError, clearErrors]);

  const onSubmit = async (data: IFormInput) => {
    const payload = serializeParameters(data, config);
    const parameters = {
      projectId: config.projectId,
      newStorage: config.isNewStorage.toString(),
      [FormFields.STORAGE_TYPE]: data[FormFields.STORAGE_TYPE].key,
      [FormFields.STORAGE_ID]: data[FormFields.STORAGE_ID]
    };
    const queryComponents = new URLSearchParams(parameters).toString();
    const resp = await post(`/admin/storageParams.html?${queryComponents}`, payload);
    const response = window.$j(resp);
    const errors: ResponseErrors | null = displayErrorsFromResponseIfAny(response);
    if (errors) {
      setErrors(errors);
    } else {
      close();
    }
  };

  return (
    <ThemeProvider className={AppMainStyle} theme={Theme.LIGHT}>
      <FormProvider {...formMethods}>
        <form
          className="ring-form"
          onSubmit={handleSubmit(onSubmit)}
        >
          <section>
            <StorageType data={storageOptions} onChange={doReset}/>
            <FormRow
              label="Storage name:"
              labelFor={FormFields.STORAGE_NAME}
            >
              <FormInput control={control} name={FormFields.STORAGE_NAME}/>
            </FormRow>
            <FormRow
              label="Storage ID:"
              star
              labelFor={`${FormFields.STORAGE_ID}_key`}
            >
              <FormInput
                control={control}
                name={FormFields.STORAGE_ID}
                id={`${FormFields.STORAGE_ID}_key`}
                rules={{required: 'Storage ID is mandatory'}}
              />
            </FormRow>
          </section>
          <AwsEnvironment/>
          <AwsSecurityCredentials {...config}/>
          <S3Parameters setErrors={setErrors} {...config}/>
          {config.cloudfrontFeatureOn && (<CloudFrontSettings setErrors={setErrors} {...config}/>)}
          <ConnectionSettings {...config}/>
          <div className={formControlButtons}>
            <FieldRow>
              <FieldColumn>
                <Button type="submit" primary>{'Save'}</Button>
              </FieldColumn>
              <FieldColumn>
                <Button onClick={close}>{'Cancel'}</Button>
              </FieldColumn>
            </FieldRow>
          </div>
        </form>
        {/*<DevTool control={formMethods.control}/>*/}
      </FormProvider>
    </ThemeProvider>
  );
}

export default React.memo(App);
