import { React } from '@jetbrains/teamcity-api';
import {
  FormCheckbox,
  FormRow,
} from '@teamcity-cloud-integrations/react-ui-components';

import { useFormContext } from 'react-hook-form';

import { FormFields } from '../../appConstants';
import { IFormInput } from '../../../types';

export default function DefaultProviderChain() {
  const { control } = useFormContext<IFormInput>();

  return (
    <FormRow label="Default provider chain">
      <FormCheckbox
        name={FormFields.USE_DEFAULT_CREDENTIAL_PROVIDER_CHAIN}
        control={control}
      />
    </FormRow>
  );
}
