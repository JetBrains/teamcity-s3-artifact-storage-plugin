import {
  errorMessage,
  FieldRow,
  FormRow,
  FormSelect,
  LabelWithHelp,
  useErrorService,
  useReadOnlyContext,
} from '@jetbrains-internal/tcci-react-ui-components';
import { React } from '@jetbrains/teamcity-api';

import { useFormContext } from 'react-hook-form';

import Button from '@jetbrains/ring-ui/components/button/button';

import { ChangeEvent, useCallback, useRef, useState } from 'react';

import { IFormInput } from '../../../../types';
import { errorIdToFieldName, FormFields } from '../../../appConstants';
import useCfDistributions from '../../../../hooks/useCfDistributions';
import { useCloudFrontDistributionsContext } from '../contexts/CloudFrontDistributionsContext';
import styles from '../../../styles.css';
import { fetchCfKeysValidationResult } from '../../../../Utilities/fetchCfKeysValidationResult';
import { useAppContext } from '../../../../contexts/AppContext';

export default function TrustedKeyGroup() {
  const config = useAppContext();
  const isReadOnly = useReadOnlyContext();
  const { control, getValues, setError, clearErrors } =
    useFormContext<IFormInput>();
  const { showErrorsOnForm, showErrorAlert } = useErrorService({
    setError,
    errorKeyToFieldNameConvertor: errorIdToFieldName,
  });
  const { isLoading, errors, reloadPublicKeys, publicKeyOptions } =
    useCfDistributions();
  const {
    setPublicKey,
    setPrivateKey,
    setPrivateKeyDetails,
    state,
    isMagicHappening,
    disabled,
  } = useCloudFrontDistributionsContext();
  const { privateKey, privateKeyDetails } = state || {};
  const reloadCloudPublicKeys = useCallback(
    () => reloadPublicKeys(getValues()),
    [getValues, reloadPublicKeys]
  );

  if (errors) {
    showErrorsOnForm(errors);
  }

  const hiddenInputEl = useRef<HTMLInputElement>(null);
  const upload = useCallback((e: unknown) => {
    if (e instanceof Event) {
      e.preventDefault();
    }
    hiddenInputEl.current?.click();
  }, []);

  const [validationInProgress, setValidationInProgress] = useState(false);
  async function validateKeys() {
    setValidationInProgress(true);

    try {
      const { errors: validationError } = await fetchCfKeysValidationResult(
        config,
        getValues()
      );
      if (validationError) {
        showErrorsOnForm(validationError);
      } else {
        clearErrors(FormFields.CLOUD_FRONT_PUBLIC_KEY_ID);
      }
    } catch (e) {
      showErrorAlert(errorMessage(e));
    } finally {
      setValidationInProgress(false);
    }
  }

  function openFile(evt: ChangeEvent<HTMLInputElement>) {
    const fileObj = evt.target.files?.[0];
    if (!fileObj) {
      return;
    }
    const reader = new FileReader();
    reader.onload = (e) => setPrivateKey(e.target?.result as string);
    reader.onloadend = () => {
      setPrivateKeyDetails(`Uploaded ${fileObj.name}`);
      validateKeys();
    };
    reader.readAsText(fileObj);
  }

  return (
    <>
      <FormRow
        label={
          <LabelWithHelp
            label="Public key"
            helpText="Public key part that CDN uses to verify a signed requests to artifacts"
          />
        }
        labelFor={FormFields.CLOUD_FRONT_PUBLIC_KEY_ID}
      >
        <FormSelect
          name={FormFields.CLOUD_FRONT_PUBLIC_KEY_ID}
          control={control}
          rules={{ required: 'Public key is mandatory' }}
          data={publicKeyOptions}
          filter
          onChange={setPublicKey}
          onBeforeOpen={reloadCloudPublicKeys}
          loading={isLoading}
          label="-- Select public key --"
          disabled={isMagicHappening || disabled}
        />
      </FormRow>
      <FieldRow>
        <Button
          className={styles.uploadButton}
          disabled={isLoading || isMagicHappening || disabled || isReadOnly}
          onClick={upload}
          loader={validationInProgress}
        >
          {(privateKey?.length ?? 0) > 0
            ? 'Update private key...'
            : 'Upload private key...'}
        </Button>
      </FieldRow>
      <FieldRow>
        <p className={styles.commentary}>{privateKeyDetails}</p>
      </FieldRow>
      <input
        type="file"
        className="hidden"
        multiple={false}
        accept=".pem"
        onChange={openFile}
        ref={hiddenInputEl}
      />
    </>
  );
}
