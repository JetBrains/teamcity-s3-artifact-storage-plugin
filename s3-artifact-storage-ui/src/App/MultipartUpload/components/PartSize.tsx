import {
  FormInput,
  FormRow,
} from '@teamcity-cloud-integrations/react-ui-components';
import { React } from '@jetbrains/teamcity-api';
import { useFormContext } from 'react-hook-form';

import { FormFields } from '../../appConstants';

export default function PartSize() {
  const { control } = useFormContext();
  return (
    <FormRow
      label="Part size"
      labelFor={FormFields.CONNECTION_MULTIPART_CHUNKSIZE}
    >
      <FormInput
        name={FormFields.CONNECTION_MULTIPART_CHUNKSIZE}
        control={control}
        details="Minimum value is 5MB. Allowed suffixes: KB, MB, GB, TB. Leave empty to use the default value."
      />
    </FormRow>
  );
}
