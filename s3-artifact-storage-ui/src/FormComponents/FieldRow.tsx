import {React} from '@jetbrains/teamcity-api';
import {ReactNode} from 'react';

import {fieldRow as fieldRowStyle} from './styles.css';

interface FieldRowProps {
  children: ReactNode;
}

export const FieldRow: React.FunctionComponent<FieldRowProps> =
  props => <div className={fieldRowStyle}>{props.children}</div>;
