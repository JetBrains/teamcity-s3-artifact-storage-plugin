import { Content, Header } from '@jetbrains/ring-ui/components/island/island';
import Panel from '@jetbrains/ring-ui/components/panel/panel';
import Dialog from '@jetbrains/ring-ui/components/dialog/dialog';
import { React, utils } from '@jetbrains/teamcity-api';
import Loader from '@jetbrains/ring-ui/components/loader/loader';
import ButtonSet from '@jetbrains/ring-ui/components/button-set/button-set';
import Button from '@jetbrains/ring-ui/components/button/button';
import {
  errorMessage,
  useErrorService,
} from '@jetbrains-internal/tcci-react-ui-components';

import okIcon from '@jetbrains/icons/ok';

import Icon, { Color } from '@jetbrains/ring-ui/components/icon';

import { useAppContext } from '../../contexts/AppContext';
import { post } from '../../Utilities/fetchHelper';
import { encodeSecret } from '../../Utilities/parametersUtils';
import { parseErrorsFromResponse } from '../../Utilities/responseParser';
import { testAwsConnection } from '../../Utilities/testAwsConnection';

import styles2 from '../styles.css';

import styles from './styles.css';
import { AwsConnection } from './AvailableAwsConnectionsConstants';
import TestAwsConnectionDialog from './TestAwsConnectionDialog';

enum CredentialsTypeEnum {
  'awsAccessKeys' = 0,
  'awsAssumeIamRole' = 1,
  'defaultProvider' = 2,
}

function setCredentialsTypeSelector(index: CredentialsTypeEnum) {
  const credentialsType = document.getElementById(
    'awsCredentialsType'
  ) as HTMLSelectElement;

  credentialsType.selectedIndex = index;
  credentialsType.dispatchEvent(new Event('change'));
}

function setAccessKeyCredentials(
  awsAccessKeyId: string,
  awsSecretAccessKey: string
) {
  const accessKeyId = document.getElementById(
    'awsAccessKeyId'
  ) as HTMLInputElement;
  const secretAccessKey = document.getElementById(
    'secure:awsSecretAccessKey'
  ) as HTMLInputElement;
  accessKeyId.value = awsAccessKeyId;
  secretAccessKey.value = awsSecretAccessKey;
}

function setIamRoleArn(iamRole: string) {
  const iamRoleArn = document.getElementById(
    'awsIamRoleArn'
  ) as HTMLInputElement;
  iamRoleArn.value = iamRole;
}

interface OwnProps {
  active: boolean;
  mode?: 'add' | 'edit' | 'convert';
  awsConnectionIdProp: string;
  onClose: (newConnection: AwsConnection | undefined) => void;
  parametersPreset?: { [key: string]: any };
}

const HeaderForMode = {
  add: 'Add new AWS Connection',
  edit: 'Edit AWS Connection',
  convert: 'Convert to AWS Connection',
};

export default function AwsConnectionDialog({
  active,
  mode = 'add',
  awsConnectionIdProp,
  onClose,
  parametersPreset,
}: OwnProps) {
  const { projectId, publicKey } = useAppContext();
  const [loading, setLoading] = React.useState(false);
  const [initialized, setInitialized] = React.useState(false);
  const [htmlContent, setHtmlContent] = React.useState('');
  const popupRef = React.useRef<HTMLDivElement>(null);
  const { showErrorAlert } = useErrorService();

  const loadHtmlContent = React.useCallback(async () => {
    const url = `/admin/oauth/showConnection.html?providerType=AWS&projectId=${projectId}&connectionId=${awsConnectionIdProp}`;

    const response = await utils.requestText(
      url,
      {
        method: 'POST',
      },
      true
    );

    setHtmlContent(response);
  }, [awsConnectionIdProp, projectId]);

  const updateScripts = React.useCallback(() => {
    popupRef.current?.querySelectorAll('script').forEach((script) => {
      const newScript = document.createElement('script');
      newScript.textContent = script.textContent;
      script.parentNode?.replaceChild(newScript, script);
    });
  }, []);

  const injectParameters = React.useCallback(() => {
    if (parametersPreset === undefined) {
      return;
    }

    if (parametersPreset.useDefaultCredentialsProviderChain) {
      setCredentialsTypeSelector(CredentialsTypeEnum.defaultProvider);
    } else if (
      parametersPreset.awsAccessKeyId &&
      parametersPreset.awsAccessKeyId.length > 0
    ) {
      setCredentialsTypeSelector(CredentialsTypeEnum.awsAccessKeys);
      setAccessKeyCredentials(
        parametersPreset.awsAccessKeyId,
        parametersPreset.awsSecretAccessKey
      );
    } else {
      setCredentialsTypeSelector(CredentialsTypeEnum.awsAssumeIamRole);
      setIamRoleArn(parametersPreset.iamRole);
    }
  }, [parametersPreset]);

  React.useEffect(() => {
    if (active) {
      setLoading(true);
      setInitialized(false);
      loadHtmlContent()
        .then(() => setLoading(false))
        .then(updateScripts)
        .then(injectParameters)
        .finally(() => setInitialized(true));
    }
  }, [loadHtmlContent, updateScripts, active, injectParameters]);

  React.useEffect(() => {
    if (active && initialized) {
      document
        .getElementById('testConnectionButton')
        ?.setAttribute('style', 'display: none');

      const collection = document.getElementsByClassName(
        'testConnectionButton'
      );

      for (let i = 0; i < collection.length; i++) {
        collection[i].setAttribute('style', 'display: none');
      }
    }
  }, [initialized, active]);

  const __onClose = React.useCallback(
    (newConnection: AwsConnection | undefined) => {
      setHtmlContent('');
      setInitialized(false);
      onClose(newConnection);
    },
    [onClose]
  );

  const collectAwsConnectionFormData = React.useCallback(() => {
    // collect parameters from the form
    const type = 'AWS';
    const displayName = (
      document.getElementById('displayName') as HTMLInputElement | null
    )?.value;
    const connectionId = (
      document.getElementById('id') as HTMLInputElement | null
    )?.value;
    const awsRegion = (
      document.getElementById('regionSelect') as HTMLSelectElement | null
    )?.value;
    const credentialsType = (
      document.getElementById('awsCredentialsType') as HTMLSelectElement | null
    )?.value;
    const accessKeyId = (
      document.getElementById('awsAccessKeyId') as HTMLInputElement | null
    )?.value;
    let secretAccessKey =
      // @ts-ignore
      __secretKey || // __secretKey is provided by awsAccessKeysCredsComponent.jsp
      (
        document.getElementById(
          'secure:awsSecretAccessKey'
        ) as HTMLInputElement | null
      )?.value;
    if (secretAccessKey) {
      secretAccessKey = encodeSecret(secretAccessKey, publicKey);
    }
    const useSessionCredentials = (
      document.getElementById(
        'useSessionCredentialsCheckbox'
      ) as HTMLInputElement | null
    )?.checked?.toString();
    const stsEndpoint = (
      document.getElementById('stsEndpointField') as HTMLInputElement | null
    )?.value;
    const iamRoleArn = (
      document.getElementById('awsIamRoleArn') as HTMLInputElement | null
    )?.value;
    const awsConnectionId = (
      document.getElementById(
        'availableAwsConnectionsSelect'
      ) as HTMLSelectElement | null
    )?.value;

    const result: { [key: string]: string | undefined } = {
      projectId,
      saveConnection: 'save',
      providerType: type,
      'prop:displayName': displayName,
      'prop:id': connectionId || awsConnectionIdProp,
      'prop:awsRegionName': awsRegion,
      'prop:awsCredentialsType': credentialsType,
      'prop:awsAccessKeyId': accessKeyId,
      'prop:encrypted:secure:awsSecretAccessKey': secretAccessKey,
      'prop:awsSessionCredentials': useSessionCredentials,
      'prop:awsStsEndpoint': stsEndpoint,
      'prop:awsIamRoleArn': iamRoleArn,
      'prop:awsConnectionId': awsConnectionId,
      'prop:awsIamRoleSessionName': 'TeamCity-session',
      connectionId: awsConnectionIdProp,
    };
    Object.keys(result).forEach((key) => {
      if (result[key] === null || result[key] === undefined) {
        delete result[key];
      }
    });
    // now all undefined values are removed
    return result as { [key: string]: string };
  }, [awsConnectionIdProp, projectId, publicKey]);

  const submitConnection = React.useCallback(async () => {
    const formData = collectAwsConnectionFormData();
    const newConnectionId: string | undefined = formData['prop:id'];
    const newConnectionDisplayName: string | undefined =
      formData['prop:displayName'];
    const newConnectionUseSessionCreds: string | undefined =
      formData['prop:awsSessionCredentials'];
    const url = 'admin/oauth/connections.html';
    try {
      const result = await post(url, formData);
      const errors = parseErrorsFromResponse(
        new DOMParser().parseFromString(result, 'text/xml')
      );
      if (errors) {
        const messages = Object.keys(errors)
          .map((key) => errors[key].message)
          .join('\n');
        showErrorAlert(messages);
      } else {
        __onClose({
          id: newConnectionId,
          displayName: newConnectionDisplayName,
          usingSessionCreds: newConnectionUseSessionCreds === 'true',
        } as AwsConnection);
      }
    } catch (e) {
      showErrorAlert(errorMessage(e));
    }
  }, [__onClose, collectAwsConnectionFormData, showErrorAlert]);

  const [showSuccessText, setShowSuccessText] = React.useState(false);
  const [showErrorText, setShowErrorText] = React.useState(false);
  const [errorMessages, setErrorMessages] = React.useState('');
  const [testingConnection, setTestingConnection] = React.useState(false);

  const testConnection = React.useCallback(async () => {
    const formData = collectAwsConnectionFormData();

    setShowSuccessText(false);
    setShowErrorText(false);
    setTestingConnection(true);
    try {
      const result = await testAwsConnection(formData);

      if (result.success) {
        setShowSuccessText(true);
      } else {
        setShowErrorText(true);
        setErrorMessages(result.message);
      }
    } catch (e) {
      showErrorAlert(errorMessage(e));
    } finally {
      setTestingConnection(false);
    }
  }, [collectAwsConnectionFormData, showErrorAlert]);

  return (
    <Dialog
      show={active}
      onCloseAttempt={() => __onClose(undefined)}
      trapFocus
      autoFocusFirst
      showCloseButton
      className={styles.fixDialog}
    >
      <Header>{HeaderForMode[mode]}</Header>
      <Content>
        {loading ? (
          <Loader />
        ) : (
          <div
            id={'popupContainer'}
            ref={popupRef}
            dangerouslySetInnerHTML={{ __html: htmlContent }}
            style={{ display: 'inline', margin: '10px', marginTop: '0' }}
          />
        )}
      </Content>
      <Panel className={styles.awsConnectionButtonPanel}>
        <ButtonSet>
          <Button primary onClick={submitConnection}>
            {mode === 'convert' ? 'Convert' : 'Save'}
          </Button>
          <Button onClick={() => __onClose(undefined)}>{'Cancel'}</Button>
          <Button loader={testingConnection} onClick={testConnection}>
            {'Test Connection'}
          </Button>
        </ButtonSet>
        {showSuccessText && (
          <div className={styles.successText}>
            <Icon glyph={okIcon} color={Color.GREEN} />
            <p className={styles2.commentary}>{'Connection is successful'}</p>
          </div>
        )}

        <TestAwsConnectionDialog
          active={showErrorText}
          status={'failed'}
          testConnectionInfo={errorMessages}
          onClose={() => setShowErrorText(false)}
        />
      </Panel>
    </Dialog>
  );
}
