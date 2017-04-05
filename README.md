# TeamCity S3 Storage Plugin

TeamCity S3 Storage Plugin is an impelementaion of external storage for TeamCity [build artifacts](https://confluence.jetbrains.com/display/TCDL/Build+Artifact) in Amazon S3.

# Compatibility

The plugin is compatible with [TeamCity](https://www.jetbrains.com/teamcity/download/) 2017.1 and greater

# Features

When installed and configured, the plugin:
* allows uploading artifacts to Amazon S3
* downloading and removing artifacts from Amazon S3
* handles resolution of artifact dependencies
* handles clean-up of artifacts 
* displays artifacts located in Amazon S3 in the TeamCity web UI.

# Implementing 
 This project contains 3 modules: '<artifactId>-server', '<artifactId>-agent' and '<artifactId>-common'. They will contain code for server and agent parts of your plugin and a common part, available for both (agent and server). When implementing components for server and agent parts, do not forget to update spring context files under 'main/resources/META-INF'. Otherwise your compoment may be not loaded. See TeamCity documentation for details on plugin development.

# Building 
 Issue the `mvn package` command from the root project to build your plugin. The resulting  <artifactId>.zip package will be placed in the 'target' directory. 
 
# Installing

To install the plugin, put the plugin zip archive into 'plugins' directory under [TeamCity data directory](https://confluence.jetbrains.com/display/TCDL/TeamCity+Data+Directory). 
If you only changed the agent-side code of your plugin, the upgrade will be perfomed 'on the fly' (agents will upgrade when idle). 
If the common or server-side code has changed, restart the server.

# Configuring 

The plugin adds the Artifacts Storage tab to the Project Settings page in the TeamCity Web UI. 
The tab lists the internal TeamCity artifacts storage is displayed by default and is marked as active.

To configure Amazon S3 storage for TeamCity artifacts, perform the following:
1. Select S3 Storage as the storage type.
2. Provide an optional name for your storage.
3. Select an AWS region.
4. Provide your AWS Security Credentials.
5. Specify an existing S3 bucker to store artifacts.
6. Save your settings.
7. The configured S3 storage will appear on the Artifacts storage page. Make it active using the corresponding link.

Now the artifacts of this project, its subprojects, and build configurations will be stored in the configured storage.
