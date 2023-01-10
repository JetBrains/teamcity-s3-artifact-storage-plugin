import {React} from '@jetbrains/teamcity-api';
import {memo, ReactNode} from 'react';

import styles from './styles.css';

interface SectionHeaderProps {
  children: ReactNode;
}

const SectionHeaderComponent: React.FunctionComponent<SectionHeaderProps> =
  props => <h2 className={styles.hint}>{props.children}</h2>;

export const SectionHeader = memo(SectionHeaderComponent);
