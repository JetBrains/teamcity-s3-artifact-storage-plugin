import { React, ReactDOM } from '@jetbrains/teamcity-api';

import App from './App/App';
import { Config } from './types';

global.renderEditS3Storage = (config: Config) => {
  ReactDOM.render(
    <App config={config} />,
    document.getElementById('edit-s3-storage-root')
  );
};
