import { useCallback, useMemo, useState } from 'react';

import {
  errorMessage,
  Option,
} from '@teamcity-cloud-integrations/react-ui-components';

import { ResponseErrors } from '@teamcity-cloud-integrations/react-ui-components/dist/types';

import { loadDistributionList } from '../Utilities/fetchDistributions';
import { loadPublicKeyList } from '../Utilities/fetchPublicKeys';
import { FormFields } from '../App/appConstants';
import { DistributionItem, IFormInput } from '../types';
import { useAppContext } from '../contexts/AppContext';

export default function useCfDistributions() {
  const config = useAppContext();
  const [isLoading, setLoading] = useState(false);
  const [responseErrors, setResponseErrors] = useState<
    ResponseErrors | undefined
  >(undefined);
  // this state conflicts with CloudFrontDistributionsContext's state
  // FIXME: refactor to use only one state
  const [publicKeyOptions, setPublicKeyOptions] = useState<Option[]>([]);
  const [cfDistributions, setCfDistributions] = useState<DistributionItem[]>(
    []
  );

  async function safeLoad<T>(func: () => Promise<T>): Promise<T | undefined> {
    setLoading(true);
    try {
      return await func();
    } catch (e) {
      setResponseErrors({ unexpected: { message: errorMessage(e) } });
      return undefined;
    } finally {
      setLoading(false);
    }
  }

  const reloadPublicKeys = useCallback(
    async (data: IFormInput) =>
      await safeLoad(async () => {
        setPublicKeyOptions([]);
        const { publicKeys, errors } = await loadPublicKeyList(config, data);

        if (publicKeys) {
          const selDD = data[FormFields.CLOUD_FRONT_DOWNLOAD_DISTRIBUTION];
          const selUD = data[FormFields.CLOUD_FRONT_UPLOAD_DISTRIBUTION];
          const publicKeysData = publicKeys
            // if Download Distribution is selected, filter keys that are compatible with it
            .filter((pk) =>
              selDD
                ? (selDD as DistributionItem).publicKeys?.find(
                    (it) => it === pk.id
                  ) !== undefined
                : true
            )
            // if Upload Distribution is selected, filter keys that are compatible with it
            .filter((pk) =>
              selUD
                ? (selUD as DistributionItem).publicKeys?.find(
                    (it) => it === pk.id
                  ) !== undefined
                : true
            )
            .reduce((acc, cur) => {
              acc.push({ label: cur.name, key: cur.id });
              return acc;
            }, [] as Option[]);

          setPublicKeyOptions(publicKeysData);
          return publicKeysData;
        }
        if (errors) {
          setResponseErrors(errors);
        }
        return [];
      }),
    [config]
  );

  const reloadDistributions = useCallback(
    async (data: IFormInput) =>
      await safeLoad(async () => {
        setCfDistributions([]);
        const { distributions, errors } = await loadDistributionList(
          config,
          data
        );

        if (distributions) {
          const distributionsData = distributions
            .filter((d) => d.enabled)
            .reduce<DistributionItem[]>((acc, cur) => {
              acc.push({
                label: cur.description!,
                key: cur.id,
                publicKeys: cur.publicKeys,
              });
              return acc;
            }, []);

          const allKeysFromDistributions = distributionsData.flatMap(
            (dd) => dd.publicKeys
          );
          const filteredPublicKeyList = publicKeyOptions.filter(
            (pk) => allKeysFromDistributions.indexOf(pk.key) > -1
          );

          setPublicKeyOptions(filteredPublicKeyList);
          setCfDistributions(distributionsData);
          return distributionsData;
        }
        if (errors) {
          setResponseErrors(errors);
        }
        return [];
      }),
    [config, publicKeyOptions]
  );

  return useMemo(
    () => ({
      isLoading,
      errors: responseErrors,
      cfDistributions,
      publicKeyOptions,
      reloadDistributions,
      reloadPublicKeys,
    }),
    [
      cfDistributions,
      isLoading,
      publicKeyOptions,
      reloadDistributions,
      reloadPublicKeys,
      responseErrors,
    ]
  );
}
