import { React } from '@jetbrains/teamcity-api';
import { errorMessage } from '@jetbrains-internal/tcci-react-ui-components';
import { useFormContext } from 'react-hook-form';
import { ResponseErrors } from '@jetbrains-internal/tcci-react-ui-components/dist/types';

import { fetchS3TransferAccelerationAvailability } from '../Utilities/fetchS3TransferAccelerationAvailability';
import { useAppContext } from '../contexts/AppContext';

import { FormFields } from '../App/appConstants';

import useCanLoadBucketInfoData from './useCanLoadBucketInfoData';

export default function useTransferAccelerationAvailable() {
  const config = useAppContext();
  const { getValues } = useFormContext();
  const canLoadBucketInfo = useCanLoadBucketInfoData();
  const isDisabled = !canLoadBucketInfo;
  const [responseErrors, setResponseErrors] = React.useState<
    ResponseErrors | string | undefined
  >();
  const [isLoading, setIsLoading] = React.useState(false);
  const [isAvailable, setIsAvailable] = React.useState<boolean>(false);

  const validateS3TransferAccelerationAvailability =
    React.useCallback(async () => {
      if (isLoading) {
        return;
      }
      setResponseErrors(undefined);
      setIsAvailable(false);
      if (isDisabled) {
        return;
      }
      setIsLoading(true);
      try {
        const data = getValues();
        // safety measure to prevent errors when switching between buckets
        data[FormFields.CONNECTION_TRANSFER_ACCELERATION_TOGGLE] = false;
        const { isAvailable: result, errors } =
          await fetchS3TransferAccelerationAvailability(config, data);
        if (errors) {
          setResponseErrors(errors);
        } else {
          setIsAvailable(result ?? false);
        }
      } catch (e) {
        setResponseErrors(errorMessage(e));
      } finally {
        setIsLoading(false);
      }
    }, [config, getValues, isDisabled, isLoading]);

  return {
    available: isAvailable,
    isLoading,
    errors: responseErrors,
    reload: validateS3TransferAccelerationAvailability,
  };
}
