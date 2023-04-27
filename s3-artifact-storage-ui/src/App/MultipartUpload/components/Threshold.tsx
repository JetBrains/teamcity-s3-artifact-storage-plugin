import {
  FormInput,
  FormRow,
  LabelWithHelp,
} from '@teamcity-cloud-integrations/react-ui-components';
import { React } from '@jetbrains/teamcity-api';
import { useFormContext } from 'react-hook-form';

import { FormFields } from '../../appConstants';

// const multipartUploadUrl = 'https://www.jetbrains.com/help/teamcity/2022.10/?Configuring+Artifacts+Storage#multipartUpload';

function TresholdLabel() {
  return (
    <LabelWithHelp
      label={'Threshold'}
      helpText={
        'Initiates multipart upload for files larger than the specified value'
      }
    />
  );
}

export default function Threshold() {
  const { control } = useFormContext();
  return (
    <FormRow
      label={<TresholdLabel />}
      labelFor={FormFields.CONNECTION_MULTIPART_THRESHOLD}
    >
      <FormInput
        name={FormFields.CONNECTION_MULTIPART_THRESHOLD}
        control={control}
        details="Minimum value is 5MB. Allowed suffixes: KB, MB, GB, TB. Leave empty to use the default value."
      />
    </FormRow>
  );
}
