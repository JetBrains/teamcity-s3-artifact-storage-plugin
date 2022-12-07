import {React} from '@jetbrains/teamcity-api';
import {ReactNode} from 'react';

import {label as labelStyle, required as requiredStyle} from './styles.css';

interface LabelProps {
  required?: boolean;
  htmlFor?: string;
  children: ReactNode;
}

export const Label: React.FunctionComponent<LabelProps> = props => (
  <div className={labelStyle}>
    <label htmlFor={props.htmlFor}>
      <b>{props.children}</b>
      {props.required && <span className={requiredStyle}>{' *'}</span>}
    </label>
  </div>
);
