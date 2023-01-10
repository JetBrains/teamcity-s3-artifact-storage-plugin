import {React} from '@jetbrains/teamcity-api';
import {ReactNode} from 'react';

import styles from './styles.css';

interface RowProps {
  children: ReactNode;
}

export const Row: React.FunctionComponent<RowProps> = props => <div className={styles.row}>{props.children}</div>;
