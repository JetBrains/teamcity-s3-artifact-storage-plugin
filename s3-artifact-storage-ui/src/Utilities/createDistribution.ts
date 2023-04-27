import { ResponseErrors } from '@teamcity-cloud-integrations/react-ui-components/dist/types';

import { Config, IFormInput } from '../types';

import { FetchResourceIds } from '../App/appConstants';

import { serializeParameters } from './parametersUtils';
import { post } from './fetchHelper';
import { displayErrorsFromResponseIfAny } from './responseParser';
import { Distribution } from './fetchDistributions';
import { PublicKeyType } from './fetchPublicKeys';

type CreateDistributionResponse = {
  response: {
    downloadDistribution: Distribution;
    uploadDistribution: Distribution;
    publicKey: PublicKeyType;
    privateKey: string;
  } | null;
  errors: ResponseErrors | null;
};

export async function createDistribution(
  appProps: Config,
  allValues: IFormInput
): Promise<CreateDistributionResponse> {
  const parameters = serializeParameters(allValues, appProps);

  return await post(appProps.distributionPath, parameters).then((resp) => {
    const response = window.$j(resp);
    const errors = displayErrorsFromResponseIfAny(response);
    if (errors) {
      return { response: null, errors };
    }

    const downloadDistribution = response.find('downloadDistribution');
    if (downloadDistribution.length < 1) {
      return {
        response: null,
        errors: {
          [FetchResourceIds.BUCKETS]: {
            message: 'Select bucket before creating distribution',
          },
        },
      };
    }
    const downloadDistrId = downloadDistribution.find('id').text();
    const downloadDistrDescription = downloadDistribution
      .find('description')
      .text();

    const uploadDistribution = response.find('uploadDistribution');
    const uploadDistrId = uploadDistribution.find('id').text();
    const uploadDistrDescription = uploadDistribution
      .find('description')
      .text();

    const publicKeyid = response.find('publicKeyId').text();
    const publicKeyName = response.find('publicKeyName').text();

    const privateKey = response.find('privateKey').text();

    return {
      response: {
        downloadDistribution: {
          id: downloadDistrId,
          description: downloadDistrDescription,
          enabled: true,
          publicKeys: [publicKeyid],
        },
        uploadDistribution: {
          id: uploadDistrId,
          description: uploadDistrDescription,
          enabled: true,
          publicKeys: [publicKeyid],
        },
        publicKey: {
          id: publicKeyid,
          name: publicKeyName,
        },
        privateKey,
      },
      errors: null,
    };

    // $privateKeyNote.text('Key has been generated automatically').change();
  });
}
