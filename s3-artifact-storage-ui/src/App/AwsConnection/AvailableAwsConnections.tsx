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
import editIcon from '@jetbrains/icons/pencil';

import ButtonSet from '@jetbrains/ring-ui/components/button-set/button-set';

import { IFormInput } from '../../types';
import { FormFields } from '../appConstants';
import { useAwsConnectionsContext } from '../../contexts/AwsConnectionsContext';

import AwsConnectionDialog from './AwsConnectionDialog';
import { AwsConnection } from './AvailableAwsConnectionsConstants';

import styles from './styles.css';

const availAwsConnectionsDropDownStyle: CSSProperties = {
  zIndex: 100,
};

export default function AvailableAwsConnections() {
  const { connectionOptions, error, isLoading, reloadConnectionOptions } =
    useAwsConnectionsContext();
  const { control, watch, setValue, setError, clearErrors } =
    useFormContext<IFormInput>();

  const currentConnectionId = watch(FormFields.AWS_CONNECTION_ID)?.key?.id;

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
  const [connectionId, setConnectionId] = useState<string>('');

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
          <ButtonSet className={styles.iconButtonsSet}>
            <Button
              disabled={isReadOnly}
              style={{ marginTop: '20px' }}
              icon={addIcon}
              title={'Create AWS connection'}
              onClick={() => {
                setConnectionId('');
                setShow(true);
              }}
            />
            <Button
              disabled={isReadOnly || !currentConnectionId}
              style={{ marginTop: '20px' }}
              icon={editIcon}
              title={'Edit AWS connection'}
              onClick={() => {
                setConnectionId(currentConnectionId ?? '');
                setShow(true);
              }}
            />
          </ButtonSet>
        </div>
      </FormRow>
      <AwsConnectionDialog
        active={show}
        mode={connectionId ? 'edit' : 'add'}
        awsConnectionIdProp={connectionId}
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
