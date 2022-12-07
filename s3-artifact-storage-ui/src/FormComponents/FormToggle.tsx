import {React} from '@jetbrains/teamcity-api';
import {Controller, RegisterOptions} from 'react-hook-form';
import {Control} from 'react-hook-form/dist/types';
import Toggle, {ToggleAttrs} from '@jetbrains/ring-ui/components/toggle/toggle';

import {fixToggleText} from './styles.css';
import {FieldRow} from './FieldRow';

interface OwnProps extends ToggleAttrs {
  name: string,
  control: Control<any>,
  rules?: RegisterOptions,
  label?: string,
}

export default function FormToggle({name, control, rules, label, ...rest}: OwnProps) {
  return (
    <Controller
      name={name}
      control={control}
      rules={rules}
      render={({field}) => (
        <FieldRow>
          <Toggle
            {...field}
            {...rest}
            className={fixToggleText}
          >
            {label}
          </Toggle>
        </FieldRow>
      )}
    />
  );
}
