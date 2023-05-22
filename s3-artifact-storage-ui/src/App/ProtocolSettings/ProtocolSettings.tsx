import { React } from '@jetbrains/teamcity-api';
import {
  FormCheckbox,
  SectionHeader,
} from '@jetbrains-internal/tcci-react-ui-components';

import { useFormContext } from 'react-hook-form';

import { useEffect } from 'react';

import { FormFields } from '../appConstants';
import { IFormInput } from '../../types';

export default function ProtocolSettings() {
  const { control, setValue, getValues, watch } = useFormContext<IFormInput>();
  const s3TransferAcceleration = watch(
    FormFields.CONNECTION_TRANSFER_ACCELERATION_TOGGLE
  );

  useEffect(() => {
    if (s3TransferAcceleration) {
      setValue(FormFields.CONNECTION_FORCE_VHA_TOGGLE, true);
    }
  }, [s3TransferAcceleration, setValue]);

  return (
    <section>
      <SectionHeader>{'Protocol settings'}</SectionHeader>
      <FormCheckbox
        defaultChecked={
          getValues(FormFields.CONNECTION_FORCE_VHA_TOGGLE) || false
        }
        name={FormFields.CONNECTION_FORCE_VHA_TOGGLE}
        control={control}
        label="Force virtual host addressing"
        disabled={s3TransferAcceleration ?? false}
      />
    </section>
  );
}
