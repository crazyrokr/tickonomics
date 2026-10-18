package com.tickonomics.computation.talib

import spock.lang.Specification

import java.nio.file.Files
import java.nio.file.Path

class TalibNativeLoaderSpec extends Specification {

  def originalLibraryPath

  def setup() {
    originalLibraryPath = System.getProperty(TalibNativeLoader.JAVA_LIBRARY_PATH)
    resetVerifiedState()
  }

  def cleanup() {
    if (originalLibraryPath == null) {
      System.clearProperty(TalibNativeLoader.JAVA_LIBRARY_PATH)
    } else {
      System.setProperty(TalibNativeLoader.JAVA_LIBRARY_PATH, originalLibraryPath)
    }
  }

  // --- ensureLoaded: happy path ---

  def "given library on java.library.path when ensureLoaded then no exception"() {
    given:
    Path libDir = createTempLibDir()
    System.setProperty(TalibNativeLoader.JAVA_LIBRARY_PATH, libDir.toAbsolutePath().toString())

    when:
    TalibNativeLoader.ensureLoaded()

    then:
    noExceptionThrown()
  }

  def "given library already verified when ensureLoaded again then no exception"() {
    given:
    Path libDir = createTempLibDir()
    System.setProperty(TalibNativeLoader.JAVA_LIBRARY_PATH, libDir.toAbsolutePath().toString())
    TalibNativeLoader.ensureLoaded()

    when:
    resetVerifiedState()
    TalibNativeLoader.ensureLoaded()

    then:
    noExceptionThrown()
  }

  def "given library on multi-entry java.library.path when ensureLoaded then no exception"() {
    given:
    Path libDir = createTempLibDir()
    Path emptyDir = Files.createTempDirectory('talib-empty')
    emptyDir.toFile().deleteOnExit()
    System.setProperty(
        TalibNativeLoader.JAVA_LIBRARY_PATH,
        emptyDir.toAbsolutePath().toString() + java.io.File.pathSeparator + libDir.toAbsolutePath().toString())

    when:
    TalibNativeLoader.ensureLoaded()

    then:
    noExceptionThrown()
  }

  // --- ensureLoaded: unreachable ---

  def "given library unreachable when ensureLoaded then fails with guidance"() {
    given:
    Path emptyDir = Files.createTempDirectory('talib-missing')
    emptyDir.toFile().deleteOnExit()
    System.setProperty(TalibNativeLoader.JAVA_LIBRARY_PATH, emptyDir.toAbsolutePath().toString())

    when:
    TalibNativeLoader.ensureLoaded()

    then:
    IllegalStateException e = thrown()
    e.message.contains(TalibNativeLoader.JAVA_LIBRARY_PATH)
    e.message.contains(TalibNativeLoader.LD_LIBRARY_PATH)
    e.message.contains(TalibNativeLoader.LIBRARY_NAME)
  }

  def "given library unreachable when ensureLoaded twice then both calls fail"() {
    given:
    Path emptyDir = Files.createTempDirectory('talib-missing')
    emptyDir.toFile().deleteOnExit()
    System.setProperty(TalibNativeLoader.JAVA_LIBRARY_PATH, emptyDir.toAbsolutePath().toString())

    when:
    TalibNativeLoader.ensureLoaded()

    then:
    thrown(IllegalStateException)

    when:
    TalibNativeLoader.ensureLoaded()

    then:
    thrown(IllegalStateException)
  }

  // --- helpers ---

  private static void resetVerifiedState() {
    def field = TalibNativeLoader.class.getDeclaredField('verified')
    field.setAccessible(true)
    field.set(null, false)
  }

  private static Path createTempLibDir() {
    Path dir = Files.createTempDirectory('talib-test')
    Files.createFile(dir.resolve(TalibNativeLoader.LIBRARY_NAME))
    dir.toFile().deleteOnExit()
    return dir
  }
}
