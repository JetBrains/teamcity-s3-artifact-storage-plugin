import {
  FormInput,
  FormRow,
} from '@teamcity-cloud-integrations/react-ui-components';
import { React } from '@jetbrains/teamcity-api';

import { useFormContext } from 'react-hook-form';

import { useCallback } from 'react';

import { FormFields } from '../../appConstants';

export default function AccessKeyId() {
  const { control, setValue } = useFormContext();

  const resetBucket = useCallback(() => {
    setValue(FormFields.S3_BUCKET_NAME, '');
  }, [setValue]);

  return (
    <FormRow label="Access key ID" star labelFor={FormFields.ACCESS_KEY_ID}>
      <FormInput
        name={FormFields.ACCESS_KEY_ID}
        control={control}
        rules={{
          required: 'Access key ID is mandatory',
          onChange: resetBucket,
        }}
      />
    </FormRow>
  );
}
