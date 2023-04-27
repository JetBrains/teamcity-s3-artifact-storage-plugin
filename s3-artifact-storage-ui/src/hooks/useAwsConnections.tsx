import { useCallback, useEffect, useState } from 'react';

import {
  errorMessage,
  Option,
} from '@teamcity-cloud-integrations/react-ui-components';

import { useAppContext } from '../contexts/AppContext';
import { post } from '../Utilities/fetchHelper';
import { AwsConnection } from '../App/AwsConnection/AvailableAwsConnectionsConstants';

export default function useAwsConnections() {
  const config = useAppContext();
  const [error, setError] = useState<string | undefined>();
  const [connectionOptions, setConnectionOptions] = useState<
    Option<AwsConnection>[] | undefined
  >();
  const [isLoading, setIsLoading] = useState(true);

  const fetchAwsConnections = useCallback(async () => {
    const parameters = {
      projectId: config.projectId,
      resource: config.availableAwsConnectionsControllerResource,
    };
    const queryComponents = new URLSearchParams(parameters).toString();
    const response = await post(
      `${config.availableAwsConnectionsControllerUrl}?${queryComponents}`
    );
    const responseData: Array<string[]> = JSON.parse(response);

    return responseData.reduce((processedValue, dataValue) => {
      processedValue.push({
        label: dataValue[1],
        key: {
          displayName: dataValue[1],
          id: dataValue[0],
          usingSessionCreds: dataValue[2] === 'true',
        } as AwsConnection,
      });
      return processedValue;
    }, [] as Option<AwsConnection>[]);
  }, [config]);

  useEffect(() => {
    setIsLoading(true);
    fetchAwsConnections()
      .then((awsConnections) => setConnectionOptions(awsConnections))
      .catch((err: unknown) => setError(errorMessage(err)))
      .finally(() => setIsLoading(false));
  }, [fetchAwsConnections]);

  return { connectionOptions, error, isLoading };
}
