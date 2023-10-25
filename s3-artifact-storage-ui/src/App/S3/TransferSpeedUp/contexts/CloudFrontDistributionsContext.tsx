import { React } from '@jetbrains/teamcity-api';
import { ReactNode, useCallback, useContext, useEffect, useState } from 'react';
import { Option } from '@jetbrains-internal/tcci-react-ui-components';
import { useFormContext } from 'react-hook-form';

import { FormFields } from '../../../appConstants';
import { useAppContext } from '../../../../contexts/AppContext';
import { DistributionItem, IFormInput } from '../../../../types';
import useCfDistributions from '../../../../hooks/useCfDistributions';

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
  setCfDistributions: React.Dispatch<React.SetStateAction<DistributionItem[]>>;
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
    setCfDistributions: () => {},
    setDownloadDistribution: () => {},
    setUploadDistribution: () => {},
    setPublicKey: () => {},
    setPrivateKey: () => {},
    setPrivateKeyDetails: () => {},
    isMagicHappening: false,
    setIsMagicHappening: () => {},
  });

const { Provider } = CloudFrontDistributionsContext;
interface OwnProps {
  children: ReactNode;
}

function isDistributionInState(
  cfDistributions: DistributionItem[],
  downloadDistribution: DistributionItem
) {
  return cfDistributions?.find(
    (distribution) => distribution.key === downloadDistribution?.key
  );
}

function CloudFrontDistributionsContextProvider({ children }: OwnProps) {
  const { setValue, watch, getValues } = useFormContext<IFormInput>();
  const config = useAppContext();
  const [customFormState, setCustomFormState] = React.useState<any>(
    getValues()
  );
  const { reloadDistributions, reloadPublicKeys } = useCfDistributions();
  // FIXME: this state is in conflict with state from useCfDistributions hook. It should be refactored
  const [internalCfDistributions, setInternalCfDistributions] = useState<
    DistributionItem[]
  >([]);
  const [isInitialized, setIsInitialized] = useState(false);
  const [state, setState] = useState<CfState>(() => ({
    downloadDistribution: getValues(
      FormFields.CLOUD_FRONT_DOWNLOAD_DISTRIBUTION
    ),
    uploadDistribution: getValues(FormFields.CLOUD_FRONT_UPLOAD_DISTRIBUTION),
    publicKey: getValues(FormFields.CLOUD_FRONT_PUBLIC_KEY_ID),
    privateKey: getValues(FormFields.CLOUD_FRONT_PRIVATE_KEY),
    privateKeyDetails: '',
  }));
  const [isMagicHappening, setIsMagicHappening] = useState(false);
  const awsConnection = watch(FormFields.AWS_CONNECTION_ID);
  const isConnectionSelected = !!awsConnection;

  const initialize = React.useCallback(() => {
    setIsInitialized(false);
    // priming using parameters from configuration
    const formData = getValues();
    Promise.all([reloadDistributions(formData), reloadPublicKeys(formData)])
      .then(([distributionsInfo, pkInfo]) => {
        distributionsInfo && setInternalCfDistributions(distributionsInfo);
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
          privateKeyDetails:
            config.cloudFrontPrivateKey.length > 0
              ? 'Private key was uploaded'
              : undefined,
        });
      })
      .finally(() => {
        setIsInitialized(true);
      });
  }, [
    config.cloudFrontDownloadDistribution,
    config.cloudFrontPrivateKey,
    config.cloudFrontPublicKeyId,
    config.cloudFrontUploadDistribution,
    getValues,
    reloadDistributions,
    reloadPublicKeys,
  ]);

  useEffect(() => {
    initialize();
  }, []); // fire once

  const fieldChanged = React.useCallback(
    (fieldName: string, currentValue: any) => {
      try {
        return (
          JSON.stringify(currentValue) !==
          JSON.stringify(customFormState[fieldName])
        );
      } catch (e) {
        return true;
      }
    },
    [customFormState]
  );

  useEffect(() => {
    const subscription = watch((data) => {
      if (
        isInitialized &&
        ((data[FormFields.AWS_CONNECTION_ID] &&
          fieldChanged(
            FormFields.AWS_CONNECTION_ID,
            data[FormFields.AWS_CONNECTION_ID]
          )) ||
          (data[FormFields.S3_BUCKET_NAME] &&
            fieldChanged(
              FormFields.S3_BUCKET_NAME,
              data[FormFields.S3_BUCKET_NAME]
            )))
      ) {
        initialize();
      }
      setCustomFormState(data);
    });

    return () => {
      subscription.unsubscribe();
    };
  }, [fieldChanged, initialize, isInitialized, watch]);

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
      !isDistributionInState(
        internalCfDistributions,
        state.downloadDistribution
      )
    ) {
      setDownloadDistribution(null);
    }

    if (
      state?.uploadDistribution &&
      !isDistributionInState(internalCfDistributions, state.uploadDistribution)
    ) {
      setUploadDistribution(null);
    }
  }, [
    internalCfDistributions,
    setDownloadDistribution,
    setUploadDistribution,
    state,
  ]);

  return (
    <Provider
      value={{
        downloadDistributions: internalCfDistributions,
        uploadDistributions: internalCfDistributions,
        setCfDistributions: setInternalCfDistributions,
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
