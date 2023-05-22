import { ResponseErrors } from '@jetbrains-internal/tcci-react-ui-components/dist/types';

import { FetchResourceIds } from '../App/appConstants';

import { Config, IFormInput } from '../types';

import { post } from './fetchHelper';
import { serializeParameters } from './parametersUtils';
import {
  displayErrorsFromResponseIfAny,
  parseResourceListFromResponse,
} from './responseParser';

export type LoadBucketListResponse = {
  bucketNames: string[] | null;
  errors: ResponseErrors | null;
};

export async function loadBucketList(
  config: Config,
  data: IFormInput
): Promise<LoadBucketListResponse> {
  const parameters = {
    ...serializeParameters(data, config),
    resource: FetchResourceIds.BUCKETS,
  };

  return await post(config.containersPath, parameters).then((resp) => {
    const response = window.$j(resp);
    const errors: ResponseErrors | null =
      displayErrorsFromResponseIfAny(response);
    if (errors) {
      return { bucketNames: null, errors };
    }

    const bucketNames = parseResourceListFromResponse(
      response,
      'buckets:eq(0) bucket'
    ).map((n) => n.textContent!);

    return { bucketNames, errors: null };
  });
}
