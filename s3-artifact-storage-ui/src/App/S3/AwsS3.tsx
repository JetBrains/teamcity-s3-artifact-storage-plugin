import { React } from '@jetbrains/teamcity-api';
import { SectionHeader } from '@jetbrains-internal/tcci-react-ui-components';

import { useFormContext } from 'react-hook-form';

import AvailableAwsConnections from '../AwsConnection';
import Bucket from '../components/Bucket';
import BucketPrefix from '../components/BucketPrefix';

import { useAwsConnectionsContext } from '../../contexts/AwsConnectionsContext';
import AccessKeyId from '../S3Compatible/components/AccessKeyId';
import SecretAccessKey from '../S3Compatible/components/SecretAccessKey';

import { FormFields } from '../appConstants';
import { IFormInput } from '../../types';

import TransferSpeedUp from './TransferSpeedUp/TransferSpeedUp';
import DefaultProviderChain from './components/DefaultProviderChain';
import IAMRole from './components/IAMRole';

export default function AwsS3() {
  const { watch } = useFormContext<IFormInput>();
  const { withFake } = useAwsConnectionsContext();
  const currentConnectionKey = watch(FormFields.AWS_CONNECTION_ID)?.key;
  const defaultProviderChain =
    watch(FormFields.USE_DEFAULT_CREDENTIAL_PROVIDER_CHAIN) ?? false;

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
          </>
        )}
        <Bucket />
        <BucketPrefix />
      </section>
      <TransferSpeedUp />
    </>
  );
}
