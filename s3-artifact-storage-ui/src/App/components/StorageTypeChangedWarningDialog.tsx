import { React } from '@jetbrains/teamcity-api';
import Dialog from '@jetbrains/ring-ui/components/dialog/dialog';
import { Content, Header } from '@jetbrains/ring-ui/components/island/island';

import Panel from '@jetbrains/ring-ui/components/panel/panel';

import Button from '@jetbrains/ring-ui/components/button/button';

import { AWS_S3, S3_COMPATIBLE } from '../Storage/components/StorageType';
import { useAppContext } from '../../contexts/AppContext';
import { FormFields } from '../appConstants';
import useS3Form from '../../hooks/useS3Form';

export default function StorageTypeChangedWarningDialog() {
  const [visited, setVisited] = React.useState(false);
  const config = useAppContext();
  const { watch } = useS3Form();
  const currentType = watch(FormFields.STORAGE_TYPE);
  const isS3Compatible = currentType?.key === S3_COMPATIBLE;
  const incorrectStorageTypeInProperties = React.useMemo(
    () =>
      !config.isNewStorage &&
      config.selectedStorageType === AWS_S3 &&
      isS3Compatible,
    [config.isNewStorage, config.selectedStorageType, isS3Compatible]
  );

  return (
    <Dialog
      show={incorrectStorageTypeInProperties && !visited}
      onCloseAttempt={() => setVisited(true)}
      autoFocusFirst
      dense
      trapFocus
      showCloseButton={false}
    >
      <Header>{'Information'}</Header>
      <Content>
        <div>
          {
            'Your storage is not an S3 bucket hosted on Amazon Web Services. The storage type will be set to "Custom S3".'
          }
        </div>
      </Content>
      <Panel>
        <Button primary onClick={() => setVisited(true)}>
          {'OK'}
        </Button>
      </Panel>
    </Dialog>
  );
}
