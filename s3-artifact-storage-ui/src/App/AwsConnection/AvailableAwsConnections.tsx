import { React } from '@jetbrains/teamcity-api';
import { useFormContext } from 'react-hook-form';
import {
  FormRow,
  FormSelect,
} from '@teamcity-cloud-integrations/react-ui-components';
import { CSSProperties, useCallback, useEffect } from 'react';

import { IFormInput } from '../../types';
import { FormFields } from '../appConstants';
import { useAwsConnectionsContext } from '../../contexts/AwsConnectionsContext';

const availAwsConnectionsDropDownStyle: CSSProperties = {
  zIndex: 100,
};

export default function AvailableAwsConnections() {
  const { connectionOptions, error, isLoading } = useAwsConnectionsContext();
  const { control, setValue, setError, clearErrors } =
    useFormContext<IFormInput>();

  useEffect(() => {
    if (error) {
      setError(FormFields.AWS_CONNECTION_ID, {
        message: error,
        type: 'custom',
      });
    } else {
      clearErrors(FormFields.AWS_CONNECTION_ID);
    }
  }, [clearErrors, error, setError]);

  const resetBucket = useCallback(() => {
    clearErrors();
    setValue(FormFields.CLOUD_FRONT_TOGGLE, false);
    setValue(FormFields.CONNECTION_TRANSFER_ACCELERATION_TOGGLE, false);
    setValue(FormFields.S3_BUCKET_NAME, '');
  }, [clearErrors, setValue]);

  return (
    <div>
      <FormRow
        label="Connection"
        labelFor={`${FormFields.AWS_CONNECTION_ID}_key`}
      >
        <FormSelect
          id={`${FormFields.AWS_CONNECTION_ID}_key`}
          name={FormFields.AWS_CONNECTION_ID}
          control={control}
          data={connectionOptions}
          popupStyle={availAwsConnectionsDropDownStyle}
          rules={{ required: 'Connection is mandatory', onChange: resetBucket }}
          loading={isLoading}
        />
      </FormRow>
    </div>
  );
}
