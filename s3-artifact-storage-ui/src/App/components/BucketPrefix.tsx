import {
  FormInput,
  FormRow,
} from '@jetbrains-internal/tcci-react-ui-components';
import { React } from '@jetbrains/teamcity-api';
import { useFormContext } from 'react-hook-form';

import { FormFields } from '../appConstants';

export default function BucketPrefix() {
  const { control } = useFormContext();
  return (
    <FormRow
      label="S3 path prefix"
      optional
      labelFor={FormFields.S3_BUCHET_PATH_PREFIX}
    >
      <FormInput control={control} name={FormFields.S3_BUCHET_PATH_PREFIX} />
    </FormRow>
  );
}
