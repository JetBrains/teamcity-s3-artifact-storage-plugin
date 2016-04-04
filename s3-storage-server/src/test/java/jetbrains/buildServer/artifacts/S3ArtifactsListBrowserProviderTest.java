package jetbrains.buildServer.artifacts;

import jetbrains.buildServer.artifacts.tree.S3ArtifactsListBrowser;
import jetbrains.buildServer.artifacts.tree.S3ArtifactsListBrowserProvider;
import jetbrains.buildServer.serverSide.SBuild;
import jetbrains.buildServer.serverSide.artifacts.BuildArtifact;
import jetbrains.buildServer.serverSide.artifacts.BuildArtifactHolder;
import jetbrains.buildServer.serverSide.artifacts.BuildArtifacts;
import jetbrains.buildServer.serverSide.artifacts.BuildArtifactsViewMode;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.FileInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * Created by Nikita.Skvortsov
 * date: 08.02.2016.
 */
@Test
public class S3ArtifactsListBrowserProviderTest {

  private final S3ArtifactsListBrowserProvider browserProvider = new S3ArtifactsListBrowserProvider();
  private Mockery myCtx;
  private SBuild myBuild;
  private BuildArtifact myArtifact;

  @BeforeMethod
  public void setUp() throws Exception {
    myCtx = new Mockery();
    myBuild = myCtx.mock(SBuild.class);
    final BuildArtifacts buildArtifacts = myCtx.mock(BuildArtifacts.class);
    final BuildArtifactHolder holder = myCtx.mock(BuildArtifactHolder.class);
    myArtifact = myCtx.mock(BuildArtifact.class);

    myCtx.checking(new Expectations() {{
      allowing(myBuild).getArtifacts(with(any(BuildArtifactsViewMode.class))); will(returnValue(buildArtifacts));
      allowing(buildArtifacts).findArtifact(with(any(String.class))); will(returnValue(holder));
      allowing(holder).isAvailable(); will(returnValue(true));
      allowing(holder).getArtifact(); will(returnValue(myArtifact));
    }});
  }

  public void testEmptyFile() throws Exception {
    myCtx.checking(new Expectations(){{
      allowing(myArtifact).getInputStream(); will(returnValue(new FileInputStream("src/test/resources/empty_list/" + Constants.S3_ARTIFACTS_LIST)));
    }});

    assertThat(browserProvider.getOrCreateBrowser(myBuild)).isNull();
  }

  public void testSimpleFile() throws Exception {
    myCtx.checking(new Expectations(){{
      allowing(myArtifact).getInputStream(); will(returnValue(new FileInputStream("src/test/resources/simple_list/" + Constants.S3_ARTIFACTS_LIST)));
    }});

    final S3ArtifactsListBrowser browser = (S3ArtifactsListBrowser)browserProvider.getOrCreateBrowser(myBuild);
    assertThat(browser).isNotNull();

    assertThat(browser.getChildren("")).extracting("name", "fullName", "leaf").containsOnly(tuple("some", "some", false));
    assertThat(browser.getChildren("some")).extracting("name", "fullName", "leaf").containsOnly(tuple("path", "some/path", false));
    assertThat(browser.getChildren("some/path")).extracting("name", "fullName", "leaf").containsOnly(tuple("file.txt", "some/path/file.txt", true));
  }

  public void testBiggerFile() throws Exception {
    myCtx.checking(new Expectations(){{
      allowing(myArtifact).getInputStream(); will(returnValue(new FileInputStream("src/test/resources/bigger_list/" + Constants.S3_ARTIFACTS_LIST)));
    }});

    final S3ArtifactsListBrowser browser = (S3ArtifactsListBrowser)browserProvider.getOrCreateBrowser(myBuild);
    assertThat(browser).isNotNull();

    assertThat(browser.getChildren(".teamcity/properties")).hasSize(2);
    assertThat(browser.getChildren("surefire-reports")).extracting("name", "fullName", "leaf").containsOnly(
        tuple("my.guinea.pig.maven.AppTest.txt", "surefire-reports/my.guinea.pig.maven.AppTest.txt", true),
        tuple("TEST-my.guinea.pig.maven.AppTest.xml", "surefire-reports/TEST-my.guinea.pig.maven.AppTest.xml", true));
  }
}