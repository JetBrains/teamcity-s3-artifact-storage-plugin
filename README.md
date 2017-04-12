# TeamCity S3 Artifact Storage Plugin

<a href="https://teamcity.jetbrains.com/viewType.html?buildTypeId=TeamCityPluginsByJetBrains_AwsS3ArtifactStorage_TeamCityTrunk&tab=buildTypeHistoryList&guest=1"><img src="https://teamcity.jetbrains.com/app/rest/builds/buildType:(id:TeamCityPluginsByJetBrains_AwsS3ArtifactStorage_TeamCityTrunk)/statusIcon.svg" alt=""/></a>

This plugin allows replacing the TeamCity built-in artifacts storage with [AWS S3](https://aws.amazon.com/s3/). The artifacts storage can be changed at the project level. After changing the storage, new artifacts produced by the builds of this project will be published to the (specified) AWS S3 bucket. Besides publishing, the plugin also implements resolving of artifact dependencies and clean-up of build artifacts.

# State

Baseline functionality finished. Feedback wanted.
The plugin  is not ready to be used in produciton and further plugin updates may break backward compatibility (regarding settings, data storage, etc.)

# Compatibility

The plugin is compatible with [TeamCity](https://www.jetbrains.com/teamcity/download/) 2017.1 and greater

# Features

When installed and configured, the plugin:
* allows uploading artifacts to Amazon S3
* allows downloading and removing artifacts from Amazon S3
* handles resolution of artifact dependencies
* handles clean-up of artifacts 
* displays artifacts located in Amazon S3 in the TeamCity web UI.

# Building 

Issue the `mvn package` command from the root project to build your plugin. The resulting \<artifactId>.zip package will be placed in the 'target' directory. 

# Download

| Download | Compatibility |
|----------|---------------|
|[Download](https://teamcity.jetbrains.com/guestAuth/app/rest/builds/buildType:TeamCityPluginsByJetBrains_AwsS3ArtifactStorage_TeamCityTrunk,tags:deploy/artifacts/content/s3-artifact-storage.zip)| 2017.1 |


# Installing

See [instructions](https://confluence.jetbrains.com/display/TCD10/Installing+Additional+Plugins) in TeamCity documentation.

# Configuring 

The plugin adds the Artifacts Storage tab to the Project Settings page in the TeamCity Web UI. 
The tab lists the built-in TeamCity artifacts storage displayed by default and marked as active.

To configure Amazon S3 storage for TeamCity artifacts, perform the following:
1. Select S3 Storage as the storage type.
2. Provide an optional name for your storage.
3. Select an AWS region.
4. Provide your AWS Security Credentials.
5. Specify an existing S3 bucket to store artifacts.
6. Save your settings.
7. The configured S3 storage will appear on the Artifacts storage page. Make it active using the corresponding link.

Now the artifacts of this project, its subprojects, and build configurations will be stored in the configured storage.

# License

Apache License 2.0

