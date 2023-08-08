import { utils } from '@jetbrains/teamcity-api';

const request = async (
  url: string,
  params: { [k: string]: string } | null,
  method = 'GET'
) => {
  let body = null;
  if (params) {
    body = new FormData();

    for (const key in params) {
      const value = params[key];
      body.append(key, value);
    }
  }

  return await utils.requestText(
    url.replace(/^\/+/, ''),
    {
      method,
      body,
    },
    true
  );
};

export const post = async (
  url: string,
  params: { [k: string]: string } | null = null
) => await request(url, params, 'POST');
