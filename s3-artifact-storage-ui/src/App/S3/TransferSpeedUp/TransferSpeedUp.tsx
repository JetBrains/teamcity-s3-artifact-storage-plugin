import { React } from '@jetbrains/teamcity-api';
import {
  Option,
  SectionHeader,
  Switcher,
  SwitcherOption,
} from '@teamcity-cloud-integrations/react-ui-components';
import inputStyles from '@jetbrains/ring-ui/components/input/input.css';

import { useFormContext } from 'react-hook-form';

import Loader from '@jetbrains/ring-ui/components/loader/loader';

import { useCallback, useMemo } from 'react';

import { useAppContext } from '../../../contexts/AppContext';
import { FormFields } from '../../appConstants';
import { IFormInput } from '../../../types';

import styles from '../../styles.css';

import {
  CloudFrontDistributionsContextProvider,
  useCloudFrontDistributionsContext,
} from './contexts/CloudFrontDistributionsContext';
import DownloadDistribution from './components/DownloadDistribution';
import UploadDistribution from './components/UploadDistribution';
import TrustedKeyGroup from './components/TrustedKeyGroup';

function CloudFrontLoaderWrapper() {
  const { isInitialized } = useCloudFrontDistributionsContext();

  if (isInitialized) {
    return (
      <>
        <DownloadDistribution />
        <UploadDistribution />
        <TrustedKeyGroup />
      </>
    );
  } else {
    return <Loader className={styles.cfLoader} />;
  }
}
export default function TransferSpeedUp() {
  const { watch, setValue } = useFormContext<IFormInput>();
  const config = useAppContext();

  const currentBucket = watch(FormFields.S3_BUCKET_NAME);
  const isBucketSelected = !!currentBucket;

  const speedUpOptions: SwitcherOption[] = [
    { key: 0, label: 'None' },
    { key: 1, label: 'AWS CloudFront', disabled: !isBucketSelected },
  ];

  const s3TransferAccelerationAvailable =
    watch(FormFields.S3_TRANSFER_ACCELERATION_AVAILABLE) ?? false;

  if (config.transferAccelerationOn) {
    speedUpOptions.push({
      key: 2,
      label: 'S3 Transfer Acceleration',
      disabled: !s3TransferAccelerationAvailable || !isBucketSelected,
      title: s3TransferAccelerationAvailable
        ? undefined
        : 'S3 Transfer Acceleration is not available on selected bucket.',
    });
  }

  const cloudFrontAcceleration = watch(FormFields.CLOUD_FRONT_TOGGLE);
  const s3TransferAcceleration = watch(
    FormFields.CONNECTION_TRANSFER_ACCELERATION_TOGGLE
  );
  const currentActive = useMemo(() => {
    if (cloudFrontAcceleration) return 1;
    else if (s3TransferAcceleration) return 2;
    else return 0;
  }, [cloudFrontAcceleration, s3TransferAcceleration]);

  const handleTypeChange = useCallback(
    (option: Option<number>) => {
      if (option.key === 0) {
        setValue(FormFields.CLOUD_FRONT_TOGGLE, false, {
          shouldDirty: true,
          shouldTouch: true,
        });
        setValue(FormFields.CONNECTION_TRANSFER_ACCELERATION_TOGGLE, false, {
          shouldDirty: true,
          shouldTouch: true,
        });
      } else if (option.key === 1) {
        setValue(FormFields.CLOUD_FRONT_TOGGLE, true, {
          shouldDirty: true,
          shouldTouch: true,
        });
        setValue(FormFields.CONNECTION_TRANSFER_ACCELERATION_TOGGLE, false, {
          shouldDirty: true,
          shouldTouch: true,
        });
      } else if (option.key === 2) {
        setValue(FormFields.CLOUD_FRONT_TOGGLE, false, {
          shouldDirty: true,
          shouldTouch: true,
        });
        setValue(FormFields.CONNECTION_TRANSFER_ACCELERATION_TOGGLE, true, {
          shouldDirty: true,
          shouldTouch: true,
        });
      }
    },
    [setValue]
  );

  return (
    <section>
      <SectionHeader>{'Transfer speed-up'}</SectionHeader>
      <div>
        <span className={inputStyles.label}>{'Type'}</span>
        <Switcher
          options={speedUpOptions}
          active={currentActive}
          onClick={handleTypeChange}
        />
      </div>
      {currentActive === 1 && (
        <CloudFrontDistributionsContextProvider>
          <CloudFrontLoaderWrapper />
        </CloudFrontDistributionsContextProvider>
      )}
    </section>
  );
}
