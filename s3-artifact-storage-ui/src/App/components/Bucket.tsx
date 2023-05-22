import { useCallback, useEffect, useState } from 'react';
import {
  errorMessage,
  FormRow,
  FormSelect,
  Option,
  useErrorService,
} from '@jetbrains-internal/tcci-react-ui-components';
import { React } from '@jetbrains/teamcity-api';
import { useFormContext } from 'react-hook-form';

import { errorIdToFieldName, FormFields } from '../appConstants';
import { loadBucketList } from '../../Utilities/fetchBucketNames';
import { useAppContext } from '../../contexts/AppContext';
import { fetchBucketLocation } from '../../Utilities/fetchBucketLocation';
import { AWS_S3, S3_COMPATIBLE } from '../Storage/components/StorageType';
import { fetchS3TransferAccelerationAvailability } from '../../Utilities/fetchS3TransferAccelerationAvailability';
import { IFormInput } from '../../types';

export default function Bucket() {
  const config = useAppContext();
  const { control, watch, getValues, setValue, clearErrors, setError } =
    useFormContext<IFormInput>();
  const [bucketsListLoading, setBucketsListLoading] = useState(false);
  const [buckets, setBuckets] = useState<Option[]>(() => {
    const bucketName = getValues(FormFields.S3_BUCKET_NAME);
    if (typeof bucketName === 'string' && bucketName.length > 0) {
      return [{ label: bucketName, key: bucketName }];
    } else {
      return [];
    }
  });
  const { showErrorsOnForm, showErrorAlert } = useErrorService({
    setError,
    errorKeyToFieldNameConvertor: errorIdToFieldName,
  });

  const awsConnectionId = watch(FormFields.AWS_CONNECTION_ID);
  const accessKeyId = watch(FormFields.ACCESS_KEY_ID);
  const secretAccessKey = watch(FormFields.SECRET_ACCESS_KEY);
  const currentType = watch(FormFields.STORAGE_TYPE);
  const isS3Compatible = currentType?.key === S3_COMPATIBLE;
  const isAwsS3 = currentType?.key === AWS_S3;
  const isDisabled =
    (isS3Compatible && (!accessKeyId || !secretAccessKey)) ||
    (isAwsS3 && !awsConnectionId);

  const reloadBuckets = useCallback(async () => {
    setBucketsListLoading(true);
    setBuckets([]);
    clearErrors(FormFields.S3_BUCKET_NAME);
    try {
      const { bucketNames, errors } = await loadBucketList(config, getValues());
      if (bucketNames) {
        const bucketsData = bucketNames.reduce((acc, cur) => {
          acc.push({ label: cur, key: cur });
          return acc;
        }, [] as Option[]);
        setBuckets(bucketsData);
      }
      if (errors) {
        showErrorsOnForm(errors);
      }
    } catch (e) {
      showErrorAlert(errorMessage(e));
    } finally {
      setBucketsListLoading(false);
    }
  }, [clearErrors, config, getValues, showErrorAlert, showErrorsOnForm]);

  const loadBucketLocation = React.useCallback(async () => {
    if (isDisabled || buckets.length < 1) {
      return;
    }
    try {
      setValue(FormFields.CUSTOM_AWS_REGION, '');
      const { location, errors } = await fetchBucketLocation(
        config,
        getValues()
      );
      if (errors) {
        showErrorsOnForm(errors);
      }
      if (location) {
        setValue(FormFields.CUSTOM_AWS_REGION, location, {
          shouldDirty: true,
          shouldTouch: true,
        });
      }
    } catch (error) {
      showErrorAlert(errorMessage(error));
    }
  }, [
    buckets.length,
    config,
    getValues,
    isDisabled,
    setValue,
    showErrorAlert,
    showErrorsOnForm,
  ]);

  const validateS3TransferAccelerationAvailability =
    React.useCallback(async () => {
      if (isDisabled || buckets.length < 1) {
        return;
      }
      try {
        const { isAvailable, errors } =
          await fetchS3TransferAccelerationAvailability(config, getValues());
        if (errors) {
          showErrorsOnForm(errors);
        } else {
          setValue(
            FormFields.S3_TRANSFER_ACCELERATION_AVAILABLE,
            isAvailable ?? false
          );
        }
      } catch (e) {
        showErrorAlert(errorMessage(e));
      }
    }, [
      buckets.length,
      config,
      getValues,
      isDisabled,
      setValue,
      showErrorAlert,
      showErrorsOnForm,
    ]);

  const selectS3BucketNameListValue = React.useCallback(
    (selected: Option | null) => {
      clearErrors();
      setValue(FormFields.CLOUD_FRONT_TOGGLE, false);
      setValue(FormFields.CONNECTION_TRANSFER_ACCELERATION_TOGGLE, false);
      if (selected) {
        setValue(FormFields.S3_BUCKET_NAME, selected, {
          shouldDirty: true,
          shouldTouch: true,
          shouldValidate: true,
        });
      }

      Promise.all([
        loadBucketLocation(),
        validateS3TransferAccelerationAvailability(),
      ]);
    },
    [
      clearErrors,
      setValue,
      loadBucketLocation,
      validateS3TransferAccelerationAvailability,
    ]
  );

  useEffect(() => {
    Promise.all([
      loadBucketLocation(),
      validateS3TransferAccelerationAvailability(),
    ]);
  }, []); // run once

  return (
    <FormRow label="Bucket" labelFor={FormFields.S3_BUCKET_NAME}>
      <FormSelect
        name={FormFields.S3_BUCKET_NAME}
        control={control}
        data={buckets}
        filter
        onBeforeOpen={reloadBuckets}
        loading={bucketsListLoading}
        label="-- Select bucket --"
        rules={{ required: 'S3 bucket name is mandatory' }}
        onChange={selectS3BucketNameListValue}
        disabled={isDisabled}
      />
    </FormRow>
  );
}
