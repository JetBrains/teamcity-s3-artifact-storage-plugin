import { React } from '@jetbrains/teamcity-api';
import { SectionHeader } from '@teamcity-cloud-integrations/react-ui-components';

import Bucket from '../components/Bucket';
import BucketPrefix from '../components/BucketPrefix';

import AccessKeyId from './components/AccessKeyId';
import Endpoint from './components/Endpoint';
import SecretAccessKey from './components/SecretAccessKey';

export default function S3Section() {
  return (
    <section>
      <SectionHeader>{'S3'}</SectionHeader>
      <AccessKeyId />
      <SecretAccessKey />
      <Endpoint />
      <Bucket />
      <BucketPrefix />
    </section>
  );
}
