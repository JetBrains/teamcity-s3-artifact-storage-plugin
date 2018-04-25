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
    final PathPatternFilter.PathNode root = create("+:**/*").createPathTree(Arrays.asList("some/path/file", "some/path/another/file", "another/path"));
    assertEquals("", root.getName());
    assertEquals("", root.getPath());

    final List<PathPatternFilter.PathNode> rootChildren = toList(root.getChildren());
    assertEquals(2, rootChildren.size());

    final PathPatternFilter.PathNode some = getChild("some", rootChildren);
    assertNotNull(some);
    assertEquals("some", some.getName());
    assertEquals("some", some.getPath());

    final List<PathPatternFilter.PathNode> someChildren = toList(some.getChildren());
    assertEquals(1, someChildren.size());

    final PathPatternFilter.PathNode somePath = getChild("some/path", someChildren);
    assertNotNull(somePath);
    assertEquals("path", somePath.getName());
    assertEquals("some/path", somePath.getPath());

    final List<PathPatternFilter.PathNode> somePathChildren = toList(somePath.getChildren());
    assertEquals(2, somePathChildren.size());

    final PathPatternFilter.PathNode somePathAnother = getChild("some/path/another", somePathChildren);
    assertNotNull(somePathAnother);
    assertEquals("another", somePathAnother.getName());
    assertEquals("some/path/another", somePathAnother.getPath());

    final List<PathPatternFilter.PathNode> somePathAnotherChildren = toList(somePathAnother.getChildren());
    assertEquals(1, somePathAnotherChildren.size());

    final PathPatternFilter.PathNode somePathAnotherFile = getChild("some/path/another/file", somePathAnotherChildren);
    assertNotNull(somePathAnotherFile);
    assertEquals("file", somePathAnotherFile.getName());
    assertEquals("some/path/another/file", somePathAnotherFile.getPath());
    assertNull(somePathAnotherFile.getChildren());

    final PathPatternFilter.PathNode somePathFile = getChild("some/path/file", somePathChildren);
    assertNotNull(somePathFile);
    assertEquals("file", somePathFile.getName());
    assertEquals("some/path/file", somePathFile.getPath());
    assertNull(somePathFile.getChildren());

    final PathPatternFilter.PathNode another = getChild("another", rootChildren);
    assertNotNull(another);
    assertEquals("another", another.getName());
    assertEquals("another", another.getPath());

    final List<PathPatternFilter.PathNode> anotherChildren = toList(another.getChildren());
    assertEquals(1, anotherChildren.size());

    final PathPatternFilter.PathNode anotherPath = getChild("another/path", anotherChildren);
    assertNotNull(anotherPath);
    assertEquals("path", anotherPath.getName());
    assertEquals("another/path", anotherPath.getPath());
    assertNull(anotherPath.getChildren());
  }

  @Test
  public void empty_pattern() {
    final List<String> filtered = create(StringUtil.EMPTY).filterPaths(Arrays.asList("some/path", "another/path", "path"));
    assertEquals(3, filtered.size());
    assertContains(filtered, "some/path", "another/path", "path");
  }

  @Test
  public void include_all() {
    final List<String> filtered = create("**/*").filterPaths(Arrays.asList("some/path/file", "some/path/another/file", "another/path"));
    assertEquals(3, filtered.size());
    assertContains(filtered, "some/path/file", "some/path/another/file", "another/path");
  }

  @Test
  public void include_file() {
    final List<String> filtered = create("some/path/file").filterPaths(Arrays.asList("some/path/file", "some/path/another/file", "another/path"));
    assertEquals(1, filtered.size());
    assertContains(filtered, "some/path/file");
  }

  @Test
  public void include_folder() {
    assertTrue(create("some/path").filterPaths(Arrays.asList("some/path/file", "some/path/another/file", "another/path")).isEmpty());
  }

  @Test
  public void include_folder_content() {
    final List<String> filtered = create("some/path/*").filterPaths(Arrays.asList("some/path/file", "some/path/another/file", "another/path"));
    assertEquals(1, filtered.size());
    assertContains(filtered, "some/path/file");
  }

  @Test
  public void include_folder_content_recursive1() {
    final List<String> filtered = create("some/path/**").filterPaths(Arrays.asList("some/path/file", "some/path/another/file", "another/path"));
    assertEquals(2, filtered.size());
    assertContains(filtered, "some/path/file", "some/path/another/file");
  }

  @Test
  public void include_folder_content_recursive2() {
    final List<String> filtered = create("some/path/**/*").filterPaths(Arrays.asList("some/path/file", "some/path/another/file", "another/path"));
    assertEquals(2, filtered.size());
    assertContains(filtered, "some/path/file", "some/path/another/file");
  }

  @Test
  public void exclude_file() {
    final List<String> filtered = create("+:**/*\n-:some/path/file").filterPaths(Arrays.asList("some/path/file", "some/path/another/file", "another/path"));
    assertEquals(2, filtered.size());
    assertContains(filtered, "some/path/another/file", "another/path");
  }

  @Test
  public void exclude_folder() {
    final List<String> filtered = create("+:**/*\n-:some/path").filterPaths(Arrays.asList("some/path/file", "some/path/another/file", "another/path"));
    assertEquals(3, filtered.size());
    assertContains(filtered, "some/path/file", "some/path/another/file", "another/path");
  }

  @Test
  public void exclude_folder_content() {
    final List<String> filtered = create("+:**/*\n-:some/path/*").filterPaths(Arrays.asList("some/path/file", "some/path/another/file", "another/path"));
    assertEquals(2, filtered.size());
    assertContains(filtered,  "some/path/another/file", "another/path");
  }

  @Test
  public void exclude_folder_content_recursive1() {
    final List<String> filtered = create("+:**/*\n-:some/path/**").filterPaths(Arrays.asList("some/path/file", "some/path/another/file", "another/path"));
    assertEquals(1, filtered.size());
    assertContains(filtered,  "another/path");
  }

  @Test
  public void exclude_folder_content_recursive2() {
    final List<String> filtered = create("+:**/*\n-:some/path/**/*").filterPaths(Arrays.asList("some/path/file", "some/path/another/file", "another/path"));
    assertEquals(1, filtered.size());
    assertContains(filtered,  "another/path");
  }

  @NotNull
  private PathPatternFilter create(@NotNull String pattern) {
    return new PathPatternFilter(pattern);
  }

  @NotNull
  private List<PathPatternFilter.PathNode> toList(@Nullable Iterable<PathPatternFilter.PathNode> nodes) {
    if (nodes == null) return Collections.emptyList();

    final ArrayList<PathPatternFilter.PathNode> result = new ArrayList<>();
    final Iterator<PathPatternFilter.PathNode> it = nodes.iterator();
    while (it.hasNext()) result.add(it.next());
    return result;
  }

  @Nullable
  private PathPatternFilter.PathNode getChild(@NotNull String path, @NotNull List<PathPatternFilter.PathNode> children) {
    return CollectionsUtil.findFirst(children, data -> path.equals(data.getPath()));
  }

  private static <T> void assertContains(@NotNull final Collection<T> collection, final T... items) {
    for (T item : items) {
      assertTrue(collection.contains(item), "Should contains " + item);
    }
  }
}
