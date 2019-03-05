package jetbrains.buildServer.artifacts.s3.cleanup;

import com.google.common.annotations.VisibleForTesting;
import java.util.*;
import jetbrains.buildServer.util.CollectionsUtil;
import jetbrains.buildServer.util.IncludeExcludeRules;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.util.pathMatcher.AntPatternTreeMatcher;
import jetbrains.buildServer.util.pathMatcher.PathNodeEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author vbedrosova
 */
public class PathPatternFilter {

  private static final String DEFAULT_INCLUDE_RULE = "**/*";
  private static final Collection<String> INCLUDE_ALL_PATTERNS = Arrays.asList(DEFAULT_INCLUDE_RULE, "+:**/*", "", "+.");

  @NotNull private final List<String> myIncludePatterns = new ArrayList<>();
  @NotNull private final List<String> myExcludePatterns = new ArrayList<>();

  public PathPatternFilter(@NotNull String patterns) {
    IncludeExcludeRules rules = StringUtil.isEmptyOrSpaces(patterns)
      ? new IncludeExcludeRules("+:" + DEFAULT_INCLUDE_RULE)
      : new IncludeExcludeRules(patterns);

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

    final Collection<Node> files = AntPatternTreeMatcher.scan(createPathTree(paths), myIncludePatterns, myExcludePatterns, AntPatternTreeMatcher.ScanOption.LEAFS_ONLY);
    return CollectionsUtil.convertCollection(files, Node::getPath);
  }

  private boolean isIncludeAll() {
    return myExcludePatterns.isEmpty() && myIncludePatterns.size() == 1 && INCLUDE_ALL_PATTERNS.contains(myIncludePatterns.get(0));
  }

  @NotNull
  Node createPathTree(@NotNull List<String> paths) {
    final List<String> pathsList = new ArrayList<>(paths);
    Collections.sort(pathsList);

    final Node root = new Node("", null);

    for (String path : pathsList) {
      Node parent = root;
      for (String part : path.split("/")) {
        final String parentPath = parent.getPath();
        parent = parent.child(parentPath.isEmpty() ? part : parentPath + "/" + part);
      }
    }

    return root;
  }

  @VisibleForTesting
  static class Node implements PathNodeEx<Node> {
    @NotNull
    private final String myPath;
    @Nullable
    private final Node myParent;
    @Nullable
    private Map<String, Node> myChildrenByName;

    Node(@NotNull String path, @Nullable Node parent) {
      myParent = parent;
      boolean startsWithSlash = path.startsWith("/");
      boolean endsWithSlash = path.endsWith("/");
      if (startsWithSlash && endsWithSlash && path.length() >= 2) {
        myPath = path.substring(1, path.length() - 1);
      } else if (startsWithSlash) {
        myPath = path.substring(1);
      } else if (endsWithSlash) {
        myPath = path.substring(0, path.length() - 1);
      } else {
        myPath = path;
      }
      myChildrenByName = null;
    }

    @NotNull
    @Override
    public String getName() {
      return getName(myPath);
    }

    @Nullable
    @Override
    public Iterable<Node> getChildren() {
      return myChildrenByName != null ? myChildrenByName.values() : null;
    }

    @Nullable
    @Override
    public Node getParent() {
      return myParent;
    }

    @Nullable
    @Override
    public Node getChild(@NotNull String childName) {
      return myChildrenByName != null ? myChildrenByName.get(childName) : null;
    }

    @NotNull
    public String getPath() {
      return myPath;
    }

    @NotNull
    public Node child(@NotNull String path) {
      if (myChildrenByName == null) {
        myChildrenByName = new HashMap<>();
      }

      for (Node c : myChildrenByName.values()) {
        if (path.equals(c.getPath())) {
          return c;
        }
      }

      final Node child = new Node(path, this);
      myChildrenByName.put(getName(path), child);
      return child;
    }

    private String getName(@NotNull String path) {
      final int slashIndex = path.lastIndexOf('/');
      return slashIndex < 0 ? path : path.substring(slashIndex + 1);
    }
  }

}
