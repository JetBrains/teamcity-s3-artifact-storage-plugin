package jetbrains.buildServer.artifacts.tree;

import jetbrains.buildServer.Build;
import jetbrains.buildServer.web.util.lazytree.LazyTreeElementRenderer;
import jetbrains.buildServer.web.util.lazytree.TreeRendererProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Created by Nikita.Skvortsov
 * date: 17.02.2016.
 */
public class S3ElementRendererProvider implements TreeRendererProvider {
  @Nullable
  @Override
  public LazyTreeElementRenderer getOrCreateArtifactRenderer(@NotNull Build build) {
    return new S3ElementRenderer();
  }
}
