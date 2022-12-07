import {React} from '@jetbrains/teamcity-api';
import {ReactNode} from 'react';

import {field as fieldStyle} from './styles.css';

interface FieldProps {
  children: ReactNode;
}

export const Field: React.FunctionComponent<FieldProps> =
  props => <div className={fieldStyle}>{props.children}</div>;
