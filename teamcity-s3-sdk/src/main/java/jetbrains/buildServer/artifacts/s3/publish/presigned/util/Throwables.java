package jetbrains.buildServer.artifacts.s3.publish.presigned.util;

// utility class
// TODO: merge it with jetbrains.buildServer.util.ExceptionUtil
public class Throwables {
  private Throwables() {
  }

  public static Throwable getRootCause(Throwable throwable) {
    // Keep a second pointer that slowly walks the causal chain. If the fast pointer ever catches
    // the slower pointer, then there's a loop.
    Throwable slowPointer = throwable;
    boolean advanceSlowPointer = false;

    Throwable cause;
    while ((cause = throwable.getCause()) != null) {
      throwable = cause;

      if (throwable == slowPointer) {
        throw new IllegalArgumentException("Loop in causal chain detected.", throwable);
      }
      if (advanceSlowPointer) {
        slowPointer = slowPointer.getCause();
      }
      advanceSlowPointer = !advanceSlowPointer; // only advance every other iteration
    }
    return throwable;
  }
}
