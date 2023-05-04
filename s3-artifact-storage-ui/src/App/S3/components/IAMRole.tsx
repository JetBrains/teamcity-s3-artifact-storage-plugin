import { React } from '@jetbrains/teamcity-api';
import {
  FormInput,
  FormRow,
} from '@teamcity-cloud-integrations/react-ui-components';

import { useFormContext } from 'react-hook-form';

import { IFormInput } from '../../../types';
import { FormFields } from '../../appConstants';

export default function IAMRole() {
  const { control } = useFormContext<IFormInput>();
  return (
    <FormRow label="IAM role" optional>
      <FormInput control={control} name={FormFields.IAM_ROLE_ARN} />
    </FormRow>
  );
}
