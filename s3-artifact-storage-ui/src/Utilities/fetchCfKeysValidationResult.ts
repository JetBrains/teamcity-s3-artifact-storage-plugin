import { ResponseErrors } from '@jetbrains-internal/tcci-react-ui-components/dist/types';

import { Config, IFormInput } from '../types';

import { FetchResourceIds } from '../App/appConstants';

import { serializeParameters } from './parametersUtils';
import { post } from './fetchHelper';
import { parseErrorsFromResponse, parseResponse } from './responseParser';

interface FetchCfKeysValidationResultResponse {
  isValid: boolean | null;
  errors: ResponseErrors | null;
}

export async function fetchCfKeysValidationResult(
  config: Config,
  data: IFormInput
): Promise<FetchCfKeysValidationResultResponse> {
  const parameters = {
    ...serializeParameters(data, config),
    resource: FetchResourceIds.VALIDATE_CLOUD_FRONT_KEYS,
  };

  return await post(config.containersPath, parameters).then((resp) => {
    const response = new DOMParser().parseFromString(resp, 'text/xml');
    const errors: ResponseErrors | null = parseErrorsFromResponse(response);
    if (errors) {
      return { isValid: null, errors };
    }

    const validationResult = parseResponse(
      response,
      'cfKeysValidationResult'
    )[0]?.textContent;
    const isValid = validationResult === 'OK';
    if (isValid) return { isValid, errors: null };
    else if (validationResult) {
      return {
        isValid,
        errors: {
          [FetchResourceIds.VALIDATE_CLOUD_FRONT_KEYS]: {
            message: validationResult,
          },
        },
      };
    } else {
      return { isValid, errors: null };
    }
  });
}
