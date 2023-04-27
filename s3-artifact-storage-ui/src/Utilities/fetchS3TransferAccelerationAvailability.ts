import { ResponseErrors } from '@teamcity-cloud-integrations/react-ui-components/dist/types';

import { Config, IFormInput } from '../types';
import { FetchResourceIds } from '../App/appConstants';

import { serializeParameters } from './parametersUtils';
import { post } from './fetchHelper';
import { parseErrorsFromResponse, parseResponse } from './responseParser';

type FetchS3TransferAccelerationAvailabilityResponse = {
  isAvailable: boolean | null;
  errors: ResponseErrors | null;
};
export async function fetchS3TransferAccelerationAvailability(
  config: Config,
  data: IFormInput
): Promise<FetchS3TransferAccelerationAvailabilityResponse> {
  const parameters = {
    ...serializeParameters(data, config),
    resource: FetchResourceIds.S3_TRANSFER_ACCELERATION_AVAILABILITY,
  };

  return await post(config.containersPath, parameters).then((resp) => {
    const response = new DOMParser().parseFromString(resp, 'text/xml');
    const errors: ResponseErrors | null = parseErrorsFromResponse(response);
    if (errors) {
      return { isAvailable: null, errors };
    }

    const isAvailable =
      parseResponse(response, 's3Acceleration')[0]?.getAttribute(
        'accelerationStatus'
      ) === 'Enabled';
    return { isAvailable, errors: null };
  });
}
