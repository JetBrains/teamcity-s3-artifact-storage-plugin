import {React, utils} from '@jetbrains/teamcity-api';
import {useMemo} from 'react';
import {FormProvider} from 'react-hook-form';
import Button from '@jetbrains/ring-ui/components/button/button';
import Theme, {ThemeProvider} from '@jetbrains/ring-ui/components/global/theme';
import {FieldColumn, FieldRow, FormInput, FormRow, Option, SectionHeader, useErrorService, useJspContainer} from '@teamcity-cloud-integrations/react-ui-components';
import {ControlsHeight, ControlsHeightContext} from '@jetbrains/ring-ui/components/global/controls-height';

import {displayErrorsFromResponseIfAny, ResponseErrors} from '../Utilities/responseParser';
import {serializeParameters} from '../Utilities/parametersUtils';
import {post} from '../Utilities/fetchHelper';
import useS3Form from '../hooks/useS3Form';
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

  const resetUI = useJspContainer('#storageParamsInner table.runnerFormTable, #saveButtons');

  const formMethods = useS3Form(config, storageOptions);

  const {
    handleSubmit,
    control,
    setError
  } = formMethods;

  const doReset = (option: Option | null) => {
    resetUI();
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

  const {showErrorsOnForm} = useErrorService({
    setError,
    errorKeyToFieldNameConvertor: errorIdToFieldName
  });

  const onSubmit = async (data: IFormInput) => {
    const payload = serializeParameters(data, config);
    const parameters = {
      projectId: config.projectId,
      newStorage: config.isNewStorage.toString(),
      [FormFields.STORAGE_TYPE]: data[FormFields.STORAGE_TYPE]!.key,
      [FormFields.STORAGE_ID]: data[FormFields.STORAGE_ID]!
    };
    const queryComponents = new URLSearchParams(parameters).toString();
    const resp = await post(`/admin/storageParams.html?${queryComponents}`, payload);
    const response = window.$j(resp);
    const errors: ResponseErrors | null = displayErrorsFromResponseIfAny(response);
    if (errors) {
      showErrorsOnForm(errors);
    } else {
      close();
    }
  };

  return (
    <ThemeProvider className={styles.App} theme={Theme.LIGHT}>
      <FormProvider {...formMethods}>
        <ControlsHeightContext.Provider value={ControlsHeight.S}>

          <form
            className="ring-form"
            onSubmit={handleSubmit(onSubmit)}
            autoComplete="off"
          >
            <section>
              <SectionHeader>{'Storage'}</SectionHeader>
              <StorageType data={storageOptions} onChange={doReset}/>
              <FormRow
                label="Storage name"
                labelFor={FormFields.STORAGE_NAME}
              >
                <FormInput control={control} name={FormFields.STORAGE_NAME}/>
              </FormRow>
              <FormRow
                label="Storage ID"
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
            <S3Parameters {...config}/>
            {config.cloudfrontFeatureOn && (<CloudFrontSettings {...config}/>)}
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

          {/*<DevTool control={formMethods.control}/>*/}

        </ControlsHeightContext.Provider>
      </FormProvider>
    </ThemeProvider>
  );
}

export default React.memo(App);
