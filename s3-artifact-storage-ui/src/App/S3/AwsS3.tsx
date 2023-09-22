import { React } from '@jetbrains/teamcity-api';
import { SectionHeader } from '@jetbrains-internal/tcci-react-ui-components';
import { useFormContext } from 'react-hook-form';
import warningIcon from '@jetbrains/icons/warning';
import Icon from '@jetbrains/ring-ui/components/icon';

import Button from '@jetbrains/ring-ui/components/button/button';

import AvailableAwsConnections from '../AwsConnection';
import Bucket from '../components/Bucket';
import BucketPrefix from '../components/BucketPrefix';
import { useAwsConnectionsContext } from '../../contexts/AwsConnectionsContext';
import AccessKeyId from '../S3Compatible/components/AccessKeyId';
import SecretAccessKey from '../S3Compatible/components/SecretAccessKey';
import { FormFields } from '../appConstants';
import { IFormInput } from '../../types';
import styles from '../styles.css';
import { AwsConnection } from '../AwsConnection/AvailableAwsConnectionsConstants';
import AwsConnectionDialog from '../AwsConnection/AwsConnectionDialog';

import TransferSpeedUp from './TransferSpeedUp/TransferSpeedUp';
import DefaultProviderChain from './components/DefaultProviderChain';
import IAMRole from './components/IAMRole';

export default function AwsS3() {
  const [show, setShow] = React.useState(false);
  const [connectionParameters, setConnectionParameters] = React.useState({});
  const { watch, getValues, setValue } = useFormContext<IFormInput>();
  const { withFake } = useAwsConnectionsContext();
  const currentConnectionKey = watch(FormFields.AWS_CONNECTION_ID)?.key;
  const defaultProviderChain =
    watch(FormFields.USE_DEFAULT_CREDENTIAL_PROVIDER_CHAIN) ?? false;

  const handleConvert = React.useCallback(() => {
    const [useDefaults, accessKey, secret, iamRole] = getValues([
      FormFields.USE_DEFAULT_CREDENTIAL_PROVIDER_CHAIN,
      FormFields.ACCESS_KEY_ID,
      FormFields.SECRET_ACCESS_KEY,
      FormFields.IAM_ROLE_ARN,
    ]);

    setConnectionParameters({
      useDefaultCredentialsProviderChain: useDefaults,
      awsAccessKeyId: accessKey,
      awsSecretAccessKey: secret,
      iamRole,
    });

    setShow(true);
  }, [getValues]);

  return (
    <>
      <section>
        <SectionHeader>{'AWS S3'}</SectionHeader>
        <AvailableAwsConnections />
        {withFake && currentConnectionKey === 'fake' && (
          <>
            <DefaultProviderChain />
            {!defaultProviderChain && (
              <>
                <AccessKeyId />
                <SecretAccessKey />
              </>
            )}
            <IAMRole />
            <div className={styles.convertWarningBox}>
              <Icon glyph={warningIcon} />
              <p className={styles.commentary}>
                {'We recommend you to'}{' '}
                <Button text onClick={handleConvert}>
                  {'Convert to AWS Connection'}
                </Button>{' '}
                {
                  'to follow the best practice. It will take less than 1 minute.'
                }
              </p>
            </div>
          </>
        )}
        <Bucket />
        <BucketPrefix />
      </section>
      <TransferSpeedUp />
      <AwsConnectionDialog
        active={show}
        mode={'convert'}
        awsConnectionIdProp={''}
        parametersPreset={connectionParameters}
        onClose={(newConnection: AwsConnection | undefined) => {
          if (newConnection) {
            setValue(FormFields.AWS_CONNECTION_ID, {
              key: newConnection,
              label: newConnection.displayName,
            });
          }
          setShow(false);
        }}
      />
    </>
  );
}
