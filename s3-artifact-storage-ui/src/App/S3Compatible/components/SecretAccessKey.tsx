import {
  FormInput,
  FormRow,
} from '@teamcity-cloud-integrations/react-ui-components';
import { React } from '@jetbrains/teamcity-api';
import { useFormContext } from 'react-hook-form';

import { useCallback } from 'react';

import { FormFields } from '../../appConstants';

export default function SecretAccessKey() {
  const { control, setValue } = useFormContext();

  const resetBucket = useCallback(() => {
    setValue(FormFields.S3_BUCKET_NAME, '');
  }, [setValue]);

  return (
    <FormRow
      label="Secret access key"
      star
      labelFor={FormFields.SECRET_ACCESS_KEY}
    >
      <FormInput
        name={FormFields.SECRET_ACCESS_KEY}
        control={control}
        rules={{
          required: 'Secret access key is mandatory',
          onChange: resetBucket,
        }}
        type="password"
      />
    </FormRow>
  );
}
