import {React} from '@jetbrains/teamcity-api';
import {ReactNode} from 'react';

import styles from './styles.css';

interface FieldProps {
  children: ReactNode;
}

export const Field: React.FunctionComponent<FieldProps> =
  props => <div className={styles.field}>{props.children}</div>;
