import { React } from '@jetbrains/teamcity-api';
import { useFormContext } from 'react-hook-form';
import {
  FormRow,
  FormSelect,
  useReadOnlyContext,
} from '@jetbrains-internal/tcci-react-ui-components';
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

const availAwsConnectionsDropDownStyle: React.CSSProperties = {
  zIndex: 100,
};

export default function AvailableAwsConnections() {
  const { connectionOptions, error, isLoading, reloadConnectionOptions } =
    useAwsConnectionsContext();
  const { control, getValues, setValue, setError, clearErrors } =
    useFormContext<IFormInput>();

  const [currentConnectionId, setCurrentConnectionId] = React.useState(
    getValues(FormFields.AWS_CONNECTION_ID)?.key?.id
  );

  const handleConnectionChange = React.useCallback(
    (event: any) => {
      setCurrentConnectionId(event?.target?.value?.key?.id);
      clearErrors();
    },
    [clearErrors]
  );

  React.useEffect(() => {
    if (error) {
      setError(FormFields.AWS_CONNECTION_ID, {
        message: error,
        type: 'custom',
      });
    } else {
      clearErrors(FormFields.AWS_CONNECTION_ID);
    }
  }, [clearErrors, error, setError]);

  const [show, setShow] = React.useState(false);
  const isReadOnly = useReadOnlyContext();
  const [connectionId, setConnectionId] = React.useState<string>('');

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
              onChange: handleConnectionChange,
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
            clearErrors();
          }
          setShow(false);
        }}
      />
    </div>
  );
}
