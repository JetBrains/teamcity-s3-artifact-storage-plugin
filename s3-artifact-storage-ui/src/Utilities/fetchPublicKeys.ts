import { ResponseErrors } from '@jetbrains-internal/tcci-react-ui-components/dist/types';

import { FetchResourceIds } from '../App/appConstants';

import { Config, IFormInput } from '../types';

import { post } from './fetchHelper';
import { serializeParameters } from './parametersUtils';
import {
  displayErrorsFromResponseIfAny,
  parseResourceListFromResponse,
} from './responseParser';

export type PublicKeyType = { id: string; name: string };

export type LoadPublicKeyListResponse = {
  publicKeys: PublicKeyType[] | null;
  errors: ResponseErrors | null;
};

export async function loadPublicKeyList(
  appProps: Config,
  allValues: IFormInput
): Promise<LoadPublicKeyListResponse> {
  const parameters = {
    ...serializeParameters(allValues, appProps),
    resource: FetchResourceIds.PUBLIC_KEYS,
  };

  return await post(appProps.containersPath, parameters).then((resp) => {
    const response = window.$j(resp);

    const errors: ResponseErrors | null =
      displayErrorsFromResponseIfAny(response);
    if (errors) {
      return { publicKeys: null, errors };
    }

    const publicKeys = parseResourceListFromResponse(
      response,
      'publicKeys:eq(0) publicKey'
    )
      .map((n) => window.$j(n))
      .map((g) => {
        const id = g.find('id').text();
        const name = g.find('name').text();
        return { id, name };
      });

    return { publicKeys, errors: null };
  });
}
