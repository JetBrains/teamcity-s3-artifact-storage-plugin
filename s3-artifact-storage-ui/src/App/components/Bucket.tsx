import {
  FormRow,
  FormSelect,
} from '@jetbrains-internal/tcci-react-ui-components';
import { React } from '@jetbrains/teamcity-api';
import { useFormContext } from 'react-hook-form';

import { FormFields } from '../appConstants';
import { IFormInput } from '../../types';
import { useBucketsContext } from '../../contexts/BucketsContext';
import useCanLoadBucketInfoData from '../../hooks/useCanLoadBucketInfoData';

export default function Bucket() {
  const canLoadBucketInfo = useCanLoadBucketInfoData();
  const isDisabled = !canLoadBucketInfo;
  const { control } = useFormContext<IFormInput>();
  const {
    bucketOptions: buckets,
    isLoading: bucketsListLoading,
    reloadBucketOptions: reloadBuckets,
  } = useBucketsContext();

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
        disabled={isDisabled}
      />
    </FormRow>
  );
}
