import {
  Option,
  SectionHeader,
} from '@teamcity-cloud-integrations/react-ui-components';

import { React } from '@jetbrains/teamcity-api';

import StorageType from './components/StorageType';
import StorageName from './components/StorageName';
import StorageId from './components/StorageId';

export default function StorageSection({
  onReset,
}: {
  onReset: (option: Option | null) => void | undefined;
}) {
  return (
    <section>
      <SectionHeader>{'Storage'}</SectionHeader>
      <StorageName />
      <StorageId />
      <StorageType onChange={onReset} />
    </section>
  );
}
