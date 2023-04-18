import {React} from '@jetbrains/teamcity-api';
import {useFormContext} from 'react-hook-form';
import {useCallback, useMemo, useState} from 'react';
import {FormCheckbox, FormInput, FormRow, Option, SectionHeader, Switcher} from '@teamcity-cloud-integrations/react-ui-components';
import {Caption} from '@jetbrains/ring-ui/components/button-group/button-group';
import inputStyles from '@jetbrains/ring-ui/components/input/input.css';
import {Config, IFormInput} from '../types';

import {FormFields} from './appConstants';


type OwnProps = Config

const accessKeysType = 'aws.access.keys';
const tempCredsType = 'aws.temp.credentials';

interface CredentialType extends Option<number> {
    keyData: string;
    details: string;
}
export const AWS_CREDENTIALS_TYPE_ARRAY: CredentialType[] = [
  {label: 'Access keys', key: 0, keyData: accessKeysType, details: 'Use pre-configured AWS account access keys'},
  {label: 'Temporary credentials', key: 1, keyData: tempCredsType, details: 'Get temporary access keys via AWS STS'}
];

export default function AwsSecurityCredentials({...config}: OwnProps) {
  const credentialTypesFieldName = FormFields.CREDENTIALS_TYPE;
  const defaultChainFlagFieldName = FormFields.USE_DEFAULT_CREDENTIAL_PROVIDER_CHAIN;
  const {control, getValues, setValue, watch} = useFormContext<IFormInput>();

  const [credentialType, setCredentialType] = useState(getValues(credentialTypesFieldName));
  const [defaultCredsFlag, setDefaultCredsFlag] = useState(getValues(defaultChainFlagFieldName));

  const useDefaultCredentialsChainFlagChanged = (event: any) => {
    setDefaultCredsFlag(event.target.value);
  };

  const handleCredTypeChange = useCallback((option: CredentialType) => {
    setValue(credentialTypesFieldName, option.keyData);
    setCredentialType(option.keyData);
  }, [credentialTypesFieldName, setValue]);

  const currentCredentials = watch(credentialTypesFieldName);
  const currentActiveCredentials = AWS_CREDENTIALS_TYPE_ARRAY.
    find(cred => cred.keyData === currentCredentials)?.
    key || 0;

  const credentialsTypeFormRow = useMemo(() => (
    <div>
      <span className={inputStyles.label}>{'Credentials type:'}</span>
      <Switcher
        options={AWS_CREDENTIALS_TYPE_ARRAY}
        active={currentActiveCredentials}
        onClick={handleCredTypeChange}
      />
    </div>
  ), [currentActiveCredentials, handleCredTypeChange]);

  return (
    <section>
      <SectionHeader>{'AWS Security Credentials'}</SectionHeader>
      {credentialsTypeFormRow}
      {credentialType === tempCredsType && (
        <>
          <FormRow
            label="IAM role ARN"
            star
            labelFor={FormFields.IAM_ROLE_ARN}
          >
            <FormInput
              name={FormFields.IAM_ROLE_ARN}
              control={control}
              rules={{required: 'IAM role ARN is mandatory'}}
              details="Pre-configured IAM role with necessary permissions"
            />
          </FormRow>
          <FormRow
            label="External ID"
            labelFor={FormFields.EXTERNAL_ID}
          >
            <FormInput
              name={FormFields.EXTERNAL_ID}
              control={control}
              details="External ID is strongly recommended to be used in role trust relationship condition"
            />
          </FormRow>
        </>
      )
      }
      {config.showDefaultCredentialsChain && (
        <FormRow label="Default Credential Provider Chain">
          <FormCheckbox
            name={defaultChainFlagFieldName}
            control={control}
            rules={{onChange: useDefaultCredentialsChainFlagChanged}}
            checked={defaultCredsFlag ?? false}
          />
        </FormRow>
      )}
      {!defaultCredsFlag && (
        <>
          <FormRow
            label="Access key ID"
            star
            labelFor={FormFields.ACCESS_KEY_ID}
          >
            <FormInput
              name={FormFields.ACCESS_KEY_ID}
              control={control}
              rules={{required: 'Access key ID is mandatory'}}
              details="AWS account access key ID"
            />
          </FormRow>
          <FormRow
            label="Secret access key"
            star
            labelFor={FormFields.SECRET_ACCESS_KEY}
          >
            <FormInput
              name={FormFields.SECRET_ACCESS_KEY}
              control={control}
              rules={{required: 'Secret access key is mandatory'}}
              details="AWS account secret access key"
              type="password"
            />
          </FormRow>
        </>
      )
      }
    </section>
  );
}
