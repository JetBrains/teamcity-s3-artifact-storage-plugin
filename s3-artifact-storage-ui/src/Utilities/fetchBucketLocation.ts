import { ResponseErrors } from '@jetbrains-internal/tcci-react-ui-components/dist/types';

import { Config, IFormInput } from '../types';
import { FetchResourceIds } from '../App/appConstants';

import {
  displayErrorsFromResponseIfAny,
  parseResourceListFromResponse,
} from './responseParser';
import { serializeParameters } from './parametersUtils';
import { post } from './fetchHelper';

export type FetchBucketLoactionResponse = {
  location: string | null;
  errors: ResponseErrors | null;
};

export async function fetchBucketLocation(
  config: Config,
  data: IFormInput
): Promise<FetchBucketLoactionResponse> {
  const parameters = {
    ...serializeParameters(data, config),
    resource: FetchResourceIds.BUCKET_LOCATION,
  };

  return await post(config.containersPath, parameters).then((resp) => {
    const response = window.$j(resp);
    const errors: ResponseErrors | null =
      displayErrorsFromResponseIfAny(response);
    if (errors) {
      return { location: null, errors };
    }

    const location =
      parseResourceListFromResponse(response, 'bucket:eq(0)')
        .map((it) => window.$j(it))
        .map((n) => n.attr('location'))?.[0] ?? null;

    return { location, errors: null };
  });
}
