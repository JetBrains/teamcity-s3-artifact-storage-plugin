package jetbrains.buildServer.artifacts.s3.cleanup;

import jetbrains.buildServer.util.CollectionsUtil;
import jetbrains.buildServer.util.IncludeExcludeRules;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.util.pathMatcher.AntPatternTreeMatcher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author vbedrosova
 */
public class PathPatternFilter {

  private static final String DEFAULT_INCLUDE_RULE = "**/*";
  private static final Collection<String> INCLUDE_ALL_PATTERNS = Arrays.asList(DEFAULT_INCLUDE_RULE, "+:**/*", "", "+.");

  @NotNull private final List<String> myIncludePatterns = new ArrayList<>();
  @NotNull private final List<String> myExcludePatterns = new ArrayList<>();

  public PathPatternFilter(@NotNull String patterns) {
    if (StringUtil.isEmptyOrSpaces(patterns)) {
      patterns = "+:" + DEFAULT_INCLUDE_RULE;
    }

    final IncludeExcludeRules rules = new IncludeExcludeRules(patterns);
    for (IncludeExcludeRules.Rule r : rules.getRules()) {
      if (r.isInclude()) {
        myIncludePatterns.add(r.getRule());
      } else {
        myExcludePatterns.add(r.getRule());
      }
    }
  }

  @NotNull
  public List<String> filterPaths(@NotNull List<String> paths) {
    if (isIncludeAll()) {
      return paths;
    }

    final Collection<PathNode> files = AntPatternTreeMatcher.scan(createPathTree(paths), myIncludePatterns, myExcludePatterns, AntPatternTreeMatcher.ScanOption.LEAFS_ONLY);
    return CollectionsUtil.convertCollection(files, PathNode::getPath);
  }

  private boolean isIncludeAll() {
    return myExcludePatterns.isEmpty() && myIncludePatterns.size() == 1 && INCLUDE_ALL_PATTERNS.contains(myIncludePatterns.get(0));
  }

  @NotNull
  PathNode createPathTree(@NotNull List<String> paths) {
    final List<String> pathsList = new ArrayList<>(paths);
    Collections.sort(pathsList);

    final PathNode root = new PathNode("");

    for (String path : pathsList) {
      PathNode parent = root;
      for (String part : path.split("/")) {
        final String parentPath = parent.getPath();
        parent = parent.child(parentPath.isEmpty() ? part : parentPath + "/" + part);
      }
    }

    return root;
  }

  static class PathNode implements jetbrains.buildServer.util.pathMatcher.PathNode<PathNode> {
    @NotNull private final String myPath;
    @Nullable private Set<PathNode> myChildren;

    public PathNode(@NotNull String path) {
      if (path.endsWith("/")) {
        path = path.substring(0, path.length() - 1);
      }
      if (path.startsWith("/")) {
        path = path.substring(1);
      }
      myPath = path;
      myChildren = null;
    }

    @NotNull
    @Override
    public String getName() {
      final int slashIndex = myPath.lastIndexOf("/");
      return slashIndex < 0 ? myPath : myPath.substring(slashIndex + 1);
    }

    @NotNull
    public String getPath() {
      return myPath;
    }

    @Nullable
    @Override
    public Iterable<PathNode> getChildren() {
      return myChildren;
    }

    @NotNull
    public PathNode child(@NotNull String path) {
      if (myChildren == null) {
        myChildren = new HashSet<>();
      }

      for (PathNode c : myChildren) {
        if (path.equals(c.getPath())) {
          return c;
        }
      }

      final PathNode child = new PathNode(path);
      myChildren.add(child);
      return child;
    }
  }

}
