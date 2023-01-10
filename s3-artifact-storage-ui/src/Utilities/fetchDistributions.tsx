import {FetchResourceIds} from '../App/appConstants';

import {Config, IFormInput} from '../types';

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

export type Distribution = {
    id: string
    description: string | null
    enabled: boolean
    publicKeys: string[] | null
}

export type LoadDistributionListResponse = {
    distributions: Distribution[] | null
    errors: ResponseErrors | null
}

export async function loadDistributionList({
  appProps,
  allValues,
  useDefaultCredentialProviderChain,
  keyId,
  keySecret
}: OwnProps)
    : Promise<LoadDistributionListResponse> {
  if (!useDefaultCredentialProviderChain && (!keyId || !keySecret)) {
    return {distributions: [], errors: null};
  }

  const parameters = {
    ...serializeParameters(allValues, appProps),
    resource: FetchResourceIds.DISTRIBUTIONS
  };

  return await post(appProps.containersPath, parameters).then(resp => {
    const response = window.$j(resp);

    const errors: ResponseErrors | null = displayErrorsFromResponseIfAny(response);
    if (errors) {
      return {distributions: null, errors};
    }

    const distributions = parseResourceListFromResponse(response, 'distributions:eq(0) distribution').map(
      n => window.$j(n)).map(d => {
      const id = d.find('id').text();
      let description = null;
      // special case, when description tag is actually empty.
      // for some reason JQuery wraps tags under <description/>
      // so <description/><enabled>true</enabled>
      // becomes <description><enabled>true</enable></description>
      // probably but in used JQuery version
      if (d.find('description').children().length > 0) {
        description = id;
      } else {
        description = d.find('description').text();
      }
      const enabled = d.find('enabled').text() === 'true';
      const publicKeys = d.find('publicKey').map((i, e) => window.$j(e).text()).get();
      return {id, description, enabled, publicKeys};
    });

    return {distributions, errors: null};
  });
}
