import {React} from '@jetbrains/teamcity-api';
import {useFormContext} from 'react-hook-form';

import {useEffect, useRef, useState} from 'react';

import Button from '@jetbrains/ring-ui/components/button/button';

import {Size} from '@jetbrains/ring-ui/components/input/input';

import {HelpButton, FormToggle, FormRow, FormSelect, Option, MagicButton, SectionHeader, FieldRow, FieldColumn} from '@teamcity-cloud-integrations/react-ui-components';

import {loadPublicKeyList} from '../Utilities/fetchPublicKeys';
import {ResponseErrors} from '../Utilities/responseParser';
import {loadDistributionList} from '../Utilities/fetchDistributions';
import {createDistribution} from '../Utilities/createDistribution';
import useDistributionInfo from '../hooks/useDistributionInfo';
import {Config, IFormInput} from '../types';

import {FormFields} from './appConstants';

import styles from './styles.css';

export interface DistributionItem extends Option {
  publicKeys: string[] | null,
}

interface OwnProps extends Config {
  setErrors: (errors: (ResponseErrors | null)) => void
}

export default function CloudFrontSettings(props: OwnProps) {
  const {setErrors} = props;
  const distributionInfo = useDistributionInfo(props);
  const cloudFrontSettingsLink = 'https://www.jetbrains.com/help/teamcity/2022.10/?CloudFrontSettings';
  const {control, setValue, getValues} = useFormContext<IFormInput>();
  const [publicKeyListData, setPublicKeyListData] = useState<Option[]>(
    getValues(FormFields.CLOUD_FRONT_PUBLIC_KEY_ID)
      ? [getValues(FormFields.CLOUD_FRONT_PUBLIC_KEY_ID) as Option]
      : []);
  const [uploadDistributionData, setUploadDistributionData] = useState<DistributionItem[]>(
    getValues(FormFields.CLOUD_FRONT_UPLOAD_DISTRIBUTION)
      ? [getValues(FormFields.CLOUD_FRONT_UPLOAD_DISTRIBUTION) as DistributionItem]
      : []);
  const [downloadDistributionData, setDownloadDistributionData] = useState<DistributionItem[]>(
    getValues(FormFields.CLOUD_FRONT_DOWNLOAD_DISTRIBUTION)
      ? [getValues(FormFields.CLOUD_FRONT_DOWNLOAD_DISTRIBUTION) as DistributionItem]
      : []);

  const selectPublicKey = React.useCallback(
    (data: Option | null) => {
      setValue(FormFields.CLOUD_FRONT_PUBLIC_KEY_ID, data);
    },
    [setValue]
  );

  const selectUploadDistribution = React.useCallback(
    (data: DistributionItem | null) => {
      setValue(FormFields.CLOUD_FRONT_UPLOAD_DISTRIBUTION, data);
      const selectedPK = getValues(FormFields.CLOUD_FRONT_PUBLIC_KEY_ID);

      if (selectedPK?.key && !data?.publicKeys?.find(key => key === selectedPK.key)) {
        selectPublicKey(null);
      }
    },
    [setValue, getValues, selectPublicKey]
  );

  const selectDownloadDistribution = React.useCallback(
    (data: DistributionItem | null) => {
      setValue(FormFields.CLOUD_FRONT_DOWNLOAD_DISTRIBUTION, data);
      const selectedPK = getValues(FormFields.CLOUD_FRONT_PUBLIC_KEY_ID);

      if (selectedPK?.key && !data?.publicKeys?.find(key => key === selectedPK.key)) {
        selectPublicKey(null);
      }
    },
    [setValue, getValues, selectPublicKey]
  );

  useEffect(() => {
    if (!distributionInfo.loading) {
      selectUploadDistribution(distributionInfo.initialUploadDistribution);
      selectDownloadDistribution(distributionInfo.initialDownloadDistribution);
      selectPublicKey(distributionInfo.publicKey);
    } else {
      selectUploadDistribution(null);
      selectDownloadDistribution(null);
      selectPublicKey(null);
    }
  },
  [distributionInfo.initialDownloadDistribution, distributionInfo.initialUploadDistribution,
    distributionInfo.loading, distributionInfo.publicKey, selectDownloadDistribution, selectPublicKey,
    selectUploadDistribution]);

  const [useCloudFront, setUseCloudFront] = useState(getValues(FormFields.CLOUD_FRONT_TOGGLE));

  const cloudFrontToggleChanged = (event: any) => {
    setUseCloudFront(event.target.value);
  };

  const [privateKeyDetails, setPrivateKeyDetails] = useState('Please upload a private key');

  const hiddenInputEl = useRef<HTMLInputElement>(null);

  const upload = (e: any) => {
    e.preventDefault();
    hiddenInputEl.current?.click();
  };

  const openFile = (evt: any) => {
    const fileObj = evt.target.files[0];
    const reader = new FileReader();

    reader.onload = e => setValue(FormFields.CLOUD_FRONT_PRIVATE_KEY, e.target?.result as string);
    reader.onloadend = () => setPrivateKeyDetails(`Uploaded ${fileObj.name}`);
    reader.readAsText(fileObj);
  };

  const [publicKeyDataLoading, setPublicKeyDataLoading] = useState(false);
  const reloadPublicKeys = async () => {
    setPublicKeyDataLoading(true);
    setPublicKeyListData([]);
    const [useDefaultCredentialProviderChain, keyId, keySecret] = getValues(
      [FormFields.USE_DEFAULT_CREDENTIAL_PROVIDER_CHAIN,
        FormFields.ACCESS_KEY_ID,
        FormFields.SECRET_ACCESS_KEY]);
    const {publicKeys, errors} = await loadPublicKeyList(
      {
        appProps: props,
        allValues: getValues(),
        useDefaultCredentialProviderChain,
        keyId,
        keySecret
      }
    );

    if (publicKeys) {
      const [selDD, selUD] = getValues(
        [FormFields.CLOUD_FRONT_DOWNLOAD_DISTRIBUTION, FormFields.CLOUD_FRONT_UPLOAD_DISTRIBUTION]);

      const publicKeysData = publicKeys.
        // if Download Distribution is selected filter keys that are compatible with it
        filter(pk => (selDD
          ? (selDD as DistributionItem).publicKeys?.find(it => it === pk.id) !== undefined
          : true)).
        // if Upload Distribution is selected filter keys that are compatible with it
        filter(pk => (selUD
          ? (selUD as DistributionItem).publicKeys?.find(it => it === pk.id) !== undefined
          : true)).reduce((acc, cur) => {
          acc.push({label: cur.name, key: cur.id});
          return acc;
        }, [] as Option[]);

      setPublicKeyListData(publicKeysData);
    }
    setPublicKeyDataLoading(false);
    setErrors(errors);
  };

  const [distributionsLoading, setDistributionsLoading] = useState(false);
  const reloadCloudDistributions = async () => {
    setDistributionsLoading(true);
    setDownloadDistributionData([]);
    setUploadDistributionData([]);
    const [useDefaultCredentialProviderChain, keyId, keySecret] = getValues(
      [FormFields.USE_DEFAULT_CREDENTIAL_PROVIDER_CHAIN,
        FormFields.ACCESS_KEY_ID,
        FormFields.SECRET_ACCESS_KEY]);
    const {distributions, errors} = await loadDistributionList(
      {
        appProps: props,
        allValues: getValues(),
        useDefaultCredentialProviderChain,
        keyId,
        keySecret
      }
    );

    if (distributions) {
      const distributionsData = distributions.filter(d => d.enabled).reduce<DistributionItem[]>((acc, cur) => {
        acc.push({label: cur.description!, key: cur.id, publicKeys: cur.publicKeys});
        return acc;
      }, []);

      const allKeysFromDistributions = distributionsData.flatMap(dd => dd.publicKeys);
      const filteredPublicKeyList = publicKeyListData.filter(pk => allKeysFromDistributions.indexOf(pk.key) > -1);
      const currentlySelectedPublicKey = getValues(FormFields.CLOUD_FRONT_PUBLIC_KEY_ID);

      if (currentlySelectedPublicKey &&
          filteredPublicKeyList.findIndex(pk => currentlySelectedPublicKey.key === pk.key) < 0) {
        selectPublicKey(null);
      }

      setPublicKeyListData(filteredPublicKeyList);
      setDownloadDistributionData(distributionsData);
      setUploadDistributionData(distributionsData);
    }
    setDistributionsLoading(false);
    setErrors(errors);
  };

  const createDistributionMagic = async () => {
    const [useDefaultCredentialProviderChain, keyId, keySecret] = getValues(
      [FormFields.USE_DEFAULT_CREDENTIAL_PROVIDER_CHAIN,
        FormFields.ACCESS_KEY_ID,
        FormFields.SECRET_ACCESS_KEY]);
    const {response, errors} = await createDistribution(
      {
        appProps: props,
        allValues: getValues(),
        useDefaultCredentialProviderChain,
        keyId,
        keySecret
      });

    if (response) {
      selectDownloadDistribution(
        {
          label: response.downloadDistribution.description!,
          key: response.downloadDistribution.id,
          publicKeys: response.downloadDistribution.publicKeys
        });
      selectUploadDistribution({
        label: response.uploadDistribution.description!,
        key: response.uploadDistribution.id,
        publicKeys: response.uploadDistribution.publicKeys
      });
      selectPublicKey({label: response.publicKey.name, key: response.publicKey.id});
      setValue(FormFields.CLOUD_FRONT_PRIVATE_KEY, response.privateKey);
      setPrivateKeyDetails('Key has been generated automatically');
    }

    setErrors(errors);
  };

  const distributionSection = () => (
    <>
      <FormRow
        label="Distribution for uploads:"
        star
        labelFor={FormFields.CLOUD_FRONT_UPLOAD_DISTRIBUTION}
      >
        <FieldRow>
          <FieldColumn>
            <FormSelect
              name={FormFields.CLOUD_FRONT_UPLOAD_DISTRIBUTION}
              control={control}
              selected={uploadDistributionData[0]}
              rules={{required: 'Distribution is mandatory'}}
              data={uploadDistributionData}
              onChange={selectUploadDistribution}
              onBeforeOpen={reloadCloudDistributions}
              loading={distributionsLoading}
              label="-- Select distribution --"
              size={Size.L}
              disabled={distributionInfo.loading}
            />
          </FieldColumn>
          <FieldColumn>
            <MagicButton
              title="Create distribution"
              onClick={createDistributionMagic}
              disabled={distributionInfo.loading}
            />
          </FieldColumn>
        </FieldRow>
      </FormRow>
      <FormRow
        label="Distribution for downloads:"
        star
        labelFor={FormFields.CLOUD_FRONT_DOWNLOAD_DISTRIBUTION}
      >
        <FieldRow>
          <FieldColumn>
            <FormSelect
              name={FormFields.CLOUD_FRONT_DOWNLOAD_DISTRIBUTION}
              control={control}
              rules={{required: 'Distribution is mandatory'}}
              data={downloadDistributionData}
              onChange={selectDownloadDistribution}
              onBeforeOpen={reloadCloudDistributions}
              loading={distributionsLoading}
              label="-- Select distribution --"
              size={Size.L}
              disabled={distributionInfo.loading}
            />
          </FieldColumn>
          <FieldColumn>
            <MagicButton
              title="Create distribution"
              onClick={createDistributionMagic}
              disabled={distributionInfo.loading}
            />
          </FieldColumn>
        </FieldRow>
      </FormRow>
      <FormRow
        label="Public key:"
        star
        labelFor={FormFields.CLOUD_FRONT_PUBLIC_KEY_ID}
      >
        <FormSelect
          name={FormFields.CLOUD_FRONT_PUBLIC_KEY_ID}
          control={control}
          rules={{required: 'Public key is mandatory'}}
          data={publicKeyListData}
          filter
          onChange={selectPublicKey}
          onBeforeOpen={reloadPublicKeys}
          loading={publicKeyDataLoading}
          label="-- Select public key --"
          disabled={distributionInfo.loading}
        />
      </FormRow>

      <FormRow label="Private key:" star>
        <>
          <FieldRow>
            <Button
              disabled={distributionInfo.loading}
              onClick={upload}
            >{'Choose file'}</Button>
          </FieldRow>
          <FieldRow>
            <p className={styles.commentary}>{privateKeyDetails}</p>
          </FieldRow>
          <input
            type="file"
            className="hidden"
            multiple={false}
            accept=".pem"
            onChange={e => openFile(e)}
            ref={hiddenInputEl}
          />
        </>
      </FormRow>
    </>
  );

  return (
    <section>
      <SectionHeader>{'CloudFront Settings'}</SectionHeader>
      <FormRow label="Use CloudFront to transport artifacts:">
        <FieldRow>
          <FieldColumn>
            <FormToggle
              defaultChecked={useCloudFront || false}
              name={FormFields.CLOUD_FRONT_TOGGLE}
              control={control}
              rules={{onChange: cloudFrontToggleChanged}}
            />
          </FieldColumn>
          <FieldColumn>
            <HelpButton href={cloudFrontSettingsLink}/>
          </FieldColumn>
        </FieldRow>
      </FormRow>
      {useCloudFront && distributionSection()}
    </section>
  );
}
