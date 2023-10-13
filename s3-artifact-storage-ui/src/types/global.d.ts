export {};

declare global {
  function renderEditS3Storage(config: ConfigWrapper): void;

  interface Window {
    BS: {
      Encrypt: {
        encryptData: (value: string, publicKey: string) => string;
      };
      Util: {
        showHelp: (
          event: React.MouseEvent<HTMLAnchorElement, MouseEvent>,
          href: string,
          { width: number, height: number }
        ) => void;
      };
    };
    $j: JQueryStatic;
    __secretKey: string | null | undefined;
  }
}
