# TeamCity S3 Artifact Storage Plugin

[![official project](http://jb.gg/badges/official.svg)](https://confluence.jetbrains.com/display/ALL/JetBrains+on+GitHub) [![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

This plugin allows replacing the TeamCity built-in artifacts storage with [AWS S3](https://aws.amazon.com/s3/). The artifacts storage can be changed at the project level. After changing the storage, new artifacts produced by the builds of this project will be published to the (specified) AWS S3 bucket. Besides publishing, the plugin also implements resolving of artifact dependencies and clean-up of build artifacts.

# State

Baseline functionality finished. Feedback wanted.

# Compatibility

The plugin is compatible with [TeamCity](https://www.jetbrains.com/teamcity/download/) 2017.1 and greater

# Features

When installed and configured, the plugin:
* allows uploading artifacts to Amazon S3
* allows downloading and removing artifacts from Amazon S3
* handles resolution of artifact dependencies
* handles clean-up of artifacts 
* displays artifacts located in Amazon S3 in the TeamCity web UI.

# Download

You can [download the plugin](https://plugins.jetbrains.com/plugin/9623-s3-artifact-storage) and install it as [an additional TeamCity plugin](https://www.jetbrains.com/help/teamcity/?Installing+Additional+Plugins). The latest plugin builds:

| Branch          | Status                                                                                                                                                                                                                                                                                                      | Download                                                                                                                                                                    | TeamCity        |
|-----------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------|
| master          | <a href="https://teamcity.jetbrains.com/viewType.html?buildTypeId=TeamCityPluginsByJetBrains_AwsS3ArtifactStorage_TeamCityTrunk&guest=1"><img src="https://teamcity.jetbrains.com/app/rest/builds/buildType:(id:TeamCityPluginsByJetBrains_AwsS3ArtifactStorage_TeamCity20181)/statusIcon.svg" alt=""/></a> | [Download](https://teamcity.jetbrains.com/repository/download/TeamCityPluginsByJetBrains_AwsS3ArtifactStorage_TeamCityTrunk/.lastSuccessful/s3-artifact-storage.zip?guest=1)| 2019.2-SNAPSHOT |
| Jaipur-2018.1.x | <a href="https://teamcity.jetbrains.com/viewType.html?buildTypeId=TeamCityPluginsByJetBrains_AwsS3ArtifactStorage_TeamCity20181&guest=1"><img src="https://teamcity.jetbrains.com/app/rest/builds/buildType:(id:TeamCityPluginsByJetBrains_AwsS3ArtifactStorage_TeamCity20181)/statusIcon.svg" alt=""/></a> | [Download](https://teamcity.jetbrains.com/repository/download/TeamCityPluginsByJetBrains_AwsS3ArtifactStorage_TeamCity20181/.lastSuccessful/s3-artifact-storage.zip?guest=1)| 2018.1.x        |
| Indore-2017.2.x | <a href="https://teamcity.jetbrains.com/viewType.html?buildTypeId=TeamCityPluginsByJetBrains_AwsS3ArtifactStorage_TeamCity20172&guest=1"><img src="https://teamcity.jetbrains.com/app/rest/builds/buildType:(id:TeamCityPluginsByJetBrains_AwsS3ArtifactStorage_TeamCity20172)/statusIcon.svg" alt=""/></a> | [Download](https://teamcity.jetbrains.com/repository/download/TeamCityPluginsByJetBrains_AwsS3ArtifactStorage_TeamCity20172/.lastSuccessful/s3-artifact-storage.zip?guest=1)| 2017.2.x        |
| Indore-2017.1.x | <a href="https://teamcity.jetbrains.com/viewType.html?buildTypeId=TeamCityPluginsByJetBrains_AwsS3ArtifactStorage_TeamCity20171&guest=1"><img src="https://teamcity.jetbrains.com/app/rest/builds/buildType:(id:TeamCityPluginsByJetBrains_AwsS3ArtifactStorage_TeamCity20171)/statusIcon.svg" alt=""/></a> | [Download](https://teamcity.jetbrains.com/repository/download/TeamCityPluginsByJetBrains_AwsS3ArtifactStorage_TeamCity20171/.lastSuccessful/s3-artifact-storage.zip?guest=1)| 2017.1.1+       |

# Installing

See [instructions](https://www.jetbrains.com/help/teamcity/?Installing+Additional+Plugins) in TeamCity documentation.

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

## Permissions

The plugin requires to have the following S3 permissions:

* build agent: `ListBucket`, `PutObject`
* server: `DeleteObject`, `ListAllMyBuckets`, `GetBucketLocation`, `GetObject`

**Note**: When pre-signed URLs option is enabled you need to grant required build agent permissions to the TeamCity server machine.

# Known issues

## Bad Request (400) from S3 when downloading artifact from TeamCity in "ant get" task using basic http auth scheme (httpAuth prefix in URL)

Error message returned from AWS: 
*Only one auth mechanism allowed; only the X-Amz-Algorithm query parameter, Signature query string parameter or the Authorization header should be specified*

Workaround: use pure 'curl -L'

## Build agents fails to fetch artifacts from S3
*Failed to resolve artifact dependency X: Failed to download file 'X': Failed to download https://teamcity/X.tar]: Illegal status [403] while downloading https://s3.X.amazonaws.com/bucket/X?...: Forbidden (jetbrains.buildServer.artifacts.impl.SourcePathAwareResolvingFailedException)*

This can occur if the build agent is not able to fetch the artifact from the presigned URL generated by TeamCity within the time limit which is default 60s.

This value can be modified by setting a [internal TeamCity server property](https://www.jetbrains.com/help/teamcity/?Configuring+TeamCity+Server+Startup+Properties) `storage.s3.url.expiration.time.seconds`.

# Building 

To build the plugin locally run the following command in the plugin root directory:
```
> gradle build
```

The plugin artifact will be produced in the following location `s3-artifact-storage-server/build/distributions/s3-artifact-storage.zip` and could be installed as [an external TeamCity plugin](https://www.jetbrains.com/help/teamcity/?Installing+Additional+Plugins).

Gradle property `shouldIncludeMigrationTool` exists for internal builds and allows to include Artifact migration tool as part of plugin distribution. 

Plugin can be build and can function without including this tool. 

# Reporting issues

Please report issues to our [YouTrack](https://youtrack.jetbrains.com/newIssue?project=TW&summary=%5BS3%20Storage%5D%20Issue%20Summary&description=Steps%20to%20reproduce%3A%0A1.%0A2.%0A...%0A%0AExpected%20Behaviour%3A%0A...%0A%0AActual%20Behaviour%3A%0A...&c=Subsystem%20plugins%3A%20other&c=tag%20S3%20Artifacts%20Storage).
