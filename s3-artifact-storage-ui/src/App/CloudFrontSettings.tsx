import {React} from '@jetbrains/teamcity-api';
import {useFormContext} from 'react-hook-form';

import {useRef, useState} from 'react';

import Button from '@jetbrains/ring-ui/components/button/button';

import HelpButton from '../FormComponents/HelpButton';
import FormToggle from '../FormComponents/FormToggle';
import {FormRow} from '../FormComponents/FormRow';
import FormSelect from '../FormComponents/FormSelect';
import {loadPublicKeyList} from '../Utilities/fetchPublicKeys';
import {ResponseErrors} from '../Utilities/responseParser';
import {loadDistributionList} from '../Utilities/fetchDistributions';
import MagicButton from '../FormComponents/MagicButton';
import {createDistribution} from '../Utilities/createDistribution';
import {SectionHeader} from '../FormComponents/SectionHeader';
import {FieldRow} from '../FormComponents/FieldRow';
import {FieldColumn} from '../FormComponents/FieldColumn';
import {commentary} from '../FormComponents/styles.css';

import {FormFields} from './appConstants';

import {Config, IFormInput} from './App';

export type PublicKeyItem = {
  label: string,
  key: string
}

export type DistributionItem = {
  label: string,
  key: string,
  publicKeys: string[] | null,
}

interface OwnProps extends Config {
    setErrors: (errors: (ResponseErrors | null)) => void
}

export default function CloudFrontSettings({setErrors, ...config}: OwnProps) {
  const cloudFrontSettingsLink = 'https://www.jetbrains.com/help/teamcity/2022.10/?CloudFrontSettings';
  const {control, setValue, getValues} = useFormContext<IFormInput>();
  const [publicKeyListData, setPublicKeyListData] = useState<PublicKeyItem[]>(
    getValues(FormFields.CLOUD_FRONT_PUBLIC_KEY_ID)
      ? [getValues(FormFields.CLOUD_FRONT_PUBLIC_KEY_ID) as PublicKeyItem]
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
    (data: PublicKeyItem | null) => {
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
        appProps: config,
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
        }, [] as PublicKeyItem[]);

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
        appProps: config,
        allValues: getValues(),
        useDefaultCredentialProviderChain,
        keyId,
        keySecret
      }
    );

    if (distributions) {
      const distributionsData = distributions.filter(d => d.enabled).reduce((acc, cur) => {
        acc.push({label: cur.description!, key: cur.id, publicKeys: cur.publicKeys});
        return acc;
      }, [] as DistributionItem[]);

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
        appProps: config,
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
      {useCloudFront && (
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
                />
              </FieldColumn>
              <FieldColumn>
                <MagicButton
                  title="Create distribution"
                  onClick={createDistributionMagic}
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
                />
              </FieldColumn>
              <FieldColumn>
                <MagicButton
                  title="Create distribution"
                  onClick={createDistributionMagic}
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
            />
          </FormRow>

          <FormRow label="Private key:" star>
            <>
              <FieldRow>
                <Button onClick={upload}>{'Choose file'}</Button>
              </FieldRow>
              <FieldRow>
                <p className={commentary}>{privateKeyDetails}</p>
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
      )}
    </section>
  );
}
