import {React} from '@jetbrains/teamcity-api';
import {ReactNode} from 'react';

import {Row} from './Row';
import {Label} from './Label';
import {Field} from './Field';

interface FormRowProps {
  label: string,
  star?: boolean,
  labelFor?: string,
  children: ReactNode;
}

export const FormRow: React.FunctionComponent<FormRowProps> =
  ({label, children, star, labelFor}) => (
    <Row>
      <Label required={star} htmlFor={labelFor}>{label}</Label>
      <Field>{children}</Field>
    </Row>
  );
