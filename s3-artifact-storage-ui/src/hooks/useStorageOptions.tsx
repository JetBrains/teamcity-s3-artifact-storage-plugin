import { Option } from '@teamcity-cloud-integrations/react-ui-components';

import { useAppContext } from '../contexts/AppContext';

export default function useStorageOptions() {
  const config = useAppContext();

  const storageTypes = config.storageTypes
    .split(/[\[\],]/)
    .map((it) => it.trim())
    .filter((it) => !!it);
  const storageNames = config.storageNames
    .split(/[\[\],]/)
    .map((it) => it.trim())
    .filter((it) => !!it);
  return storageTypes.reduce<Option[]>((acc, next, i) => {
    acc.push({ key: next, label: storageNames[i] });
    return acc;
  }, []);
}
