import {
  FormInput,
  FormRow,
} from '@teamcity-cloud-integrations/react-ui-components';
import { React } from '@jetbrains/teamcity-api';
import { useFormContext } from 'react-hook-form';
import DOMPurify from 'dompurify';

import { FormFields } from '../../appConstants';

function diffStrings(original: string, cleaned: string) {
  const unique = new Set(cleaned);
  let diff = '';
  for (let i = 0; i < original.length; i++) {
    if (!unique.has(original[i])) {
      diff += original[i];
    }
  }
  return diff;
}
export default function StorageName() {
  const { control } = useFormContext();
  const sanitizeValidation = (value: string) => {
    const cleanValue = DOMPurify.sanitize(value);
    return cleanValue === value
      ? true
      : `Invalid input: ${diffStrings(value, cleanValue) || value}`;
    // if diff is empty and cleanValue is not empty, then the original value is some sort of
    // acceptable tag that wasn't closed properly; we show it as an error.
  };

  return (
    <FormRow label="Name" optional labelFor={FormFields.STORAGE_NAME}>
      <FormInput
        control={control}
        name={FormFields.STORAGE_NAME}
        rules={{ validate: sanitizeValidation }}
      />
    </FormRow>
  );
}
