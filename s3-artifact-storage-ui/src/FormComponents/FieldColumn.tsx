import {React} from '@jetbrains/teamcity-api';
import {ReactNode} from 'react';

import {fieldColumn as fieldColumnStyle} from './styles.css';

interface FieldColumnProps {
  children: ReactNode;
}

export const FieldColumn: React.FunctionComponent<FieldColumnProps> = props => (
  <div
    className={fieldColumnStyle}
  >{props.children}</div>
);
