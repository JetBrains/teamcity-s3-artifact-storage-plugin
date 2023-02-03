import {React} from '@jetbrains/teamcity-api';
import {useFormContext} from 'react-hook-form';

import {useState} from 'react';

import {FormRow, FormSelect, Option, FormInput} from '@teamcity-cloud-integrations/react-ui-components';

import {IFormInput} from '../types';

import {FormFields} from './appConstants';

export const AWS_ENV_TYPE_ARRAY: Array<Option<number>> = [
  {label: '<Default>', key: 0},
  {label: 'Custom', key: 1}
];

export default function AwsEnvironment() {
  const {control, setValue} = useFormContext<IFormInput>();
  const selectAwsEnvironmentType = React.useCallback(
    (data: Option<number>) => {
      setValue(FormFields.AWS_ENVIRONMENT_TYPE, data);
    },
    [setValue]
  );

  const [customFlag, setCustomFlag] = useState(false);

  return (
    <section>
      <FormRow
        label="AWS environment:"
        labelFor={FormFields.AWS_ENVIRONMENT_TYPE}
      >
        <FormSelect
          name={FormFields.AWS_ENVIRONMENT_TYPE}
          control={control}
          data={AWS_ENV_TYPE_ARRAY}
          onChange={(option: Option<number> | null) => {
            if (option) {
              selectAwsEnvironmentType(option);
            }
            if (option?.key === 1) {
              setCustomFlag(true);
            } else {
              setCustomFlag(false);
            }
          }}
        />
      </FormRow>
      {customFlag && (
        <>
          <FormRow
            label="Endpoint URL:"
            star
            labelFor={FormFields.CUSTOM_AWS_ENDPOINT_URL}
          >
            <FormInput
              control={control}
              name={FormFields.CUSTOM_AWS_ENDPOINT_URL}
              rules={{required: 'Endpoint URL is mandatory'}}
            />
          </FormRow>
          <FormRow
            label="AWS region:"
            star
            labelFor={FormFields.CUSTOM_AWS_REGION}
          >
            <FormInput
              control={control}
              name={FormFields.CUSTOM_AWS_REGION}
              rules={{required: 'Region is mandatory'}}
            />
          </FormRow>
        </>
      )}
    </section>
  );
}
