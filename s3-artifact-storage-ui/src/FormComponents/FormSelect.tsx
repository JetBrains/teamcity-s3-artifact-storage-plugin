import {Controller} from 'react-hook-form';
import {React} from '@jetbrains/teamcity-api';
import Select from '@jetbrains/ring-ui/components/select/select';

import {FieldRow} from './FieldRow';

import {commentary, errorText, selectError} from './styles.css';

export default function FormSelect({name, control, selected, rules, details, id, ...selectProps}: any) {
  return (
    <Controller
      name={name}
      control={control}
      render={({field, fieldState}) => {
        // passing ref will cause elm.focus exception on element if {required: true}
        const {ref, ...rest} = field;
        const errorMessage = fieldState.error?.message;
        return (
          <>
            <FieldRow>
              <Select
                {...rest}
                {...selectProps}
                id={id || name}
                selected={field.value || selected}
                className={errorMessage && selectError}
              />
            </FieldRow>
            {errorMessage && (
              <FieldRow>
                <p className={errorText}>{errorMessage}</p>
              </FieldRow>
            )}
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
