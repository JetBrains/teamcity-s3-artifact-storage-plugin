
 # TeamCity AWS S3 Artifact Storage Plugin

TeamCity AWS S3 Artifact Storage Plugin is an impelementaion of external storage for TeamCity [build artifacts](https://confluence.jetbrains.com/display/TCDL/Build+Artifact) in Amazon S3.

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
 Issue 'mvn package' command from the root project to build your plugin. Resulting package <artifactId>.zip will be placed in 'target' directory. 
 
# Installing

To install the plugin, put the plugin zip archive into 'plugins' directory under [TeamCity data directory] (https://confluence.jetbrains.com/display/TCDL/TeamCity+Data+Directory). 
If you only changed the agent-side code of your plugin, the upgrade will be perfomed 'on the fly' (agents will upgrade when idle). 
If the common or server-side code has changed, restart the server.

# Configuring 

The plugin adds the Artifacts Storage tab to the Project Settings page in the TeamCity Web UI. 
The tab lists the internal TeamCity artifacts storage is displayed by default and is marked as active.

To configure Amazon S3 storage for TeamCity artifacts, perform the following:
- select S3 Storage as the storage type
- provide an optional name for your storage
- select an AWS region
- provide your AWS Security Credentials
- specify an existing S3 bucket to store artifacts
- save your settings

The configured S3 storage will appear on the Artifacts storage page. Make it active using the corresponding link.

Now the artifacts of this project, its subprojects and build configurations will be stored in the configured storage.
