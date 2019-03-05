package jetbrains.buildServer.artifacts.s3.cleanup;

import jetbrains.buildServer.util.CollectionsUtil;
import jetbrains.buildServer.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.testng.annotations.Test;

import java.util.*;

import static org.testng.Assert.*;

/**
 * @author vbedrosova
 */
@Test
public class PathPatternFilterTest {

  @Test
  public void path_tree() {
    final PathPatternFilter.Node root = create("+:**/*").createPathTree(Arrays.asList("some/path/file", "some/path/another/file", "another/path"));
    assertEquals(root.getName(), "");
    assertEquals(root.getPath(), "");

    final List<PathPatternFilter.Node> rootChildren = toList(root.getChildren());
    assertEquals(rootChildren.size(), 2);

    final PathPatternFilter.Node some = getChild("some", rootChildren);
    assertNotNull(some);
    assertEquals(some.getName(), "some");
    assertEquals(some.getPath(), "some");

    final List<PathPatternFilter.Node> someChildren = toList(some.getChildren());
    assertEquals(someChildren.size(), 1);

    final PathPatternFilter.Node somePath = getChild("some/path", someChildren);
    assertNotNull(somePath);
    assertEquals(somePath.getName(), "path");
    assertEquals(somePath.getPath(), "some/path");

    final List<PathPatternFilter.Node> somePathChildren = toList(somePath.getChildren());
    assertEquals(somePathChildren.size(), 2);

    final PathPatternFilter.Node somePathAnother = getChild("some/path/another", somePathChildren);
    assertNotNull(somePathAnother);
    assertEquals(somePathAnother.getName(), "another");
    assertEquals(somePathAnother.getPath(), "some/path/another");

    final List<PathPatternFilter.Node> somePathAnotherChildren = toList(somePathAnother.getChildren());
    assertEquals(somePathAnotherChildren.size(), 1);

    final PathPatternFilter.Node somePathAnotherFile = getChild("some/path/another/file", somePathAnotherChildren);
    assertNotNull(somePathAnotherFile);
    assertEquals(somePathAnotherFile.getName(), "file");
    assertEquals(somePathAnotherFile.getPath(), "some/path/another/file");
    assertNull(somePathAnotherFile.getChildren());

    final PathPatternFilter.Node somePathFile = getChild("some/path/file", somePathChildren);
    assertNotNull(somePathFile);
    assertEquals(somePathFile.getName(), "file");
    assertEquals(somePathFile.getPath(), "some/path/file");
    assertNull(somePathFile.getChildren());

    final PathPatternFilter.Node another = getChild("another", rootChildren);
    assertNotNull(another);
    assertEquals(another.getName(), "another");
    assertEquals(another.getPath(), "another");

    final List<PathPatternFilter.Node> anotherChildren = toList(another.getChildren());
    assertEquals(anotherChildren.size(), 1);

    final PathPatternFilter.Node anotherPath = getChild("another/path", anotherChildren);
    assertNotNull(anotherPath);
    assertEquals(anotherPath.getName(), "path");
    assertEquals(anotherPath.getPath(), "another/path");
    assertNull(anotherPath.getChildren());
  }

  @Test
  public void empty_pattern() {
    final List<String> filtered = create(StringUtil.EMPTY).filterPaths(Arrays.asList("some/path", "another/path", "path"));
    assertEquals(filtered.size(), 3);
    assertContains(filtered, "some/path", "another/path", "path");
  }

  @Test
  public void include_all() {
    final List<String> filtered = create("**/*").filterPaths(Arrays.asList("some/path/file", "some/path/another/file", "another/path"));
    assertEquals(filtered.size(), 3);
    assertContains(filtered, "some/path/file", "some/path/another/file", "another/path");
  }

  @Test
  public void include_file() {
    final List<String> filtered = create("some/path/file").filterPaths(Arrays.asList("some/path/file", "some/path/another/file", "another/path"));
    assertEquals(filtered.size(), 1);
    assertContains(filtered, "some/path/file");
  }

  @Test
  public void include_folder() {
    assertTrue(create("some/path").filterPaths(Arrays.asList("some/path/file", "some/path/another/file", "another/path")).isEmpty());
  }

  @Test
  public void include_folder_content() {
    final List<String> filtered = create("some/path/*").filterPaths(Arrays.asList("some/path/file", "some/path/another/file", "another/path"));
    assertEquals(filtered.size(), 1);
    assertContains(filtered, "some/path/file");
  }

  @Test
  public void include_folder_content_recursive1() {
    final List<String> filtered = create("some/path/**").filterPaths(Arrays.asList("some/path/file", "some/path/another/file", "another/path"));
    assertEquals(filtered.size(), 2);
    assertContains(filtered, "some/path/file", "some/path/another/file");
  }

  @Test
  public void include_folder_content_recursive2() {
    final List<String> filtered = create("some/path/**/*").filterPaths(Arrays.asList("some/path/file", "some/path/another/file", "another/path"));
    assertEquals(filtered.size(), 2);
    assertContains(filtered, "some/path/file", "some/path/another/file");
  }

  @Test
  public void exclude_file() {
    final List<String> filtered = create("+:**/*\n-:some/path/file").filterPaths(Arrays.asList("some/path/file", "some/path/another/file", "another/path"));
    assertEquals(filtered.size(), 2);
    assertContains(filtered, "some/path/another/file", "another/path");
  }

  @Test
  public void exclude_folder() {
    final List<String> filtered = create("+:**/*\n-:some/path").filterPaths(Arrays.asList("some/path/file", "some/path/another/file", "another/path"));
    assertEquals(filtered.size(), 3);
    assertContains(filtered, "some/path/file", "some/path/another/file", "another/path");
  }

  @Test
  public void exclude_folder_content() {
    final List<String> filtered = create("+:**/*\n-:some/path/*").filterPaths(Arrays.asList("some/path/file", "some/path/another/file", "another/path"));
    assertEquals(filtered.size(), 2);
    assertContains(filtered,  "some/path/another/file", "another/path");
  }

  @Test
  public void exclude_folder_content_recursive1() {
    final List<String> filtered = create("+:**/*\n-:some/path/**").filterPaths(Arrays.asList("some/path/file", "some/path/another/file", "another/path"));
    assertEquals(filtered.size(), 1);
    assertContains(filtered,  "another/path");
  }

  @Test
  public void exclude_folder_content_recursive2() {
    final List<String> filtered = create("+:**/*\n-:some/path/**/*").filterPaths(Arrays.asList("some/path/file", "some/path/another/file", "another/path"));
    assertEquals(filtered.size(), 1);
    assertContains(filtered,  "another/path");
  }

  @NotNull
  private PathPatternFilter create(@NotNull String pattern) {
    return new PathPatternFilter(pattern);
  }

  @NotNull
  private List<PathPatternFilter.Node> toList(@Nullable Iterable<PathPatternFilter.Node> nodes) {
    if (nodes == null) {
      return Collections.emptyList();
    }

    final List<PathPatternFilter.Node> result = new ArrayList<>();
    for (PathPatternFilter.Node node : nodes) {
      result.add(node);
    }
    return result;
  }

  @Nullable
  private PathPatternFilter.Node getChild(@NotNull String path, @NotNull List<PathPatternFilter.Node> children) {
    return CollectionsUtil.findFirst(children, data -> path.equals(data.getPath()));
  }

  private static <T> void assertContains(@NotNull final Collection<T> collection, final T... items) {
    for (T item : items) {
      assertTrue(collection.contains(item), "Should contains " + item);
    }
  }

}
