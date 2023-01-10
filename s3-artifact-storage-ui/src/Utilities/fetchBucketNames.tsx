import {FetchResourceIds} from '../App/appConstants';

import {Config, IFormInput} from '../types';

import {post} from './fetchHelper';
import {serializeParameters} from './parametersUtils';
import {displayErrorsFromResponseIfAny, parseResourceListFromResponse, ResponseErrors} from './responseParser';


type OwnProps = {
  appProps: Config
  allValues: IFormInput
  useDefaultCredentialProviderChain?: boolean | null,
  keyId?: string | null,
  keySecret?: string | null,
}

export type LoadBucketListResponse = {
    bucketNames: string[] | null
    errors: ResponseErrors | null
}

export async function loadBucketList({
  appProps,
  allValues,
  useDefaultCredentialProviderChain,
  keyId,
  keySecret
}: OwnProps)
    : Promise<LoadBucketListResponse> {
  if (!useDefaultCredentialProviderChain && (!keyId || !keySecret)) {
    return {bucketNames: [], errors: null};
  }

  const parameters = {
    ...serializeParameters(allValues, appProps),
    resource: FetchResourceIds.BUCKETS
  };

  return await post(appProps.containersPath, parameters).then(resp => {
    const response = window.$j(resp);
    const errors: ResponseErrors | null = displayErrorsFromResponseIfAny(response);
    if (errors) {
      return {bucketNames: null, errors};
    }

    const bucketNames = parseResourceListFromResponse(response, 'buckets:eq(0) bucket').map(n => n.textContent!);

    return {bucketNames, errors: null};
  });
}
