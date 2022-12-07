export type ResponseError = {
    message: string,
}

export type ResponseErrors = { [key: string]: ResponseError }

export function parseResourceListFromResponse(response: JQuery<any>, selector: string) {
  const list: HTMLElement[] = [];
  const elems: JQuery = response.find(selector);
  for (let i = 0; i < elems.length; ++i) {
    const e: HTMLElement = elems[i];
    list.push(e);
  }
  return list;
}

export function displayErrorsFromResponseIfAny(response: JQuery<any>) {
  const errors = parseErrors(response);
  if (!errors) {
    return null;
  }
  return errors;
}

export function parseErrors(response: JQuery<any>) {
  const errors = response.find('errors:eq(0) error');
  if (!errors.length) {
    return null;
  } else {
    const result: ResponseErrors = {};
    for (let i = 0; i < errors.length; ++i) {
      result[errors[i].id] = {message: errors[i].textContent!};
    }
    return result;
  }
}
