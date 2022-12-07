import Input from '@jetbrains/ring-ui/components/input/input';
import {Controller} from 'react-hook-form';
import {React} from '@jetbrains/teamcity-api';

import {FieldRow} from './FieldRow';

import {commentary} from './styles.css';

export default function FormInput({control, name, rules, id, ...inputProps}: any) {
  const {defaultValue, details} = inputProps;
  return (
    <Controller
      name={name}
      control={control}
      defaultValue={defaultValue}
      render={({field, fieldState}) => {
        const {ref, ...rest} = field;
        const errorMessage = fieldState.error?.message;
        return (
          <>
            <FieldRow>
              <Input
                {...inputProps}
                {...rest}
                id={id || name}
                inputRef={ref}
                error={errorMessage}
              />
            </FieldRow>
            {!errorMessage && details &&
            <FieldRow><p className={commentary}>{details}</p></FieldRow>
            }
          </>
        );
      }}
      rules={rules}
    />
  );
}
