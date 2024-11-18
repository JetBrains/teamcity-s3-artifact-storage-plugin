import { React } from '@jetbrains/teamcity-api';
import {
  Option,
  SectionHeader,
} from '@jetbrains-internal/tcci-react-ui-components';
import { useFormContext, UseFormReturn } from 'react-hook-form';

import {
  AvailableAwsConnectionsWithButtons,
  AwsConnectionCredentialsType,
  AwsConnectionData,
  AwsConnectionsConversionFeature,
} from '@jetbrains-internal/aws-connection-components';

import Bucket from '../components/Bucket';
import BucketPrefix from '../components/BucketPrefix';
import AccessKeyId from '../S3Compatible/components/AccessKeyId';
import SecretAccessKey from '../S3Compatible/components/SecretAccessKey';
import { FormFields } from '../appConstants';
import { Config, IFormInput } from '../../types';

import { useAppContext } from '../../contexts/AppContext';

import { PASSWORD_STUB } from '../../hooks/useS3Form';

import { encodeSecret } from '../../Utilities/parametersUtils';

import styles from '../styles.css';

import TransferSpeedUp from './TransferSpeedUp/TransferSpeedUp';
import DefaultProviderChain from './components/DefaultProviderChain';
import IAMRole from './components/IAMRole';

export default function AwsS3() {
  const config = useAppContext();
  const methods = useFormContext<IFormInput>();
  const { watch, setValue } = useFormContext<IFormInput>();
  const currentConnectionKey = watch(FormFields.AWS_CONNECTION_ID)?.key;
  const defaultProviderChain =
    watch(FormFields.USE_DEFAULT_CREDENTIAL_PROVIDER_CHAIN) ?? false;
  const connectionsFilter = config.showDefaultCredentialsChain
    ? undefined
    : (type: string) => type !== AwsConnectionCredentialsType.DEFAULT_PROVIDER;
  const shouldConvert =
    (config.secretAcessKeyValue || config.accessKeyIdValue) &&
    !currentConnectionKey;

  const awsConnectionSelector = React.useMemo(
    () => (
      <AvailableAwsConnectionsWithButtons
        ctx={methods}
        connectionsData={toConnectionsData(
          currentConnectionKey,
          config,
          methods,
          (conn: Option) => setValue(FormFields.AWS_CONNECTION_ID, conn),
          defaultProviderChain,
          connectionsFilter
        )}
        formFieldName={FormFields.AWS_CONNECTION_ID}
        awsConnectionsStyle={styles.awsConnections}
      />
    ),
    [currentConnectionKey]
  );

  return (
    <>
      <section>
        <SectionHeader>{'AWS S3'}</SectionHeader>
        {awsConnectionSelector}
        {shouldConvert && (
          <>
            <DefaultProviderChain />
            {!defaultProviderChain && (
              <>
                <AccessKeyId />
                <SecretAccessKey />
              </>
            )}
            <IAMRole />
            <AwsConnectionsConversionFeature
              data={toConnectionsData(
                '',
                config,
                methods,
                (conn: Option) => {
                  setValue(FormFields.AWS_CONNECTION_ID, {
                    key: conn.key,
                    label: conn.label,
                  });
                  setValue(FormFields.ACCESS_KEY_ID, '');
                  setValue(FormFields.SECRET_ACCESS_KEY, '');
                },
                defaultProviderChain,
                connectionsFilter
              )}
            />
          </>
        )}
        <Bucket />
        <BucketPrefix />
      </section>
      <TransferSpeedUp />
    </>
  );
}

function toConnectionsData(
  connId: string | undefined,
  config: Config,
  methods: UseFormReturn,
  onSuccess: (connection: Option) => void,
  dpc: boolean,
  connectionsFilter: ((type: string) => boolean) | undefined
): AwsConnectionData {
  const { getValues, watch } = methods;
  const secretInput = watch(FormFields.SECRET_ACCESS_KEY);

  let secret = '';

  if (secretInput) {
    secret =
      secretInput === PASSWORD_STUB
        ? config.secretAcessKeyValue
        : encodeSecret(secretInput, config.publicKey);
  }

  return {
    awsConnectionId: connId,
    key: getValues(FormFields.ACCESS_KEY_ID),
    secret,
    allRegionKeys: config.regionCodes,
    allRegionValues: config.regionDescriptions,
    projectId: config.projectId,
    publicKey: config.publicKey,
    onSuccess,
    defaultProviderChain: config.showDefaultCredentialsChain,
    credentialsType: dpc
      ? AwsConnectionCredentialsType.DEFAULT_PROVIDER
      : AwsConnectionCredentialsType.ACCESS_KEYS,
    region: '',
    awsConnectionsUrl: config.postConnectionUrl,
    awsAvailableConnectionsControllerUrl:
      config.availableAwsConnectionsControllerUrl,
    awsAvailableConnectionsResource:
      config.availableAwsConnectionsControllerResource,
    testConnectionsUrl: config.testConnectionUrl,
    awsConnectionTypesFilter: connectionsFilter,
  } as AwsConnectionData;
}
