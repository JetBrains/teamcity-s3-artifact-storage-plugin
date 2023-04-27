import { React } from '@jetbrains/teamcity-api';
import { ReactNode, useContext, useEffect, useState } from 'react';

import { Option } from '@teamcity-cloud-integrations/react-ui-components';

import useAwsConnections from '../hooks/useAwsConnections';
import { AwsConnection } from '../App/AwsConnection/AvailableAwsConnectionsConstants';

import { useAppContext } from './AppContext';

type AwsConnectionsContextType = {
  isLoading: boolean;
  connectionOptions: Option<AwsConnection>[] | undefined;
  error: string | undefined;
  withFake?: boolean;
};
const AwsConnectionsContext = React.createContext<AwsConnectionsContextType>({
  connectionOptions: undefined,
  error: undefined,
  isLoading: true,
});

const { Provider } = AwsConnectionsContext;

interface OwnProps {
  children: ReactNode;
}

function AwsConnectionsContextProvider({ children }: OwnProps) {
  const { chosenAwsConnectionId, accessKeyIdValue, secretAcessKeyValue } =
    useAppContext();
  const value = useAwsConnections();
  const connectionOptions = value.connectionOptions || [];
  const [withFake, setWithFake] = useState(false);
  // meaning that we have outdated configuration with secrets
  // or migrating from S3 Compatible
  useEffect(() => {
    if (!chosenAwsConnectionId && accessKeyIdValue && secretAcessKeyValue) {
      // create a fake connection
      const fake = {
        key: 'fake',
        label: 'Static access key',
      } as Option<AwsConnection>;
      connectionOptions.unshift(fake);
      setWithFake(true);
    }
  }, [
    accessKeyIdValue,
    chosenAwsConnectionId,
    connectionOptions,
    secretAcessKeyValue,
  ]);

  return (
    <Provider value={{ ...value, connectionOptions, withFake }}>
      {children}
    </Provider>
  );
}

const useAwsConnectionsContext = () => useContext(AwsConnectionsContext);

export { AwsConnectionsContextProvider, useAwsConnectionsContext };
