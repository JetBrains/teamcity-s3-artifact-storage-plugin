package jetbrains.buildServer.artifacts.s3.transfer.settings;

public class ArtifactTransferConstants {
  public static final String ACCESS_TOKEN = "teamcity.internal.storage.transfer.teamcity.access.token";
  public static final String PROCESSING_THREAD_COUNT = "teamcity.internal.storage.transfer.processing.threadCount";
  public static final int DEFAULT_PROCESSING_THREAD_COUNT = 4;
  public static final String BUILD_PROCESSING_TIMEOUT = "teamcity.internal.storage.transfer.processing.build.timeout.sec";
  public static final int DEFAULT_BUILD_PROCESSING_TIMEOUT = 3600;
}
