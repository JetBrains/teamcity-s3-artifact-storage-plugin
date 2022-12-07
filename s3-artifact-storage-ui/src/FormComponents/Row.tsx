import {React} from '@jetbrains/teamcity-api';
import {ReactNode} from 'react';

import {row} from './styles.css';

interface RowProps {
  children: ReactNode;
}

export const Row: React.FunctionComponent<RowProps> = props => <div className={row}>{props.children}</div>;
