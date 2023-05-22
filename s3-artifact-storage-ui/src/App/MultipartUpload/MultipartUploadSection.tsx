import { React } from '@jetbrains/teamcity-api';
import { SectionHeader } from '@jetbrains-internal/tcci-react-ui-components';

import { useFormContext } from 'react-hook-form';

import { FormFields } from '../appConstants';

import Threshold from './components/Threshold';
import PartSize from './components/PartSize';
import CustomizeUpload from './components/CustomizeUpload';

export default function MultipartUploadSection() {
  const { watch } = useFormContext();
  const customizeUpload = watch(FormFields.CONNECTION_MULTIPART_CUSTOMIZE_FLAG);

  return (
    <section>
      <SectionHeader>{'Multipart upload settings'}</SectionHeader>
      <CustomizeUpload />
      {customizeUpload && (
        <>
          <Threshold />
          <PartSize />
        </>
      )}
    </section>
  );
}
