import {React} from '@jetbrains/teamcity-api';

import {useFormContext} from 'react-hook-form';

import {useEffect, useState} from 'react';

import FormSelect from '../FormComponents/FormSelect';
import {FormRow} from '../FormComponents/FormRow';


import FormInput from '../FormComponents/FormInput';
import {loadBucketList} from '../Utilities/fetchBucketNames';
import {ResponseErrors} from '../Utilities/responseParser';
import {SectionHeader} from '../FormComponents/SectionHeader';

import {Config, IFormInput} from './App';
import {FormFields} from './appConstants';

export type S3BucketNameSwitchType = {
  label: string,
  key: number
}

export type BucketNameType = {
  label: string,
  key: number
}

interface OwnProps extends Config {
    setErrors: (errors: (ResponseErrors | null)) => void
}

export const S3_BUCKET_FROM_LIST_OR_BY_NAME_ARRAY = [
  {label: 'Choose from list', key: 0},
  {label: 'Specify name', key: 1}
];

export default function S3Parameters({setErrors, ...config}: OwnProps) {
  const s3BucketListOrNameFieldName = FormFields.S3_BUCKET_LIST_OR_NAME;
  const {control, setValue, getValues, trigger} = useFormContext<IFormInput>();
  const selectS3BucketListOrName = React.useCallback(
    (data: S3BucketNameSwitchType) => {
      setValue(s3BucketListOrNameFieldName, data);
      if (data.key === 1) {
        setValue(FormFields.S3_BUCKET_NAME, null);
      }
    },
    [setValue, s3BucketListOrNameFieldName]
  );
  const selectS3BucketNameListValue = React.useCallback(
    (data: BucketNameType | null) => {
      setValue(FormFields.S3_BUCKET_NAME, data);
    },
    [setValue]
  );

  const [manualFlag, setManualFlag] = useState(
    getValues(s3BucketListOrNameFieldName).key === S3_BUCKET_FROM_LIST_OR_BY_NAME_ARRAY[1].key);
  const [buckets, setBuckets] = useState<BucketNameType[]>(
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
  }, [selectS3BucketNameListValue,
    selectS3BucketListOrName,
    trigger, buckets,
    manualFlag, setValue]);

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
      },
                                             [] as BucketNameType[]);
      setBuckets(bucketsData);
    }
    setBucketsListLoading(false);
    setErrors(errors);
  };

  return (
    <section>
      <SectionHeader>{'S3 Parameters'}</SectionHeader>
      <FormRow
        label="Specify S3 bucket:"
        labelFor={s3BucketListOrNameFieldName}
      >
        <FormSelect
          name={s3BucketListOrNameFieldName}
          control={control}
          data={S3_BUCKET_FROM_LIST_OR_BY_NAME_ARRAY}
          onChange={(option: S3BucketNameSwitchType | null) => {
            if (option) {
              selectS3BucketListOrName(option);
            }
            if (option?.key === 1) {
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
            label="S3 bucket name:"
            star
            labelFor={FormFields.S3_BUCKET_NAME}
          >
            <FormInput
              name={FormFields.S3_BUCKET_NAME}
              control={control}
              rules={{required: 'S3 bucket name is mandatory'}}
              details="Specify the bucket name"
            />
          </FormRow>
        )
        : (
          <FormRow
            label="S3 bucket name:"
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
        label="S3 path prefix:"
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
