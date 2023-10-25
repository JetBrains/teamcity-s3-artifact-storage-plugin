import { React } from '@jetbrains/teamcity-api';

import {
  errorMessage,
  Option,
} from '@jetbrains-internal/tcci-react-ui-components';

import { useFormContext } from 'react-hook-form';

import { ResponseErrors } from '@jetbrains-internal/tcci-react-ui-components/dist/types';

import { loadBucketList } from '../Utilities/fetchBucketNames';
import { IFormInput } from '../types';

import { useAppContext } from '../contexts/AppContext';

export default function useBucketOptions() {
  const config = useAppContext();
  const { getValues } = useFormContext<IFormInput>();
  const [bucketsListLoading, setBucketsListLoading] = React.useState(false);
  const [buckets, setBuckets] = React.useState<Option[]>([]);
  const [loadErrors, setLoadErrors] = React.useState<
    ResponseErrors | string | undefined
  >();

  const reloadBuckets = React.useCallback(async () => {
    if (bucketsListLoading) {
      return [];
    }

    setBucketsListLoading(true);
    setBuckets([]);
    setLoadErrors(undefined);
    try {
      const { bucketNames, errors } = await loadBucketList(config, getValues());
      if (bucketNames) {
        const bucketsData = bucketNames.reduce((acc, cur) => {
          acc.push({ label: cur, key: cur });
          return acc;
        }, [] as Option[]);
        setBuckets(bucketsData);
        return bucketsData;
      }
      if (errors) {
        setLoadErrors(errors);
      }
    } catch (e) {
      setLoadErrors(errorMessage(e));
    } finally {
      setBucketsListLoading(false);
    }

    return [];
  }, [bucketsListLoading, config, getValues]);

  return {
    bucketOptions: buckets,
    loading: bucketsListLoading,
    errors: loadErrors,
    reload: reloadBuckets,
  };
}
