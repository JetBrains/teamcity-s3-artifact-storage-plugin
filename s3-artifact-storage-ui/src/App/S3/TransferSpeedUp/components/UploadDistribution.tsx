import { React } from '@jetbrains/teamcity-api';

import { LabelWithHelp } from '@jetbrains-internal/tcci-react-ui-components';

import { FormFields } from '../../../appConstants';
import { useCloudFrontDistributionsContext } from '../contexts/CloudFrontDistributionsContext';

import Distribution from './Distribution';

function UploadDistributionLabel() {
  return (
    <LabelWithHelp
      label={'Upload distribution'}
      helpText={'Distribution with write permission to S3 bucket'}
    />
  );
}
export default function UploadDistribution() {
  const { setUploadDistribution } = useCloudFrontDistributionsContext();
  return (
    <Distribution
      label={<UploadDistributionLabel />}
      fieldName={FormFields.CLOUD_FRONT_UPLOAD_DISTRIBUTION}
      onChange={setUploadDistribution}
    />
  );
}
