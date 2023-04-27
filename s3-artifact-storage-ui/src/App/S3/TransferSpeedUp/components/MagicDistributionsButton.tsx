import {
  errorMessage,
  MagicButton,
  Option,
  useErrorService,
} from '@teamcity-cloud-integrations/react-ui-components';
import { React } from '@jetbrains/teamcity-api';
import { ResponseErrors } from '@teamcity-cloud-integrations/react-ui-components/dist/types';

import { useFormContext } from 'react-hook-form';

import { useCallback } from 'react';

import styles from '../../../styles.css';

import { DistributionItem, IFormInput } from '../../../../types';
import { createDistribution } from '../../../../Utilities/createDistribution';
import { useCloudFrontDistributionsContext } from '../contexts/CloudFrontDistributionsContext';
import useCfDistributions from '../../../../hooks/useCfDistributions';
import { errorIdToFieldName } from '../../../appConstants';
import { useAppContext } from '../../../../contexts/AppContext';

type CreateDistributionsResponse = {
  result: {
    downloadDistribution: DistributionItem;
    uploadDistribution: DistributionItem;
    publicKey: Option;
    privateKey: string;
  } | null;
  errors: ResponseErrors | null;
};

export default function MagicDistributionsButton() {
  const config = useAppContext();
  const { getValues, setError } = useFormContext<IFormInput>();
  const { isLoading } = useCfDistributions();
  const {
    setDownloadDistribution,
    setUploadDistribution,
    setPublicKey,
    setPrivateKey,
    setPrivateKeyDetails,
    setIsMagicHappening,
    isMagicHappening,
    disabled,
  } = useCloudFrontDistributionsContext();
  const { showErrorsOnForm, showErrorAlert } = useErrorService({
    setError,
    errorKeyToFieldNameConvertor: errorIdToFieldName,
  });

  const createDistributions = useCallback(
    async (data: IFormInput): Promise<CreateDistributionsResponse> => {
      const { response, errors } = await createDistribution(config, data);

      if (response) {
        const dld = {
          label: response.downloadDistribution.description!,
          key: response.downloadDistribution.id,
          publicKeys: response.downloadDistribution.publicKeys,
        };
        const uld = {
          label: response.uploadDistribution.description!,
          key: response.uploadDistribution.id,
          publicKeys: response.uploadDistribution.publicKeys,
        };
        const pubk = {
          label: response.publicKey.name,
          key: response.publicKey.id,
        };
        const privk = response.privateKey;
        const result = {
          downloadDistribution: dld,
          uploadDistribution: uld,
          publicKey: pubk,
          privateKey: privk,
        };
        return { result, errors: null };
      }

      if (errors) {
        return { result: null, errors };
      }

      return {
        result: null,
        errors: { unexpected: { message: 'Unexpected error' } },
      };
    },
    [config]
  );

  const createDistributionMagic = useCallback(async () => {
    setIsMagicHappening(true);
    try {
      const { result, errors } = await createDistributions(getValues());
      if (errors) {
        showErrorsOnForm(errors);
      } else if (result) {
        setDownloadDistribution(result.downloadDistribution);
        setUploadDistribution(result.uploadDistribution);
        setPublicKey(result.publicKey);
        setPrivateKey(result.privateKey);
        setPrivateKeyDetails('Key has been generated automatically');
      }
    } catch (e) {
      showErrorAlert(errorMessage(e));
    } finally {
      setIsMagicHappening(false);
    }
  }, [
    createDistributions,
    getValues,
    setDownloadDistribution,
    setIsMagicHappening,
    setPrivateKey,
    setPrivateKeyDetails,
    setPublicKey,
    setUploadDistribution,
    showErrorAlert,
    showErrorsOnForm,
  ]);

  return (
    <MagicButton
      className={styles.magicButtonShift}
      title="Create distribution"
      onClick={createDistributionMagic}
      disabled={isLoading || disabled}
      loading={isMagicHappening}
    />
  );
}
