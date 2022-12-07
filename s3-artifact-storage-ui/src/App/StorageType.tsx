import {React} from '@jetbrains/teamcity-api';
import {SelectItem} from '@jetbrains/ring-ui/components/select/select';
import {useFormContext} from 'react-hook-form';

import {FormRow} from '../FormComponents/FormRow';
import FormSelect from '../FormComponents/FormSelect';

import {IFormInput} from './App';
import {FormFields} from './appConstants';

type StorageTypeConfig = {
  data: StorageTypeSelectItem[],
  onChange: (option: StorageTypeSelectItem | null) => void | undefined | null,
}

export type StorageTypeSelectItem = {
  label: string,
  key: string
}

export default function StorageType({data, onChange: callback}: StorageTypeConfig) {
  const s3StorageType = 'S3_storage';

  const {control} = useFormContext<IFormInput>();

  const innerOnChange = (option: SelectItem<StorageTypeSelectItem> | null) => {
    if (option && option.key === s3StorageType) {
      // do nothing
    } else if (callback) {
      callback(option);
    }
  };

  return (
    <FormRow
      label="Storage type:"
      labelFor={`${FormFields.STORAGE_TYPE}_key`}
    >
      <FormSelect
        id={`${FormFields.STORAGE_TYPE}_key`}
        name={FormFields.STORAGE_TYPE}
        control={control}
        data={data}
        selected={data[0]}
        onChange={innerOnChange}
      />
    </FormRow>
  );
}
