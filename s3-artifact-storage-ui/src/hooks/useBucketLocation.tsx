import { React } from '@jetbrains/teamcity-api';
import {
  errorMessage,
  Option,
} from '@jetbrains-internal/tcci-react-ui-components';
import { useFormContext } from 'react-hook-form';

import { ResponseErrors } from '@jetbrains-internal/tcci-react-ui-components/dist/types';

import { fetchBucketLocation } from '../Utilities/fetchBucketLocation';
import { useAppContext } from '../contexts/AppContext';

import { FormFields } from '../App/appConstants';

import useCanLoadBucketInfoData from './useCanLoadBucketInfoData';

export default function useBucketLocation(bucketOptions: Option[]) {
  const config = useAppContext();
  const { getValues } = useFormContext();
  const canLoadBucketInfo = useCanLoadBucketInfoData();
  const isDisabled = !canLoadBucketInfo;
  const [responseErrors, setResponseErrors] = React.useState<
    ResponseErrors | string | undefined
  >();
  const [isLoading, setIsLoading] = React.useState(false);
  const [bucketLocation, setBucketLocation] = React.useState<
    string | undefined
  >();

  const loadBucketLocation = React.useCallback(async () => {
    if (isLoading) {
      return;
    }
    setResponseErrors(undefined);
    setBucketLocation(undefined);
    if (isDisabled || bucketOptions.length < 1) {
      return;
    }
    setIsLoading(true);
    try {
      const data = getValues();
      // safety measure to prevent errors when switching between buckets
      data[FormFields.CONNECTION_TRANSFER_ACCELERATION_TOGGLE] = false;
      const { location, errors } = await fetchBucketLocation(config, data);
      if (errors) {
        setResponseErrors(errors);
      }
      if (location) {
        setBucketLocation(location);
      }
    } catch (error) {
      setResponseErrors(errorMessage(error));
    } finally {
      setIsLoading(false);
    }
  }, [bucketOptions.length, config, getValues, isDisabled, isLoading]);

  return {
    bucketLocation,
    isLoading,
    errors: responseErrors,
    reload: loadBucketLocation,
  };
}
