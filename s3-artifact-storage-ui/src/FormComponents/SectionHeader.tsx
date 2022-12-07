import {React} from '@jetbrains/teamcity-api';
import {memo, ReactNode} from 'react';

import {hint} from './styles.css';

interface SectionHeaderProps {
  children: ReactNode;
}

const SectionHeaderComponent: React.FunctionComponent<SectionHeaderProps> =
  props => <h2 className={hint}>{props.children}</h2>;

export const SectionHeader = memo(SectionHeaderComponent);
