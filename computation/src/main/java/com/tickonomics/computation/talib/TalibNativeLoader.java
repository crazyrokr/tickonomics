package com.tickonomics.computation.talib;

import java.io.File;
import java.util.List;

public final class TalibNativeLoader {

  static final String LIBRARY_NAME = "libta-lib.so";
  static final String JAVA_LIBRARY_PATH = "java.library.path";
  static final String LD_LIBRARY_PATH = "LD_LIBRARY_PATH";
  // Directories the wrapper itself scans after System.loadLibrary fails
  static final List<String> COMMON_LIB_DIRS = List.of(
      "/usr/lib/x86_64-linux-gnu", "/usr/lib", "/usr/local/lib", "/lib/x86_64-linux-gnu", "/lib");
  static final String BUNDLED_RESOURCE = "/libta-lib.so";
  private static final Object LOCK = new Object();
  private static volatile boolean verified;

  private TalibNativeLoader() {
  }

  /**
   * The FFM wrapper resolves the native library via System.loadLibrary("ta-lib"),
   * falling back to common system directories and LD_LIBRARY_PATH. It does not
   * load from classpath resources, so this preflight fails fast with actionable
   * guidance when none of those locations contains the library.
   */
  public static void ensureLoaded() {
    synchronized (LOCK) {
      if (verified) {
        return;
      }
      if (isDiscoverable()) {
        verified = true;
        return;
      }
      throw new IllegalStateException(unreachableMessage());
    }
  }

  static boolean isDiscoverable() {
    if (dirsFromProperty(JAVA_LIBRARY_PATH).stream().anyMatch(dir -> libraryIn(dir).exists())) {
      return true;
    }
    if (dirsFromEnv(LD_LIBRARY_PATH).stream().anyMatch(dir -> libraryIn(dir).exists())) {
      return true;
    }
    return COMMON_LIB_DIRS.stream().anyMatch(dir -> libraryIn(dir).exists());
  }

  private static List<String> dirsFromProperty(String name) {
    String value = System.getProperty(name);
    return value == null || value.isBlank() ? List.of() : List.of(value.split(File.pathSeparator));
  }

  private static List<String> dirsFromEnv(String name) {
    String value = System.getenv(name);
    return value == null || value.isBlank() ? List.of() : List.of(value.split(File.pathSeparator));
  }

  private static File libraryIn(String dir) {
    return new File(dir, LIBRARY_NAME);
  }

  private static String unreachableMessage() {
    boolean bundled = TalibNativeLoader.class.getResource(BUNDLED_RESOURCE) != null;
    return "TA-Lib native library '" + LIBRARY_NAME + "' is not discoverable by the JVM. Point "
        + JAVA_LIBRARY_PATH + " or " + LD_LIBRARY_PATH + " at a directory containing " + LIBRARY_NAME
        + " (e.g. native-libs/ta-lib/build after running ./gradlew :computation:setupTalib), or install "
        + LIBRARY_NAME + " under /usr/local/lib. "
        + (bundled
            ? "The library is bundled on the classpath at " + BUNDLED_RESOURCE
              + " but the FFM wrapper only loads from library paths."
            : "The library is not bundled on the classpath either.");
  }
}
