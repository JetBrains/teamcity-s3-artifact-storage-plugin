import { React } from '@jetbrains/teamcity-api';
import { SectionHeader } from '@teamcity-cloud-integrations/react-ui-components';

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

export default function AwsS3() {
  const { watch } = useFormContext<IFormInput>();
  const { withFake } = useAwsConnectionsContext();
  const currentConnectionKey = watch(FormFields.AWS_CONNECTION_ID)?.key;
  return (
    <>
      <section>
        <SectionHeader>{'AWS S3'}</SectionHeader>
        <AvailableAwsConnections />
        {withFake && currentConnectionKey === 'fake' && (
          <>
            <AccessKeyId />
            <SecretAccessKey />
          </>
        )}
        <Bucket />
        <BucketPrefix />
      </section>
      <TransferSpeedUp />
    </>
  );
}
