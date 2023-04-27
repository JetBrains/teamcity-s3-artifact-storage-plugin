import { React } from '@jetbrains/teamcity-api';
import { useFormContext } from 'react-hook-form';
import {
  FormInput,
  FormRow,
} from '@teamcity-cloud-integrations/react-ui-components';

import { IFormInput } from '../../types';
import { FormFields } from '../appConstants';

export default function SessionDurationField() {
  const { control, watch } = useFormContext<IFormInput>();
  const awsConnection = watch(FormFields.AWS_CONNECTION_ID);

  if (awsConnection && awsConnection.key.usingSessionCreds) {
    return (
      <FormRow label="Session Duration:" labelFor={FormFields.SESSION_DURATION}>
        <FormInput
          control={control}
          name={FormFields.SESSION_DURATION}
          defaultValue={'60'}
        />
      </FormRow>
    );
  } else {
    return null;
  }
}
