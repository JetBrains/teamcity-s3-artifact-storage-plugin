import { React } from '@jetbrains/teamcity-api';
import { ReactNode, useContext } from 'react';

import { Config } from '../types';

const AppContext = React.createContext<Config>({
  storageTypes: '',
  storageNames: '',
  containersPath: '',
  distributionPath: '',
  publicKey: '',
  projectId: '',
  isNewStorage: false,
  cloudfrontFeatureOn: false,
  transferAccelerationOn: false,
  selectedStorageType: '',
  selectedStorageName: '',
  storageSettingsId: '',
  environmentNameValue: '',
  serviceEndpointValue: '',
  awsRegionName: '',
  showDefaultCredentialsChain: false,
  isDefaultCredentialsChain: false,
  credentialsTypeValue: '',
  accessKeyIdValue: '',
  secretAcessKeyValue: '',
  iamRoleArnValue: '',
  externalIdValue: '',
  bucketNameWasProvidedAsString: '',
  bucket: '',
  bucketPathPrefix: '',
  useCloudFront: false,
  cloudFrontUploadDistribution: '',
  cloudFrontDownloadDistribution: '',
  cloudFrontPublicKeyId: '',
  cloudFrontPrivateKey: '',
  usePresignUrlsForUpload: false,
  forceVirtualHostAddressing: false,
  enableAccelerateMode: false,
  multipartUploadThreshold: '',
  multipartUploadPartSize: '',
  chosenAwsConnectionId: '',
  availableAwsConnectionsControllerUrl: '',
  availableAwsConnectionsControllerResource: '',
});

const { Provider } = AppContext;

interface OwnProps {
  value: Config;
  children: ReactNode | ReactNode[];
}

function AppContextProvider(props: OwnProps) {
  return <Provider value={props.value}>{props.children}</Provider>;
}

const useAppContext = () => useContext(AppContext);

export { AppContextProvider, useAppContext };
