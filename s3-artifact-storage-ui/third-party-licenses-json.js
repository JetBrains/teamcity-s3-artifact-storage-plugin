module.exports = ({modules}) =>
    JSON.stringify(
        modules.map(({license, ...rest}) => ({
            ...rest,
            license: license.name,
            licenseUrl: license.url,
        })),
        null,
        // eslint-disable-next-line no-magic-numbers
        2,
    )
