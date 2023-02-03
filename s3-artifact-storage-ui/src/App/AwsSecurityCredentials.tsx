import {React} from '@jetbrains/teamcity-api';

import {useFormContext} from 'react-hook-form';

import {useMemo, useState} from 'react';

import {FormRadio, FormRow, FormToggle, FormInput, SectionHeader, Label, Field, Row} from '@teamcity-cloud-integrations/react-ui-components';

import {Config, IFormInput} from '../types';

import {FormFields} from './appConstants';

import styles from './styles.css';

type OwnProps = Config

const accessKeysType = 'aws.access.keys';
const tempCredsType = 'aws.temp.credentials';

export const AWS_CREDENTIALS_TYPE_ARRAY = [
  {label: 'Access keys', value: accessKeysType, details: 'Use pre-configured AWS account access keys'},
  {label: 'Temporary credentials', value: tempCredsType, details: 'Get temporary access keys via AWS STS'}
];

export default function AwsSecurityCredentials({...config}: OwnProps) {
  const credentialTypesFieldName = FormFields.CREDENTIALS_TYPE;
  const defaultChainFlagFieldName = FormFields.USE_DEFAULT_CREDENTIAL_PROVIDER_CHAIN;
  const {control, getValues} = useFormContext<IFormInput>();

  const [credentialType, setCredentialType] = useState(getValues(credentialTypesFieldName));
  const [defaultCredsFlag, setDefaultCredsFlag] = useState(getValues(defaultChainFlagFieldName));

  const credentialTypeChanged = (event: any) => {
    setCredentialType(event.target.value);
  };

  const useDefaultCredentialsChainFlagChanged = (event: any) => {
    setDefaultCredsFlag(event.target.value);
  };

  const credentialsTypeFormRow = useMemo(() => (
    <Row>
      <div className={styles.credTypesCustom}>
        <Label required>{'Credentials type:'}</Label>
        <a href="https://console.aws.amazon.com/iam" target="_blank" rel="noopener noreferrer">{'Open IAM Console'}</a>
      </div>
      <Field>
        <FormRadio
          data={AWS_CREDENTIALS_TYPE_ARRAY}
          name={credentialTypesFieldName}
          control={control}
          rules={{
            required: 'Credentials type is mandatory',
            onChange: credentialTypeChanged
          }}
        />
      </Field>
    </Row>
  ), [control, credentialTypesFieldName]);

  return (
    <section>
      <SectionHeader>{'AWS Security Credentials'}</SectionHeader>
      {credentialsTypeFormRow}
      {credentialType === tempCredsType && (
        <>
          <FormRow
            label="IAM role ARN:"
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
            label="External ID:"
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
        <FormRow label="Default Credential Provider Chain:">
          <FormToggle
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
            label="Access key ID:"
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
            label="Secret access key:"
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
