import { React } from '@jetbrains/teamcity-api';
import Dialog from '@jetbrains/ring-ui/components/dialog/dialog';

import { Content, Header } from '@jetbrains/ring-ui/components/island/island';

import Loader from '@jetbrains/ring-ui/components/loader/loader';

function TestConnectionContent({
  loading = false,
  success,
  testConnectionInfo,
}: {
  loading?: boolean;
  success?: boolean;
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
  testConnectionInfo,
  status,
  onClose,
}: {
  active: boolean;
  status: 'success' | 'failed';
  testConnectionInfo: string;
  onClose: () => void;
}) {
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
          success={status === 'success'}
          testConnectionInfo={testConnectionInfo}
        />
      </Content>
    </Dialog>
  );
}
