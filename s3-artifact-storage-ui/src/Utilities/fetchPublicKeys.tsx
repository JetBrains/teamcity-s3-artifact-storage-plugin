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

export type PublicKeyType = { id: string, name: string }

export type LoadPublicKeyListResponse = {
    publicKeys: PublicKeyType[] | null
    errors: ResponseErrors | null
}

export async function loadPublicKeyList({
  appProps,
  allValues,
  useDefaultCredentialProviderChain,
  keyId,
  keySecret
}: OwnProps)
    : Promise<LoadPublicKeyListResponse> {
  if (!useDefaultCredentialProviderChain && (!keyId || !keySecret)) {
    return {publicKeys: [], errors: null};
  }

  const parameters = {
    ...serializeParameters(allValues, appProps),
    resource: FetchResourceIds.PUBLIC_KEYS
  };

  return await post(appProps.containersPath, parameters).then(resp => {
    const response = window.$j(resp);

    const errors: ResponseErrors | null = displayErrorsFromResponseIfAny(response);
    if (errors) {
      return {publicKeys: null, errors};
    }

    const publicKeys = parseResourceListFromResponse(response, 'publicKeys:eq(0) publicKey').map(n => window.$j(n)).map(
      g => {
        const id = g.find('id').text();
        const name = g.find('name').text();
        return {id, name};
      });

    return {publicKeys, errors: null};
  });
}
