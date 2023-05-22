import { ResponseErrors } from '@jetbrains-internal/tcci-react-ui-components/dist/types';

export function parseResourceListFromResponse(
  response: JQuery<any>,
  selector: string
) {
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
      result[errors[i].id] = { message: errors[i].textContent! };
    }
    return result;
  }
}

export function parseErrorsFromResponse(response: Document) {
  const errors = response.querySelectorAll('errors > error');
  if (!errors.length) {
    return null;
  } else {
    const result: ResponseErrors = {};
    errors.forEach(
      (elem) => (result[elem.id] = { message: elem.textContent! })
    );
    return result;
  }
}

export function parseResponse(response: Document, selector: string) {
  const result: Element[] = [];
  response.querySelectorAll(selector).forEach((elem) => result.push(elem));

  return result;
}
