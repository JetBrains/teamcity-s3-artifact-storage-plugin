import {React} from '@jetbrains/teamcity-api';
import {ReactNode} from 'react';

import styles from './styles.css';

interface FieldRowProps {
  children: ReactNode;
}

export const FieldRow: React.FunctionComponent<FieldRowProps> =
  props => <div className={styles.fieldRow}>{props.children}</div>;
