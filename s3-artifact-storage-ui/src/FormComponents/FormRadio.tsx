import {React} from '@jetbrains/teamcity-api';
import {Controller, RegisterOptions} from 'react-hook-form';
import Radio from '@jetbrains/ring-ui/components/radio/radio';
import {Control} from 'react-hook-form/dist/types';

import {FieldRow} from './FieldRow';
import styles from './styles.css';

type OwnProps = {
  data: FormRadioItem[],
  name: string,
  control: Control<any>,
  rules: RegisterOptions,
}

export type FormRadioItem = {
  value: string,
    label: string,
    details?: string
}

export default function FormRadio({name, control, data, rules}: OwnProps) {
  return (
    <Controller
      name={name}
      control={control}
      rules={rules}
      render={({field}) => (
        <Radio {...field} >
          {data.map(({value, label, details},) => (
            <>
              <FieldRow>
                <Radio.Item value={value}>{label}</Radio.Item>
              </FieldRow>
              {details && <FieldRow><p className={styles.commentary}>{details}</p></FieldRow>}
            </>
          ))}
        </Radio>
      )}
    />
  );
}
