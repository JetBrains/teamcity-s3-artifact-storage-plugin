import { React, utils } from '@jetbrains/teamcity-api';
import { FormProvider, useFormContext } from 'react-hook-form';
import Button from '@jetbrains/ring-ui/components/button/button';
import {
  errorMessage,
  FieldColumn,
  FieldRow,
  Option,
  ReadOnlyContextProvider,
  useErrorService,
  useJspContainer,
  useReadOnlyContext,
} from '@jetbrains-internal/tcci-react-ui-components';
import {
  ControlsHeight,
  ControlsHeightContext,
} from '@jetbrains/ring-ui/components/global/controls-height';
import { ResponseErrors } from '@jetbrains-internal/tcci-react-ui-components/dist/types';
import { BaseSyntheticEvent, useCallback } from 'react';

import { displayErrorsFromResponseIfAny } from '../Utilities/responseParser';
import { serializeParameters } from '../Utilities/parametersUtils';
import { post } from '../Utilities/fetchHelper';
import useS3Form from '../hooks/useS3Form';
import { ConfigWrapper, IFormInput } from '../types';
import useStorageOptions from '../hooks/useStorageOptions';
import { AppContextProvider, useAppContext } from '../contexts/AppContext';

import { BucketsContextProvider } from '../contexts/BucketsContext';
import useBucketOptions from '../hooks/useBucketOptions';

import { errorIdToFieldName, FormFields } from './appConstants';
import styles from './styles.css';
import { AWS_S3, S3_COMPATIBLE } from './Storage/components/StorageType';
import StorageSection from './Storage/StorageSection';
import S3Section from './S3Compatible/S3Section';
import AwsS3 from './S3/AwsS3';
import MultipartUploadSection from './MultipartUpload/MultipartUploadSection';
import ProtocolSettings from './ProtocolSettings/ProtocolSettings';
import StorageTypeChangedWarningDialog from './components/StorageTypeChangedWarningDialog';

const formId = 'S3ProfileForm';

function MainFormComponent(props: {
  onReset: (option: Option | null) => void;
  onClose: () => void;
}) {
  const config = useAppContext();
  const { handleSubmit, watch, setError, clearErrors } =
    useFormContext<IFormInput>();
  const { showErrorsOnForm, showErrorAlert } = useErrorService({
    setError,
    errorKeyToFieldNameConvertor: errorIdToFieldName,
  });
  const { reload } = useBucketOptions();
  const [isSaving, setIsSaving] = React.useState(false);

  const onSubmit = useCallback(
    async (data: IFormInput, event?: BaseSyntheticEvent) => {
      if (event?.target.id !== formId) {
        return;
      }

      setIsSaving(true);
      clearErrors();
      try {
        // validate bucket
        try {
          const bucketsFromCredentials = await reload();
          const currentlySelectedBucket = data[FormFields.S3_BUCKET_NAME];
          const bucketFound = bucketsFromCredentials.some((bucket) => {
            if (typeof currentlySelectedBucket === 'string') {
              return bucket.key === currentlySelectedBucket;
            } else {
              return bucket.key === currentlySelectedBucket?.key;
            }
          });
          if (!bucketFound) {
            setError(FormFields.S3_BUCKET_NAME, {
              type: 'custom',
              message: 'Bucket not found. Check your S3 credentials.',
            });
            return;
          }
        } catch (e) {
          showErrorAlert(errorMessage(e));
          return;
        }

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
          props.onClose();
        }
      } finally {
        setIsSaving(false);
      }
    },
    [
      clearErrors,
      config,
      props,
      reload,
      setError,
      showErrorAlert,
      showErrorsOnForm,
    ]
  );

  const currentType = watch(FormFields.STORAGE_TYPE);
  const isS3Compatible = currentType?.key === S3_COMPATIBLE;
  const isAwsS3 = currentType?.key === AWS_S3;
  const isReadOnly = useReadOnlyContext();

  return (
    <form
      className="ring-form"
      onSubmit={handleSubmit(onSubmit)}
      autoComplete="off"
      id={formId}
    >
      <StorageSection onReset={props.onReset} />

      {isS3Compatible && <S3Section />}
      {isAwsS3 && <AwsS3 />}

      <MultipartUploadSection />
      <ProtocolSettings />
      <div className={styles.formControlButtons}>
        <FieldRow>
          <FieldColumn>
            <Button
              disabled={isReadOnly}
              loader={isSaving}
              type="submit"
              primary
            >
              {'Save'}
            </Button>
          </FieldColumn>
          <FieldColumn>
            <Button onClick={props.onClose}>{'Cancel'}</Button>
          </FieldColumn>
        </FieldRow>
      </div>
    </form>
  );
}

function Main() {
  const config = useAppContext();
  // console.log(config);
  const resetUI = useJspContainer(
    '#storageParamsInner table.runnerFormTable, #saveButtons'
  );
  const formMethods = useS3Form();
  const storageOptions = useStorageOptions();

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

  return (
    <FormProvider {...formMethods}>
      <ControlsHeightContext.Provider value={ControlsHeight.S}>
        <BucketsContextProvider>
          <MainFormComponent onReset={doReset} onClose={close} />
          <StorageTypeChangedWarningDialog />
          {/*<DevTool control={formMethods.control} />*/}
        </BucketsContextProvider>
      </ControlsHeightContext.Provider>
    </FormProvider>
  );
}

function App({ config }: ConfigWrapper) {
  useJspContainer('#storageParamsInner table.runnerFormTable, #saveButtons');
  return (
    <ReadOnlyContextProvider value={config.readOnly}>
      <AppContextProvider value={config}>
        <Main />
      </AppContextProvider>
    </ReadOnlyContextProvider>
  );
}

export default React.memo(App);
