import { React } from '@jetbrains/teamcity-api';
import {
  FieldColumn,
  FieldRow,
  FormRow,
  FormSelect,
  useErrorService,
} from '@teamcity-cloud-integrations/react-ui-components';
import { useFormContext } from 'react-hook-form';
import { ReactNode, useCallback } from 'react';

import { errorIdToFieldName } from '../../../appConstants';
import useCfDistributions from '../../../../hooks/useCfDistributions';
import { useCloudFrontDistributionsContext } from '../contexts/CloudFrontDistributionsContext';

import { DistributionItem } from '../../../../types';

import MagicDistributionsButton from './MagicDistributionsButton';

interface OwnProps {
  label: ReactNode;
  fieldName: string;
  onChange?: (distribution: DistributionItem | null) => void;
}

export default function Distribution({ label, fieldName, onChange }: OwnProps) {
  const { control, getValues, setError, clearErrors } = useFormContext();
  const { showErrorsOnForm } = useErrorService({
    setError,
    errorKeyToFieldNameConvertor: errorIdToFieldName,
  });
  const { isLoading, errors, reloadDistributions, cfDistributions } =
    useCfDistributions();
  const { isMagicHappening, disabled, setCfDistributions } =
    useCloudFrontDistributionsContext();

  const reloadCloudDistributions = useCallback(async () => {
    clearErrors(fieldName);
    const result = await reloadDistributions(getValues());
    result && setCfDistributions(result);
  }, [
    clearErrors,
    fieldName,
    reloadDistributions,
    getValues,
    setCfDistributions,
  ]);

  if (errors) {
    showErrorsOnForm(errors);
  }

  return (
    <FormRow label={label} labelFor={fieldName}>
      <FieldRow>
        <FieldColumn>
          <FormSelect
            name={fieldName}
            control={control}
            // selected={uploadDistributionData[0]}
            rules={{ required: 'Distribution is mandatory' }}
            data={cfDistributions}
            onChange={onChange}
            onBeforeOpen={reloadCloudDistributions}
            loading={isLoading}
            label="-- Select distribution --"
            disabled={isMagicHappening || disabled}
          />
        </FieldColumn>
        <FieldColumn>
          <MagicDistributionsButton />
        </FieldColumn>
      </FieldRow>
    </FormRow>
  );
}
