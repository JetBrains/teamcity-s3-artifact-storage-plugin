import { React } from '@jetbrains/teamcity-api';
import { ReactNode, useContext, useEffect, useMemo, useState } from 'react';

import { Option } from '@jetbrains-internal/tcci-react-ui-components';

import useAwsConnections from '../hooks/useAwsConnections';
import { AwsConnection } from '../App/AwsConnection/AvailableAwsConnectionsConstants';

import { useAppContext } from './AppContext';

type AwsConnectionsContextType = {
  isLoading: boolean;
  connectionOptions: Option<AwsConnection>[] | undefined;
  error: string | undefined;
  withFake?: boolean;
  reloadConnectionOptions: () => void;
};
const AwsConnectionsContext = React.createContext<AwsConnectionsContextType>({
  connectionOptions: undefined,
  error: undefined,
  isLoading: true,
  reloadConnectionOptions: () => {},
});

const { Provider } = AwsConnectionsContext;

interface OwnProps {
  children: ReactNode;
}

function AwsConnectionsContextProvider({ children }: OwnProps) {
  const {
    chosenAwsConnectionId,
    accessKeyIdValue,
    secretAcessKeyValue,
    isDefaultCredentialsChain,
  } = useAppContext();
  const value = useAwsConnections();
  const connectionOptions = useMemo(
    () => value.connectionOptions || [],
    [value.connectionOptions]
  );
  const [withFake, setWithFake] = useState(false);
  // meaning that we have outdated configuration with secrets
  // or migrating from S3 Compatible
  useEffect(() => {
    if (
      !chosenAwsConnectionId &&
      ((accessKeyIdValue && secretAcessKeyValue) || isDefaultCredentialsChain)
    ) {
      // zero width space is used to trick select renderer to make dropdown proper height.
      // if " " character or "" character is used, select rendered incorrectly
      const zeroWidthSpace = '\u200B';
      // create a fake connection
      const fake = {
        key: 'fake',
        label: zeroWidthSpace,
      } as Option<AwsConnection>;
      connectionOptions.unshift(fake);
      setWithFake(true);
    }
  }, [
    accessKeyIdValue,
    chosenAwsConnectionId,
    connectionOptions,
    isDefaultCredentialsChain,
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
