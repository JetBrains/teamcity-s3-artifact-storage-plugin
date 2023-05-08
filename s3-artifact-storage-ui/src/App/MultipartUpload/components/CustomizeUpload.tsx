import { React } from '@jetbrains/teamcity-api';
import { FormCheckbox } from '@teamcity-cloud-integrations/react-ui-components';
import { useFormContext } from 'react-hook-form';

import { FormFields } from '../../appConstants';

export default function CustomizeUpload() {
  const { control } = useFormContext();
  return (
    <FormCheckbox
      name={FormFields.CONNECTION_MULTIPART_CUSTOMIZE_FLAG}
      control={control}
      label="Customize threshold and part size"
    />
  );
}
