import { React } from '@jetbrains/teamcity-api';
import { SelectItem } from '@jetbrains/ring-ui/components/select/select';
import { useFormContext } from 'react-hook-form';

import {
  FormRow,
  FormSelect,
  Option,
} from '@teamcity-cloud-integrations/react-ui-components';

import { useMemo, useCallback } from 'react';

import useStorageOptions from '../../../hooks/useStorageOptions';
import { IFormInput } from '../../../types';
import { AWS_ENV_TYPE_ARRAY, FormFields } from '../../appConstants';

type StorageTypeConfig = {
  onChange: (option: Option | null) => void | undefined | null;
};

export const S3_COMPATIBLE = 'S3_compatible_storage';
export const AWS_S3 = 'S3_storage';

export default function StorageType({ onChange: callback }: StorageTypeConfig) {
  const storageOptions = useStorageOptions();
  const s3StorageTypes = useMemo(() => [AWS_S3, S3_COMPATIBLE], []);

  const { control, setValue, clearErrors } = useFormContext<IFormInput>();

  const innerOnChange = useCallback(
    (option: SelectItem<Option> | null) => {
      if (option && s3StorageTypes.includes(option.key)) {
        clearErrors();
        setValue(FormFields.STORAGE_TYPE, option, {
          shouldTouch: true,
          shouldDirty: true,
        });

        if (option.key === S3_COMPATIBLE) {
          setValue(FormFields.AWS_ENVIRONMENT_TYPE, AWS_ENV_TYPE_ARRAY[1]);
        } else {
          setValue(FormFields.AWS_ENVIRONMENT_TYPE, AWS_ENV_TYPE_ARRAY[0]);
        }
      } else if (callback) {
        callback(option);
      }
    },
    [callback, clearErrors, s3StorageTypes, setValue]
  );

  return (
    <FormRow label="Type" labelFor={`${FormFields.STORAGE_TYPE}_key`}>
      <FormSelect
        id={`${FormFields.STORAGE_TYPE}_key`}
        name={FormFields.STORAGE_TYPE}
        control={control}
        data={storageOptions}
        onChange={innerOnChange}
      />
    </FormRow>
  );
}
