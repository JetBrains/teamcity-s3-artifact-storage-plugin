const path = require('path');
const getWebpackConfig = require('@jetbrains/teamcity-api/getWebpackConfig');
const LicenseChecker = require('@jetbrains/ring-ui-license-checker');

function createLicenseChecker(filename) {
    return new LicenseChecker({
        format: require('./third-party-licenses-json'),
        filename,
        exclude: [/@jetbrains/],
        surviveLicenseErrors: true,
    });
}

const createConfig = getWebpackConfig({
    srcPath: path.join(__dirname, './src'),
    outputPath: path.resolve(__dirname, '../s3-artifact-storage-server/src/main/resources/buildServerResources'),
    entry: './src/index.tsx',
    useTypeScript: true
});

const config = createConfig();
config.plugins.push(createLicenseChecker('../../../../../s3-artifact-storage-ui/js-related-libraries.json'))

module.exports = config;
