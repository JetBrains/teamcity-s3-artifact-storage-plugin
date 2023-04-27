import {
  FormInput,
  FormRow,
  LabelWithHelp,
} from '@teamcity-cloud-integrations/react-ui-components';
import { React } from '@jetbrains/teamcity-api';

import { useFormContext } from 'react-hook-form';

import { FormFields } from '../../appConstants';

function StorageIdLabel() {
  return (
    <LabelWithHelp
      label={'ID'}
      helpText={
        'This ID is used in URLs, REST API, HTTP requests to the server, and configuration settings in the TeamCity Data Directory'
      }
    />
  );
}
export default function StorageId() {
  const { control } = useFormContext();

  return (
    <FormRow
      label={<StorageIdLabel />}
      star
      labelFor={`${FormFields.STORAGE_ID}_key`}
    >
      <FormInput
        control={control}
        name={FormFields.STORAGE_ID}
        id={`${FormFields.STORAGE_ID}_key`}
        rules={{ required: 'Storage ID is mandatory' }}
      />
    </FormRow>
  );
}
