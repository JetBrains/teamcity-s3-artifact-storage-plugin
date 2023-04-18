import {React} from '@jetbrains/teamcity-api';
import {useFormContext} from 'react-hook-form';
import {FieldColumn, FieldRow, FormCheckbox, FormInput, FormRow, HelpButton, SectionHeader} from '@teamcity-cloud-integrations/react-ui-components';

import {Config, IFormInput} from '../types';

import {FormFields} from './appConstants';
import styles from './styles.css';

type OwnProps = Config

export default function ConnectionSettings({...config}: OwnProps) {
  const multipartUploadUrl = 'https://www.jetbrains.com/help/teamcity/2022.10/?Configuring+Artifacts+Storage#multipartUpload';
  const {control, getValues} = useFormContext<IFormInput>();

  return (
    <section>
      <SectionHeader>{'Connection Settings'}</SectionHeader>
      <FormCheckbox
        defaultChecked={getValues(FormFields.CONNECTION_PRESIGNED_URL_TOGGLE) || false}
        name={FormFields.CONNECTION_PRESIGNED_URL_TOGGLE}
        control={control}
        label="Use Pre-Signed URLs for upload"
      />
      <FormCheckbox
        defaultChecked={getValues(FormFields.CONNECTION_FORCE_VHA_TOGGLE) || false}
        name={FormFields.CONNECTION_FORCE_VHA_TOGGLE}
        control={control}
        label="Force Virtual Host Addressing"
      />
      {config.transferAccelerationOn && (
        <FormCheckbox
          label="Enable Transfer Acceleration"
          defaultChecked={getValues(FormFields.CONNECTION_TRANSFER_ACCELERATION_TOGGLE) || false}
          name={FormFields.CONNECTION_TRANSFER_ACCELERATION_TOGGLE}
          control={control}
          details={(<>{'Transfer Acceleration only works when '}<b>{'Force Virtual Host Addressing'}</b>{' is enabled'}</>)}
        />
      )}
      <FormRow
        label="Multipart upload threshold"
        labelFor={FormFields.CONNECTION_MULTIPART_THRESHOLD}
      >
        <FieldRow>
          <FieldColumn>
            <FormInput
              name={FormFields.CONNECTION_MULTIPART_THRESHOLD}
              control={control}
              details="Initiates multipart upload for files larger than the specified value. Minimum value is 5MB. Allowed suffixes: KB, MB, GB, TB . Leave empty to use the default value."
            />
          </FieldColumn>
          <FieldColumn>
            <HelpButton className={styles.helpButtonShift} href={multipartUploadUrl}/>
          </FieldColumn>
        </FieldRow>
      </FormRow>
      <FormRow
        label="Multipart upload part size"
        labelFor={FormFields.CONNECTION_MULTIPART_CHUNKSIZE}
      >
        <FieldRow>
          <FieldColumn>
            <FormInput
              name={FormFields.CONNECTION_MULTIPART_CHUNKSIZE}
              control={control}
              details="Specify the maximum allowed part size. Minimum value is 5MB. Allowed suffixes: KB, MB, GB, TB . Leave empty to use the default value."
            />
          </FieldColumn>
          <FieldColumn>
            <HelpButton className={styles.helpButtonShift} href={multipartUploadUrl}/>
          </FieldColumn>
        </FieldRow>
      </FormRow>
    </section>
  );
}
