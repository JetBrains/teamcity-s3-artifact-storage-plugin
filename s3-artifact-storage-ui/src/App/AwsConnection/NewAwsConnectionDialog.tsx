import { Content, Header } from '@jetbrains/ring-ui/components/island/island';
import Panel from '@jetbrains/ring-ui/components/panel/panel';
import Dialog from '@jetbrains/ring-ui/components/dialog/dialog';
import { React, utils } from '@jetbrains/teamcity-api';

import { useCallback, useEffect } from 'react';

import Loader from '@jetbrains/ring-ui/components/loader/loader';

import ButtonSet from '@jetbrains/ring-ui/components/button-set/button-set';

import Button from '@jetbrains/ring-ui/components/button/button';

import {
  errorMessage,
  useErrorService,
} from '@jetbrains-internal/tcci-react-ui-components';

import styles from '../styles.css';
import { useAppContext } from '../../contexts/AppContext';
import { post } from '../../Utilities/fetchHelper';
import { encodeSecret } from '../../Utilities/parametersUtils';
import { parseErrorsFromResponse } from '../../Utilities/responseParser';

import { AwsConnection } from './AvailableAwsConnectionsConstants';
import TestAwsConnectionDialog from './TestAwsConnectionDialog';

export default function NewAwsConnectionDialog({
  active,
  onClose,
}: {
  active: boolean;
  onClose: (newConnection: AwsConnection | undefined) => void;
}) {
  const { projectId, publicKey } = useAppContext();
  const [loading, setLoading] = React.useState(false);
  const [initialized, setInitialized] = React.useState(false);
  const [htmlContent, setHtmlContent] = React.useState('');
  const popupRef = React.useRef<HTMLDivElement>(null);
  const { showErrorAlert } = useErrorService();

  const loadHtmlContent = React.useCallback(async () => {
    const url = `/admin/oauth/showConnection.html?providerType=AWS&projectId=${projectId}`;

    const response = await utils.requestText(
      url,
      {
        method: 'POST',
      },
      true
    );

    setHtmlContent(response);
  }, [projectId]);

  const updateScripts = useCallback(() => {
    popupRef.current?.querySelectorAll('script').forEach((script) => {
      const newScript = document.createElement('script');
      newScript.textContent = script.textContent;
      script.parentNode?.replaceChild(newScript, script);
    });
  }, []);

  useEffect(() => {
    if (active && !initialized) {
      setLoading(true);
      setInitialized(false);
      loadHtmlContent()
        .then(() => setLoading(false))
        .then(updateScripts)
        .finally(() => setInitialized(true));
    } else if (active) {
      updateScripts();
    }
  }, [loadHtmlContent, initialized, updateScripts, active]);

  useEffect(() => {
    if (active && initialized) {
      document
        .getElementById('testConnectionButton')
        ?.setAttribute('style', 'display: none');
    }
  }, [initialized, active]);

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
    let secretAccessKey = (
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
      'prop:id': connectionId,
      'prop:awsRegionName': awsRegion,
      'prop:awsCredentialsType': credentialsType,
      'prop:awsAccessKeyId': accessKeyId,
      'prop:encrypted:secure:awsSecretAccessKey': secretAccessKey,
      'prop:awsSessionCredentials': useSessionCredentials,
      'prop:awsStsEndpoint': stsEndpoint,
      'prop:awsIamRoleArn': iamRoleArn,
      'prop:awsConnectionId': awsConnectionId,
    };
    Object.keys(result).forEach((key) => {
      if (result[key] === null || result[key] === undefined) {
        delete result[key];
      }
    });
    // now all undefined values are removed
    return result as { [key: string]: string };
  }, [projectId, publicKey]);

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
        onClose({
          id: newConnectionId,
          displayName: newConnectionDisplayName,
          usingSessionCreds: newConnectionUseSessionCreds === 'true',
        } as AwsConnection);
      }
    } catch (e) {
      showErrorAlert(errorMessage(e));
    }
  }, [collectAwsConnectionFormData, onClose, showErrorAlert]);

  const [testDialogActive, setTestDialogActive] = React.useState(false);
  const [currentFormData, setCurrentFormData] = React.useState<{
    [key: string]: string;
  }>({});
  const testConnection = React.useCallback(() => {
    const formData = collectAwsConnectionFormData();
    setCurrentFormData(formData);
    setTestDialogActive(true);
  }, [collectAwsConnectionFormData]);

  return (
    <>
      <Dialog
        show={active}
        onCloseAttempt={() => onClose(undefined)}
        trapFocus
        autoFocusFirst
        showCloseButton
        className={styles.fixDialog}
      >
        <Header>{'Add new AWS Connection'}</Header>
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
        <Panel>
          <ButtonSet>
            <Button primary onClick={submitConnection}>
              {'Save'}
            </Button>
            <Button onClick={() => onClose(undefined)}>{'Cancel'}</Button>
            <Button onClick={testConnection}>{'Test Connection'}</Button>
          </ButtonSet>
        </Panel>
      </Dialog>
      <TestAwsConnectionDialog
        active={testDialogActive}
        formData={currentFormData}
        onClose={() => setTestDialogActive(false)}
      />
    </>
  );
}
