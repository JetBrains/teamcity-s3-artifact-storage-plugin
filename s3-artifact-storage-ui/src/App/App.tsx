import {React, utils} from '@jetbrains/teamcity-api';
import {useCallback, useMemo, useState} from 'react';
import {FormProvider} from 'react-hook-form';

import Button from '@jetbrains/ring-ui/components/button/button';

import Theme, {ThemeProvider} from '@jetbrains/ring-ui/components/global/theme';

import {DevTool} from '@hookform/devtools';

import Alert, {AlertType} from '@jetbrains/ring-ui/components/alert/alert';

import {FormRow, FormInput, FieldRow, FieldColumn, Option} from '@teamcity-cloud-integrations/react-ui-components';

import {displayErrorsFromResponseIfAny, ResponseErrors} from '../Utilities/responseParser';

import {serializeParameters} from '../Utilities/parametersUtils';

import {post} from '../Utilities/fetchHelper';

import useS3Form from '../hooks/useS3Form';

import useJspContainer from '../hooks/useJspContainer';

import {ConfigWrapper, IFormInput} from '../types';

import StorageType from './StorageType';


import AwsEnvironment from './AwsEnvironment';
import AwsSecurityCredentials from './AwsSecurityCredentials';
import S3Parameters from './S3Parameters';
import {errorIdToFieldName, FormFields} from './appConstants';
import CloudFrontSettings from './CloudFrontSettings';
import ConnectionSettings from './ConnectionSettings';

import styles from './styles.css';

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

  const doReset = (option: Option | null) => {
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

  const [alertError, setAlertError] = useState<string | null>(null);

  const setErrors = useCallback((errors: ResponseErrors | null) => {
    setAlertError(null);
    if (errors) {
      Object.keys(errors).forEach(key => {
        const message: string = errors[key].message;
        const fieldName = errorIdToFieldName(key);
        if (fieldName) {
          if (Array.isArray(fieldName)) {
            fieldName.forEach(fn => setError(fn, {type: 'custom', message}));
          } else if (fieldName === FormFields.CLOUD_FRONT_PRIVATE_KEY) {
            setAlertError(message);
          } else {
            setError(fieldName, {type: 'custom', message});
          }
        } else {
          setAlertError(message);
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
      [FormFields.STORAGE_TYPE]: data[FormFields.STORAGE_TYPE]!.key,
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
    <ThemeProvider className={styles.App} theme={Theme.LIGHT}>
      <FormProvider {...formMethods}>
        <form
          className="ring-form"
          onSubmit={handleSubmit(onSubmit)}
          autoComplete="off"
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
          <div className={styles.formControlButtons}>
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
        {alertError != null && (
          <Alert
            type={AlertType.ERROR}
            onCloseRequest={() => setAlertError(null)}
          >{alertError}</Alert>
        )}
        <DevTool control={formMethods.control}/>
      </FormProvider>
    </ThemeProvider>
  );
}

export default React.memo(App);
