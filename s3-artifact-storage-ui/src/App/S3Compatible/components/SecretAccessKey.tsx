import {
  FormInput,
  FormRow,
} from '@jetbrains-internal/tcci-react-ui-components';
import { React } from '@jetbrains/teamcity-api';
import { useFormContext } from 'react-hook-form';

import { FormFields } from '../../appConstants';

export default function SecretAccessKey() {
  const { control } = useFormContext();

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
        }}
        type="password"
      />
    </FormRow>
  );
}
