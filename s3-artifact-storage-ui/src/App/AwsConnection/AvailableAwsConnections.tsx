import { React } from '@jetbrains/teamcity-api';
import { useFormContext } from 'react-hook-form';
import {
  FormRow,
  FormSelect,
  useReadOnlyContext,
} from '@jetbrains-internal/tcci-react-ui-components';
import { CSSProperties, useCallback, useEffect, useState } from 'react';

import Button from '@jetbrains/ring-ui/components/button/button';

import addIcon from '@jetbrains/icons/add';

import { IFormInput } from '../../types';
import { FormFields } from '../appConstants';
import { useAwsConnectionsContext } from '../../contexts/AwsConnectionsContext';

import NewAwsConnectionDialog from './NewAwsConnectionDialog';
import { AwsConnection } from './AvailableAwsConnectionsConstants';

const availAwsConnectionsDropDownStyle: CSSProperties = {
  zIndex: 100,
};

export default function AvailableAwsConnections() {
  const { connectionOptions, error, isLoading, reloadConnectionOptions } =
    useAwsConnectionsContext();
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

  const [show, setShow] = useState(false);
  const isReadOnly = useReadOnlyContext();

  return (
    <div>
      <FormRow
        label="Connection"
        labelFor={`${FormFields.AWS_CONNECTION_ID}_key`}
      >
        <div style={{ display: 'flex', flexDirection: 'row' }}>
          <FormSelect
            id={`${FormFields.AWS_CONNECTION_ID}_key`}
            name={FormFields.AWS_CONNECTION_ID}
            control={control}
            data={connectionOptions}
            onBeforeOpen={reloadConnectionOptions}
            popupStyle={availAwsConnectionsDropDownStyle}
            rules={{
              required: 'Connection is mandatory',
              onChange: resetBucket,
            }}
            loading={isLoading}
          />
          <Button
            disabled={isReadOnly}
            style={{ marginTop: '20px' }}
            icon={addIcon}
            onClick={() => setShow(true)}
          />
        </div>
      </FormRow>
      <NewAwsConnectionDialog
        active={show}
        onClose={(newConnection: AwsConnection | undefined) => {
          if (newConnection) {
            setValue(FormFields.AWS_CONNECTION_ID, {
              key: newConnection,
              label: newConnection.displayName,
            });
            resetBucket();
          }
          setShow(false);
        }}
      />
    </div>
  );
}
