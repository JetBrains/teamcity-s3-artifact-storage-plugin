import { React, utils } from '@jetbrains/teamcity-api';
import { FormProvider } from 'react-hook-form';
import Button from '@jetbrains/ring-ui/components/button/button';
import {
  FieldColumn,
  FieldRow,
  Option,
  useErrorService,
  useJspContainer,
} from '@jetbrains-internal/tcci-react-ui-components';
import {
  ControlsHeight,
  ControlsHeightContext,
} from '@jetbrains/ring-ui/components/global/controls-height';

import { ResponseErrors } from '@jetbrains-internal/tcci-react-ui-components/dist/types';

import Loader from '@jetbrains/ring-ui/components/loader/loader';

import { useCallback } from 'react';

import { displayErrorsFromResponseIfAny } from '../Utilities/responseParser';
import { serializeParameters } from '../Utilities/parametersUtils';
import { post } from '../Utilities/fetchHelper';
import useS3Form from '../hooks/useS3Form';
import { ConfigWrapper, IFormInput } from '../types';

import useStorageOptions from '../hooks/useStorageOptions';

import { AppContextProvider, useAppContext } from '../contexts/AppContext';

import {
  AwsConnectionsContextProvider,
  useAwsConnectionsContext,
} from '../contexts/AwsConnectionsContext';

import { errorIdToFieldName, FormFields } from './appConstants';

import styles from './styles.css';
import { AWS_S3, S3_COMPATIBLE } from './Storage/components/StorageType';
import StorageSection from './Storage/StorageSection';
import S3Section from './S3Compatible/S3Section';
import AwsS3 from './S3/AwsS3';
import MultipartUploadSection from './MultipartUpload/MultipartUploadSection';
import ProtocolSettings from './ProtocolSettings/ProtocolSettings';

function Main() {
  const config = useAppContext();
  // console.log(config);
  const resetUI = useJspContainer(
    '#storageParamsInner table.runnerFormTable, #saveButtons'
  );
  const formMethods = useS3Form();
  const storageOptions = useStorageOptions();

  const { handleSubmit, setError, watch } = formMethods;

  const doReset = useCallback(
    (option: Option | null) => {
      resetUI();
      let ind = 0;
      if (option) {
        ind = storageOptions.findIndex((e) => e.key === option.key);
        ind = ind < 0 ? 0 : ind;
      }
      // @ts-ignore
      $('editStorageForm').storageType.selectedIndex = ind;
      // @ts-ignore
      $('editStorageForm')['-ufd-teamcity-ui-storageType'].value =
        option?.label || storageOptions[0].label;
      // @ts-ignore
      $('storageParams').updateContainer();
    },
    [resetUI, storageOptions]
  );

  const close = useCallback(() => {
    document.location.href = `${utils.resolveRelativeURL(
      '/admin/editProject.html'
    )}?projectId=${config.projectId}&tab=artifactsStorage`;
  }, [config.projectId]);

  const { showErrorsOnForm } = useErrorService({
    setError,
    errorKeyToFieldNameConvertor: errorIdToFieldName,
  });

  const onSubmit = useCallback(
    async (data: IFormInput) => {
      const payload = serializeParameters(data, config);
      const parameters = {
        projectId: config.projectId,
        newStorage: config.isNewStorage.toString(),
        [FormFields.STORAGE_TYPE]: data[FormFields.STORAGE_TYPE]!.key,
        [FormFields.STORAGE_ID]: data[FormFields.STORAGE_ID]!,
      };
      const queryComponents = new URLSearchParams(parameters).toString();
      const resp = await post(
        `/admin/storageParams.html?${queryComponents}`,
        payload
      );
      const response = window.$j(resp);
      const errors: ResponseErrors | null =
        displayErrorsFromResponseIfAny(response);
      if (errors) {
        showErrorsOnForm(errors);
      } else {
        close();
      }
    },
    [close, config, showErrorsOnForm]
  );

  const currentType = watch(FormFields.STORAGE_TYPE);
  const isS3Compatible = currentType?.key === S3_COMPATIBLE;
  const isAwsS3 = currentType?.key === AWS_S3;

  return (
    <FormProvider {...formMethods}>
      <ControlsHeightContext.Provider value={ControlsHeight.S}>
        <form
          className="ring-form"
          onSubmit={handleSubmit(onSubmit)}
          autoComplete="off"
        >
          <StorageSection onReset={doReset} />

          {isS3Compatible && <S3Section />}
          {isAwsS3 && <AwsS3 />}

          <MultipartUploadSection />
          <ProtocolSettings />
          <div className={styles.formControlButtons}>
            <FieldRow>
              <FieldColumn>
                <Button type="submit" primary>
                  {'Save'}
                </Button>
              </FieldColumn>
              <FieldColumn>
                <Button onClick={close}>{'Cancel'}</Button>
              </FieldColumn>
            </FieldRow>
          </div>
        </form>

        {/*<DevTool control={formMethods.control} />*/}
      </ControlsHeightContext.Provider>
    </FormProvider>
  );
}

function AwaitConnections() {
  const { isLoading } = useAwsConnectionsContext();

  if (isLoading) {
    return <Loader />;
  }

  return <Main />;
}

function App({ config }: ConfigWrapper) {
  useJspContainer('#storageParamsInner table.runnerFormTable, #saveButtons');
  return (
    <AppContextProvider value={config}>
      <AwsConnectionsContextProvider>
        <AwaitConnections />
      </AwsConnectionsContextProvider>
    </AppContextProvider>
  );
}

export default React.memo(App);
