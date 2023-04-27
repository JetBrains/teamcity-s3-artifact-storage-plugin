import {
  FormInput,
  FormRow,
} from '@teamcity-cloud-integrations/react-ui-components';
import { React } from '@jetbrains/teamcity-api';
import { useFormContext } from 'react-hook-form';

import { FormFields } from '../../appConstants';

export default function StorageName() {
  const { control } = useFormContext();
  return (
    <FormRow label="Name" optional labelFor={FormFields.STORAGE_NAME}>
      <FormInput control={control} name={FormFields.STORAGE_NAME} />
    </FormRow>
  );
}
