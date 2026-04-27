package com.tickonomics.computation.talib;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class TalibNativeLoader {

    private static final String LIBRARY_RESOURCE = "/native/libta-lib.so";
    private static final String JNA_LIBRARY_PATH = "jna.library.path";
    private static volatile boolean loaded;

    private TalibNativeLoader() {
    }

    public static synchronized void ensureLoaded() {
        if (loaded) {
            return;
        }
        String existing = System.getProperty(JNA_LIBRARY_PATH);
        if (existing != null && Path.of(existing).resolve("libta-lib.so").toFile().exists()) {
            loaded = true;
            return;
        }
        try {
            Path tempDir = Files.createTempDirectory("talib-native");
            tempDir.toFile().deleteOnExit();
            Path libDest = tempDir.resolve("libta-lib.so");
            try (InputStream is = TalibNativeLoader.class.getResourceAsStream(LIBRARY_RESOURCE)) {
                if (is == null) {
                    throw new IllegalStateException("TA-Lib native library not found on classpath: " + LIBRARY_RESOURCE);
                }
                Files.copy(is, libDest, StandardCopyOption.REPLACE_EXISTING);
            }
            libDest.toFile().deleteOnExit();
            System.setProperty(JNA_LIBRARY_PATH, tempDir.toAbsolutePath().toString());
            loaded = true;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to extract TA-Lib native library", e);
        }
    }
}
