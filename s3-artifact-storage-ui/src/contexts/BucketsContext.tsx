import { React } from '@jetbrains/teamcity-api';
import {
  Option,
  useErrorService,
} from '@jetbrains-internal/tcci-react-ui-components';
import { useFormContext } from 'react-hook-form';

import useBucketOptions from '../hooks/useBucketOptions';
import useBucketLocation from '../hooks/useBucketLocation';
import useTransferAccelerationAvailable from '../hooks/useTransferAccelerationAvailable';
import { errorIdToFieldName, FormFields } from '../App/appConstants';
import { IFormInput } from '../types';

type BucketsContextType = {
  isLoading: boolean;
  bucketOptions: Option[] | undefined;
  reloadBucketOptions: () => Promise<Option[]>;
};

const BucketsContext = React.createContext<BucketsContextType>({
  bucketOptions: undefined,
  isLoading: true,
  reloadBucketOptions: () => Promise.resolve([]),
});

const { Provider } = BucketsContext;

function BucketsContextProvider({ children }: { children: React.ReactNode }) {
  const { getValues, setValue, setError, watch, clearErrors } =
    useFormContext<IFormInput>();
  const [customFormState, setCustomFormState] = React.useState<any>(
    getValues()
  );
  const { showErrorsOnForm, showErrorAlert, clearAlerts } = useErrorService({
    setError,
    errorKeyToFieldNameConvertor: errorIdToFieldName,
  });

  const {
    bucketOptions,
    errors: bucketOptionsErrors,
    reload: reloadBucketOptions,
    loading: isBucketOptionsLoading,
  } = useBucketOptions();

  const {
    bucketLocation,
    errors: bucketLocationErrors,
    reload: reloadBucketLocation,
    isLoading: isBucketLocationLoading,
  } = useBucketLocation(bucketOptions);

  React.useEffect(() => {
    if (!isBucketLocationLoading) {
      setValue(FormFields.CUSTOM_AWS_REGION, bucketLocation);
    }
  }, [bucketLocation, isBucketLocationLoading, setValue]);

  const {
    available: isS3TransferAccelerationAvailable,
    errors: s3TransferAccelerationErrors,
    reload: reloadS3TransferAcceleration,
    isLoading: isS3TransferAccelerationLoading,
    triggered: isS3TransferAccelerationTriggered,
  } = useTransferAccelerationAvailable();


  React.useEffect(() => {
    if (isS3TransferAccelerationTriggered && !isS3TransferAccelerationLoading) {
      setValue(
        FormFields.S3_TRANSFER_ACCELERATION_AVAILABLE,
        isS3TransferAccelerationAvailable
      );
      const currentTransferAccelerationValue = customFormState[
        FormFields.CONNECTION_TRANSFER_ACCELERATION_TOGGLE
      ] as boolean;
      setValue(
        FormFields.CONNECTION_TRANSFER_ACCELERATION_TOGGLE,
        currentTransferAccelerationValue && isS3TransferAccelerationAvailable
      );
    }
  }, [
    customFormState,
    isS3TransferAccelerationAvailable,
    isS3TransferAccelerationLoading,
    setValue,
  ]);

  const isLoading =
    isBucketOptionsLoading ||
    isBucketLocationLoading ||
    isS3TransferAccelerationLoading;

  // Watch bucket changes
  const fieldChanged = React.useCallback(
    (fieldName: string, currentValue: any) => {
      try {
        return (
          JSON.stringify(currentValue) !==
          JSON.stringify(customFormState[fieldName])
        );
      } catch (e) {
        return true;
      }
    },
    [customFormState]
  );
  const reloadBucketInfoIfBucketChanged = React.useCallback(
    (data: any) => {
      if (
        !isLoading &&
        data[FormFields.S3_BUCKET_NAME] &&
        fieldChanged(FormFields.S3_BUCKET_NAME, data[FormFields.S3_BUCKET_NAME])
      ) {
        Promise.all([reloadBucketLocation(), reloadS3TransferAcceleration()]);
      }
    },
    [
      fieldChanged,
      isLoading,
      reloadBucketLocation,
      reloadS3TransferAcceleration,
    ]
  );

  React.useEffect(() => {
    reloadS3TransferAcceleration();
  }, []); // fire only once

  const cleanStateIfCredentialsChanged = React.useCallback(
    (data: any) => {
      if (
        data[FormFields.S3_BUCKET_NAME] &&
        data[FormFields.AWS_CONNECTION_ID] &&
        fieldChanged(
          FormFields.AWS_CONNECTION_ID,
          data[FormFields.AWS_CONNECTION_ID]
        )
      ) {
        reloadBucketOptions()
          .then((options: Option[]) =>
            options.some((option) => {
              if (typeof data[FormFields.S3_BUCKET_NAME] === 'string') {
                return option.key === data[FormFields.S3_BUCKET_NAME];
              } else {
                return option.key === data[FormFields.S3_BUCKET_NAME]?.key;
              }
            })
          )
          .then((isBucketInOptions) => {
            if (!isBucketInOptions) {
              setValue(FormFields.S3_BUCKET_NAME, '');
              setValue(FormFields.CLOUD_FRONT_TOGGLE, false);
              setValue(
                FormFields.CONNECTION_TRANSFER_ACCELERATION_TOGGLE,
                false
              );
            }
          });
      }
    },
    [fieldChanged, reloadBucketOptions, setValue]
  );

  React.useEffect(() => {
    const subscription = watch((data) => {
      reloadBucketInfoIfBucketChanged(data);
      cleanStateIfCredentialsChanged(data);
      setCustomFormState(data);
    });

    return () => {
      subscription.unsubscribe();
    };
  }, [cleanStateIfCredentialsChanged, reloadBucketInfoIfBucketChanged, watch]);

  // Handle errors
  const errors =
    bucketOptionsErrors || bucketLocationErrors || s3TransferAccelerationErrors;
  React.useEffect(() => {
    clearAlerts();
    clearErrors();
    if (errors) {
      if (typeof errors === 'string') {
        showErrorAlert(errors);
      } else {
        showErrorsOnForm(errors);
      }
    }
  }, [clearAlerts, clearErrors, errors, showErrorAlert, showErrorsOnForm]);

  // Set context values
  const value = React.useMemo(
    () => ({
      bucketOptions,
      isLoading,
      reloadBucketOptions,
    }),
    [bucketOptions, isLoading, reloadBucketOptions]
  );

  return <Provider value={value}>{children}</Provider>;
}

const useBucketsContext = () => React.useContext(BucketsContext);

export { BucketsContextProvider, useBucketsContext };
