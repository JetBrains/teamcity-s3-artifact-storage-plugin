import {useCallback, useEffect} from 'react';

interface OwnProps {
  halt: boolean,
  selector: string
}

export default function useJspContainer({halt, selector}: OwnProps) {
  const collapseOldUi = useCallback(() => {
    const allWithClass: HTMLElement[] = Array.from(
      document.querySelectorAll(selector)
    );
    allWithClass.forEach(element => {
      element.setAttribute('style', 'visibility: collapse');
    });
    const button: HTMLElement | null = document.getElementById('saveButtons');
    button?.setAttribute('style', 'visibility: collapse');
  }, [selector]);

  const displayOldUi = useCallback(() => {
    const allWithClass = Array.from(
      document.querySelectorAll(selector)
    );
    allWithClass.forEach(element => {
      element.removeAttribute('style');
    });
    const button: HTMLElement | null = document.getElementById('saveButtons');
    button?.removeAttribute('style');
  }, [selector]);

  return useEffect(() => {
    !halt && collapseOldUi();

    return () => {
      displayOldUi();
    };
  }, [collapseOldUi, displayOldUi, halt]);
}
