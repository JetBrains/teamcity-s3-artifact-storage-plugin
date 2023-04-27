import { React } from '@jetbrains/teamcity-api';

import { LabelWithHelp } from '@teamcity-cloud-integrations/react-ui-components';

import { FormFields } from '../../../appConstants';

import { useCloudFrontDistributionsContext } from '../contexts/CloudFrontDistributionsContext';

import Distribution from './Distribution';

function DownloadDistributionLabel() {
  return (
    <LabelWithHelp
      label={'Download distribution'}
      helpText={'Distribution with read-only permission to S3 bucket'}
    />
  );
}
export default function DownloadDistribution() {
  const { setDownloadDistribution } = useCloudFrontDistributionsContext();
  return (
    <Distribution
      label={<DownloadDistributionLabel />}
      fieldName={FormFields.CLOUD_FRONT_DOWNLOAD_DISTRIBUTION}
      onChange={setDownloadDistribution}
    />
  );
}
