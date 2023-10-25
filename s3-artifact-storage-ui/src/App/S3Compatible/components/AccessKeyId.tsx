import {
  FormInput,
  FormRow,
} from '@jetbrains-internal/tcci-react-ui-components';
import { React } from '@jetbrains/teamcity-api';
import { useFormContext } from 'react-hook-form';

import { FormFields } from '../../appConstants';

export default function AccessKeyId() {
  const { control } = useFormContext();

  return (
    <FormRow label="Access key ID" star labelFor={FormFields.ACCESS_KEY_ID}>
      <FormInput
        name={FormFields.ACCESS_KEY_ID}
        control={control}
        rules={{
          required: 'Access key ID is mandatory',
        }}
      />
    </FormRow>
  );
}
