import { React } from '@jetbrains/teamcity-api';
import Dialog from '@jetbrains/ring-ui/components/dialog/dialog';

import { Content, Header } from '@jetbrains/ring-ui/components/island/island';
import { useCallback, useEffect, useState } from 'react';

import Loader from '@jetbrains/ring-ui/components/loader/loader';

import { errorMessage } from '@jetbrains-internal/tcci-react-ui-components';

import { post } from '../../Utilities/fetchHelper';
import {
  parseErrorsFromResponse,
  parseResponse,
} from '../../Utilities/responseParser';

const url = '/repo/aws-test-connection.html';
const BASE_TEST_CONNECTION_PREFIX = 'Running STS get-caller-identity...\n';

function TestConnectionContent({
  loading,
  success,
  testConnectionInfo,
}: {
  loading: boolean;
  success: boolean;
  testConnectionInfo: string;
}) {
  if (loading) {
    return <Loader />;
  }

  if (success) {
    return (
      <>
        <div className="testConnectionSuccess">{'Connection successful!'}</div>
        <div className="mono" style={{ whiteSpace: 'pre-line' }}>
          {testConnectionInfo}
        </div>
      </>
    );
  } else {
    return (
      <>
        <div className="testConnectionFailed">{'Connection failed.'}</div>
        <div className="mono" style={{ whiteSpace: 'pre-line' }}>
          {testConnectionInfo}
        </div>
      </>
    );
  }
}

export default function TestAwsConnectionDialog({
  active,
  formData,
  onClose,
}: {
  active: boolean;
  formData: { [key: string]: string };
  onClose: () => void;
}) {
  const [testConnectionInfo, setTestConnectionInfo] = useState(
    BASE_TEST_CONNECTION_PREFIX
  );
  const [loading, setLoading] = useState(false);
  const [success, setSuccess] = useState(false);

  const testConnection = useCallback(async () => {
    const responseStr = await post(url, formData);
    const response = new DOMParser().parseFromString(responseStr, 'text/xml');
    const errors = parseErrorsFromResponse(response);
    const result: Element | undefined = parseResponse(
      response,
      'callerIdentity'
    )[0];
    if (result) {
      const account = result.getAttribute('accountId');
      const userId = result.getAttribute('userId');
      const arn = result.getAttribute('userArn');
      setTestConnectionInfo(
        `${BASE_TEST_CONNECTION_PREFIX}Caller Identity:\n Account ID: ${account}\n User ID: ${userId}\n ARN: ${arn}`
      );
      setSuccess(true);
    } else if (errors) {
      setTestConnectionInfo(
        Object.keys(errors)
          .map((key) => errors[key].message)
          .join('\n')
      );
    } else {
      setTestConnectionInfo(
        `${BASE_TEST_CONNECTION_PREFIX}Could not get the Caller Identity information from the response.`
      );
    }
  }, [formData]);

  useEffect(() => {
    if (active) {
      setLoading(true);
      setSuccess(false);
      setTestConnectionInfo(BASE_TEST_CONNECTION_PREFIX);
      testConnection()
        .catch((err) => {
          setSuccess(false);
          setTestConnectionInfo(errorMessage(err));
        })
        .finally(() => setLoading(false));
    }
  }, [active, testConnection]);

  return (
    <Dialog
      show={active}
      onCloseAttempt={onClose}
      trapFocus
      autoFocusFirst
      showCloseButton
    >
      <Header>{'Test Connection'}</Header>
      <Content>
        <TestConnectionContent
          loading={loading}
          success={success}
          testConnectionInfo={testConnectionInfo}
        />
      </Content>
    </Dialog>
  );
}
