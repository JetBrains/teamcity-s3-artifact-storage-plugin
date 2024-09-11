import { useFormContext } from 'react-hook-form';

import { FormFields } from '../App/appConstants';
import { AWS_S3, S3_COMPATIBLE } from '../App/Storage/components/StorageType';
import { IFormInput } from '../types';

export default function useCanLoadBucketInfoData() {
  const { watch } = useFormContext<IFormInput>();
  const awsConnectionId = watch(FormFields.AWS_CONNECTION_ID);
  const accessKeyId = watch(FormFields.ACCESS_KEY_ID);
  const secretAccessKey = watch(FormFields.SECRET_ACCESS_KEY);
  const currentType = watch(FormFields.STORAGE_TYPE);
  const isS3Compatible = currentType?.key === S3_COMPATIBLE;
  const isAwsS3 = currentType?.key === AWS_S3;
  const isDisabled =
    (isS3Compatible && (!accessKeyId || !secretAccessKey)) ||
    (isAwsS3 && !awsConnectionId?.key && (!accessKeyId || !secretAccessKey));

  return !isDisabled;
}
