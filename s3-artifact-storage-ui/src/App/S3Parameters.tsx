import {React} from '@jetbrains/teamcity-api';

import {useFormContext} from 'react-hook-form';

import {FocusEventHandler, useEffect, useState} from 'react';

import {FormInput, FormRow, FormSelect, Option, SectionHeader, useErrorService} from '@teamcity-cloud-integrations/react-ui-components';

import {loadBucketList} from '../Utilities/fetchBucketNames';

import {Config, IFormInput} from '../types';

import {fetchBucketLocation} from '../Utilities/fetchBucketLocation';

import {errorIdToFieldName, FormFields} from './appConstants';

type OwnProps = Config

export const S3_BUCKET_FROM_LIST_OR_BY_NAME_ARRAY: Option<number>[] = [
  {label: 'Choose from list', key: 0},
  {label: 'Specify name', key: 1}
];

export default function S3Parameters({...config}: OwnProps) {
  const s3BucketListOrNameFieldName = FormFields.S3_BUCKET_LIST_OR_NAME;
  const {control, setError, setValue, getValues} = useFormContext<IFormInput>();
  const {showErrorsOnForm, showErrorAlert} = useErrorService({
    setError,
    errorKeyToFieldNameConvertor: errorIdToFieldName
  });
  const selectS3BucketListOrName = React.useCallback(
    (data: Option<number>) => {
      setValue(s3BucketListOrNameFieldName, data);
      if (data.key === 1) {
        setValue(FormFields.S3_BUCKET_NAME, null);
      }
    },
    [setValue, s3BucketListOrNameFieldName]
  );

  const loadBucketLocation = React.useCallback(() => {
    const values = getValues([FormFields.USE_DEFAULT_CREDENTIAL_PROVIDER_CHAIN,
      FormFields.ACCESS_KEY_ID,
      FormFields.SECRET_ACCESS_KEY]);
    return fetchBucketLocation(
      {
        appProps: config,
        allValues: getValues(),
        useDefaultCredentialProviderChain: values[0],
        keyId: values[1],
        keySecret: values[2]
      }
    ).then(({location, errors}) => {
      if (errors) {
        showErrorsOnForm(errors);
      }

      if (location) {
        setValue(FormFields.CUSTOM_AWS_REGION, location);
      }
    }).catch((error: Error) => (showErrorAlert(error.message)));
  }, [config, getValues, setValue, showErrorAlert, showErrorsOnForm]);

  const updateDefaultRegionName = React.useCallback<FocusEventHandler<HTMLInputElement>>(
    event => {
      if (event?.target?.value) {
        loadBucketLocation();
      }
    }, [loadBucketLocation]);

  const selectS3BucketNameListValue = React.useCallback(
    (selected: Option<number> | null) => {
      loadBucketLocation();
      setValue(FormFields.S3_BUCKET_NAME, selected);
    }, [loadBucketLocation, setValue]);

  const [manualFlag, setManualFlag] = useState(
    getValues(s3BucketListOrNameFieldName)?.key === S3_BUCKET_FROM_LIST_OR_BY_NAME_ARRAY[1].key);

  const [buckets, setBuckets] = useState<Option<number>[]>(
    (getValues(FormFields.S3_BUCKET_NAME) &&
     typeof getValues(FormFields.S3_BUCKET_NAME) === 'string')
      ? [{label: getValues(FormFields.S3_BUCKET_NAME) as string, key: 0}]
      : []
  );

  useEffect(() => {
    if (buckets.length > 0) {
      if (manualFlag) {
        setValue(FormFields.S3_BUCKET_NAME, buckets[0].label);
      } else {
        selectS3BucketNameListValue(buckets[0]);
      }
    }
  }, []); // we only need this effect to run once

  const [bucketsListLoading, setBucketsListLoading] = useState(false);

  const reloadBuckets = async () => {
    setBucketsListLoading(true);
    setBuckets([]);
    const values = getValues([FormFields.USE_DEFAULT_CREDENTIAL_PROVIDER_CHAIN,
      FormFields.ACCESS_KEY_ID,
      FormFields.SECRET_ACCESS_KEY]);
    const {bucketNames, errors} = await loadBucketList(
      {
        appProps: config,
        allValues: getValues(),
        useDefaultCredentialProviderChain: values[0],
        keyId: values[1],
        keySecret: values[2]
      }
    );
    if (bucketNames) {
      let i = 1;
      const bucketsData = bucketNames.reduce((acc, cur) => {
        acc.push({label: cur, key: i++});
        return acc;
      }, [] as Option<number>[]);
      setBuckets(bucketsData);
    }
    setBucketsListLoading(false);
    if (errors) {
      showErrorsOnForm(errors);
    }
  };

  return (
    <section>
      <SectionHeader>{'S3 Parameters'}</SectionHeader>
      <FormRow
        label="Specify S3 bucket"
        labelFor={s3BucketListOrNameFieldName}
      >
        <FormSelect
          name={s3BucketListOrNameFieldName}
          control={control}
          data={S3_BUCKET_FROM_LIST_OR_BY_NAME_ARRAY}
          onChange={(selected: Option<number> | null) => {
            if (selected) {
              selectS3BucketListOrName(selected);
            }
            if (selected?.key === 1) {
              setManualFlag(true);
            } else {
              setManualFlag(false);
            }
          }}
        />
      </FormRow>
      {manualFlag
        ? (
          <FormRow
            label="S3 bucket name"
            star
            labelFor={FormFields.S3_BUCKET_NAME}
          >
            <FormInput
              name={FormFields.S3_BUCKET_NAME}
              control={control}
              rules={{required: 'S3 bucket name is mandatory', onBlur: updateDefaultRegionName}}
              details="Specify the bucket name"
            />
          </FormRow>
        )
        : (
          <FormRow
            label="S3 bucket name"
            star
            labelFor={FormFields.S3_BUCKET_NAME}
          >
            <FormSelect
              name={FormFields.S3_BUCKET_NAME}
              control={control}
              data={buckets}
              filter
              onBeforeOpen={reloadBuckets}
              loading={bucketsListLoading}
              label="-- Select bucket --"
              rules={{required: 'S3 bucket name is mandatory'}}
              details="Existing S3 bucket to store artifacts"
              onChange={selectS3BucketNameListValue}
            />
          </FormRow>
        )}
      <FormRow
        label="S3 path prefix"
        labelFor={FormFields.S3_BUCHET_PATH_PREFIX}
      >
        <FormInput
          control={control}
          name={FormFields.S3_BUCHET_PATH_PREFIX}
          details="Specify the path prefix"
        />
      </FormRow>
    </section>
  );
}
