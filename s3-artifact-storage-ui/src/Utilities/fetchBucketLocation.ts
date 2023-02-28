import {Config, IFormInput} from '../types';

import {FetchResourceIds} from '../App/appConstants';

import {displayErrorsFromResponseIfAny, parseResourceListFromResponse, ResponseErrors} from './responseParser';
import {serializeParameters} from './parametersUtils';
import {post} from './fetchHelper';

type OwnProps = {
    appProps: Config
    allValues: IFormInput
    useDefaultCredentialProviderChain?: boolean | null,
    keyId?: string | null,
    keySecret?: string | null,
}

export type FetchBucketLoactionResponse = {
    location: string | null
    errors: ResponseErrors | null
}

export async function fetchBucketLocation(
  {
    appProps,
    allValues,
    useDefaultCredentialProviderChain,
    keyId,
    keySecret
  }: OwnProps
): Promise<FetchBucketLoactionResponse> {
  if (!useDefaultCredentialProviderChain && (!keyId || !keySecret)) {
    return {location: null, errors: null};
  }

  const parameters = {
    ...serializeParameters(allValues, appProps),
    resource: FetchResourceIds.BUCKET_LOCATION
  };

  return await post(appProps.containersPath, parameters).then(resp => {
    const response = window.$j(resp);
    const errors: ResponseErrors | null = displayErrorsFromResponseIfAny(response);
    if (errors) {
      return {location: null, errors};
    }

    const location = parseResourceListFromResponse(response, 'bucket:eq(0)').
      map(it => window.$j(it)).
      map(n => n.attr('location'))?.[0] ?? null;

    return {location, errors: null};
  });
}
