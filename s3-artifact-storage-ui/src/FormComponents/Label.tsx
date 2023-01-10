import {React} from '@jetbrains/teamcity-api';
import {ReactNode} from 'react';

import styles from './styles.css';

interface LabelProps {
  required?: boolean;
  htmlFor?: string;
  children: ReactNode;
}

export const Label: React.FunctionComponent<LabelProps> = props => (
  <div className={styles.label}>
    <label htmlFor={props.htmlFor}>
      <b>{props.children}</b>
      {props.required && <span className={styles.required}>{' *'}</span>}
    </label>
  </div>
);
