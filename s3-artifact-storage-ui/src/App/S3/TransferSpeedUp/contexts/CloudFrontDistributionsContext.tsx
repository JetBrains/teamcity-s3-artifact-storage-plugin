import { React } from '@jetbrains/teamcity-api';
import { ReactNode, useCallback, useContext, useEffect, useState } from 'react';
import { Option } from '@teamcity-cloud-integrations/react-ui-components';
import { useFormContext } from 'react-hook-form';

import { AWS_ENV_TYPE_ARRAY, FormFields } from '../../../appConstants';
import { useAppContext } from '../../../../contexts/AppContext';
import { Config, DistributionItem, IFormInput } from '../../../../types';
import useCfDistributions from '../../../../hooks/useCfDistributions';
import { AwsConnection } from '../../../AwsConnection/AvailableAwsConnectionsConstants';

type CfState = {
  downloadDistribution?: DistributionItem;
  uploadDistribution?: DistributionItem;
  publicKey?: Option;
  privateKey?: string;
  privateKeyDetails?: string;
};

type CloudFrontDistributionsContextType = {
  isInitialized: boolean;
  disabled: boolean;
  downloadDistributions: DistributionItem[];
  uploadDistributions: DistributionItem[];
  state?: CfState;
  setDownloadDistribution: (distribution: DistributionItem | null) => void;
  setUploadDistribution: (distribution: DistributionItem | null) => void;
  setPublicKey: (publicKey: Option | null) => void;
  setPrivateKey: (privateKey: string) => void;
  setPrivateKeyDetails: (details: string) => void;
  isMagicHappening: boolean;
  setIsMagicHappening: (isMagicHappening: boolean) => void;
};

const CloudFrontDistributionsContext =
  React.createContext<CloudFrontDistributionsContextType>({
    isInitialized: false,
    disabled: false,
    downloadDistributions: [],
    uploadDistributions: [],
    setDownloadDistribution: () => {},
    setUploadDistribution: () => {},
    setPublicKey: () => {},
    setPrivateKey: () => {},
    setPrivateKeyDetails: () => {},
    isMagicHappening: false,
    setIsMagicHappening: () => {},
  });

const { Provider } = CloudFrontDistributionsContext;

function configAsFormInput(
  config: Config,
  connectionOptions: Option<AwsConnection>[]
): IFormInput {
  const selectedConnection = connectionOptions.find(
    (connection) => connection.key.id === config.chosenAwsConnectionId
  );
  return {
    [FormFields.AWS_CONNECTION_ID]: selectedConnection,
    [FormFields.STORAGE_TYPE]: {
      key: config.selectedStorageType,
      label: config.selectedStorageType,
    },
    [FormFields.STORAGE_NAME]: config.selectedStorageName || 'New S3 Storage',
    [FormFields.STORAGE_ID]: config.storageSettingsId || 'newS3Storage',
    [FormFields.AWS_ENVIRONMENT_TYPE]: config.environmentNameValue
      ? AWS_ENV_TYPE_ARRAY[1]
      : AWS_ENV_TYPE_ARRAY[0],
    [FormFields.CUSTOM_AWS_ENDPOINT_URL]: config.serviceEndpointValue,
    [FormFields.CUSTOM_AWS_REGION]: config.awsRegionName,
    [FormFields.USE_DEFAULT_CREDENTIAL_PROVIDER_CHAIN]:
      config.isDefaultCredentialsChain,
    [FormFields.CREDENTIALS_TYPE]: config.credentialsTypeValue,
    [FormFields.ACCESS_KEY_ID]: config.accessKeyIdValue,
    [FormFields.SECRET_ACCESS_KEY]: config.secretAcessKeyValue,
    [FormFields.IAM_ROLE_ARN]: config.iamRoleArnValue,
    [FormFields.EXTERNAL_ID]: config.externalIdValue,
    [FormFields.S3_BUCKET_NAME]: config.bucket,
    [FormFields.S3_BUCHET_PATH_PREFIX]: config.bucketPathPrefix,
    [FormFields.CLOUD_FRONT_TOGGLE]: config.useCloudFront,
    [FormFields.CLOUD_FRONT_PRIVATE_KEY]: config.cloudFrontPrivateKey,
    [FormFields.CONNECTION_PRESIGNED_URL_TOGGLE]:
      config.usePresignUrlsForUpload,
    [FormFields.CONNECTION_FORCE_VHA_TOGGLE]: config.forceVirtualHostAddressing,
    [FormFields.CONNECTION_TRANSFER_ACCELERATION_TOGGLE]:
      config.enableAccelerateMode,
    [FormFields.CONNECTION_MULTIPART_THRESHOLD]:
      config.multipartUploadThreshold,
    [FormFields.CONNECTION_MULTIPART_CHUNKSIZE]: config.multipartUploadPartSize,
  };
}

interface OwnProps {
  children: ReactNode;
}

function CloudFrontDistributionsContextProvider({ children }: OwnProps) {
  const { setValue, watch, getValues } = useFormContext<IFormInput>();
  const config = useAppContext();
  const { cfDistributions, reloadDistributions, reloadPublicKeys } =
    useCfDistributions();

  const [isInitialized, setIsInitialized] = useState(false);
  const [state, setState] = useState<CfState | undefined>(undefined);
  const [isMagicHappening, setIsMagicHappening] = useState(false);
  const awsConnection = watch(FormFields.AWS_CONNECTION_ID);
  const isConnectionSelected = !!awsConnection;

  useEffect(() => {
    setIsInitialized(false);
    // priming using parameters from configuration
    const formData = getValues();
    Promise.all([reloadDistributions(formData), reloadPublicKeys(formData)])
      .then(([distributionsInfo, pkInfo]) => {
        const uld = distributionsInfo?.find(
          (it) => it.key === config.cloudFrontUploadDistribution
        );
        const dld = distributionsInfo?.find(
          (it) => it.key === config.cloudFrontDownloadDistribution
        );
        const pk = pkInfo?.find(
          (it) => it.key === config.cloudFrontPublicKeyId
        );
        setState({
          downloadDistribution: dld,
          uploadDistribution: uld,
          publicKey: pk,
          privateKey: config.cloudFrontPrivateKey,
        });
      })
      .finally(() => {
        setIsInitialized(true);
      });
  }, []); // fire once

  const setPrivateKey = useCallback((privateKey: string) => {
    setState((prevState) => ({
      ...prevState,
      privateKey,
    }));
  }, []);

  const setPublicKey = useCallback((publicKey: Option | null) => {
    setState((prevState) => ({
      ...prevState,
      publicKey: publicKey ?? undefined,
    }));
  }, []);

  const resetPKIfNecessary = useCallback(
    (distribution: DistributionItem | null) => {
      if (
        state?.publicKey?.key &&
        !distribution?.publicKeys?.some((key) => key === state.publicKey?.key)
      ) {
        setPublicKey(null);
      }
    },
    [setPublicKey, state?.publicKey?.key]
  );

  const setUploadDistribution = useCallback(
    (distribution: DistributionItem | null) => {
      setState((prevState) => ({
        ...prevState,
        uploadDistribution: distribution ?? undefined,
      }));

      resetPKIfNecessary(distribution);
    },
    [resetPKIfNecessary]
  );

  const setDownloadDistribution = useCallback(
    (distribution: DistributionItem | null) => {
      setState((prevState) => ({
        ...prevState,
        downloadDistribution: distribution ?? undefined,
      }));

      resetPKIfNecessary(distribution);
    },
    [resetPKIfNecessary]
  );

  const setPrivateKeyDetails = useCallback((details: string) => {
    setState((prevState) => ({
      ...prevState,
      privateKeyDetails: details,
    }));
  }, []);

  useEffect(() => {
    setValue(
      FormFields.CLOUD_FRONT_DOWNLOAD_DISTRIBUTION,
      state?.downloadDistribution ?? undefined,
      { shouldValidate: true, shouldTouch: true, shouldDirty: true }
    );
  }, [setValue, state?.downloadDistribution]);

  useEffect(() => {
    setValue(
      FormFields.CLOUD_FRONT_UPLOAD_DISTRIBUTION,
      state?.uploadDistribution ?? undefined,
      { shouldValidate: true, shouldTouch: true, shouldDirty: true }
    );
  }, [setValue, state?.uploadDistribution]);

  useEffect(() => {
    setValue(
      FormFields.CLOUD_FRONT_PUBLIC_KEY_ID,
      state?.publicKey ?? undefined,
      { shouldValidate: true, shouldTouch: true, shouldDirty: true }
    );
  }, [setValue, state?.publicKey]);

  useEffect(() => {
    setValue(
      FormFields.CLOUD_FRONT_PRIVATE_KEY,
      state?.privateKey ?? undefined,
      { shouldValidate: true, shouldTouch: true, shouldDirty: true }
    );
  }, [setValue, state?.privateKey]);

  useEffect(() => {
    if (
      state?.downloadDistribution &&
      !cfDistributions?.includes(state?.downloadDistribution)
    ) {
      setDownloadDistribution(null);
    }

    if (
      state?.uploadDistribution &&
      !cfDistributions?.includes(state?.uploadDistribution)
    ) {
      setUploadDistribution(null);
    }
  }, [cfDistributions, setDownloadDistribution, setUploadDistribution, state]);

  return (
    <Provider
      value={{
        downloadDistributions: cfDistributions,
        uploadDistributions: cfDistributions,
        disabled: !isConnectionSelected,
        isInitialized,
        state,
        setUploadDistribution,
        setDownloadDistribution,
        setPublicKey,
        setPrivateKey,
        setPrivateKeyDetails,
        isMagicHappening,
        setIsMagicHappening,
      }}
    >
      {children}
    </Provider>
  );
}

const useCloudFrontDistributionsContext = () =>
  useContext(CloudFrontDistributionsContext);

export {
  CloudFrontDistributionsContextProvider,
  useCloudFrontDistributionsContext,
};
