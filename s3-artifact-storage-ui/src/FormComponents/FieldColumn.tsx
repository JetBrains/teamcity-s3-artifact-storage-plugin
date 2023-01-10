import {React} from '@jetbrains/teamcity-api';
import {ReactNode} from 'react';

import styles from './styles.css';

interface FieldColumnProps {
  children: ReactNode;
}

export const FieldColumn: React.FunctionComponent<FieldColumnProps> = props => (
  <div
    className={styles.fieldColumn}
  >{props.children}</div>
);
