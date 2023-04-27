import {
  FormInput,
  FormRow,
} from '@teamcity-cloud-integrations/react-ui-components';
import { React } from '@jetbrains/teamcity-api';
import { useFormContext } from 'react-hook-form';

import { FormFields } from '../../appConstants';

export default function Endpoint() {
  const { control } = useFormContext();
  return (
    <FormRow
      label="Endpoint"
      star
      labelFor={FormFields.CUSTOM_AWS_ENDPOINT_URL}
    >
      <FormInput
        control={control}
        name={FormFields.CUSTOM_AWS_ENDPOINT_URL}
        rules={{ required: 'Endpoint URL is mandatory' }}
      />
    </FormRow>
  );
}
