package com.tickonomics.ingestion.buffer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class FileOverflowBuffer<T> {

    private static final Logger log = LoggerFactory.getLogger(FileOverflowBuffer.class);
    private final Path overflowPath;
    private final Class<T> itemClass;
    private final ObjectMapper objectMapper;

    public FileOverflowBuffer(Path overflowPath, Class<T> itemClass) {
        this.overflowPath = overflowPath;
        this.itemClass = itemClass;
        this.objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    public void append(T item) {
        try {
            ensureDirectoryExists();
            try (BufferedWriter writer = Files.newBufferedWriter(overflowPath,
                    java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.APPEND)) {
                writer.write(objectMapper.writeValueAsString(item));
                writer.newLine();
            }
        } catch (IOException e) {
            log.error("Failed to write overflow item to {}: {}", overflowPath, e.getMessage());
        }
    }

    public List<T> replayAll() {
        List<T> items = new ArrayList<>();
        if (!Files.exists(overflowPath)) {
            return items;
        }

        try (BufferedReader reader = Files.newBufferedReader(overflowPath)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isBlank()) {
                    items.add(objectMapper.readValue(line, itemClass));
                }
            }
        } catch (IOException e) {
            log.error("Failed to replay overflow from {}: {}", overflowPath, e.getMessage());
        }

        return items;
    }

    public void truncate() {
        try {
            Files.deleteIfExists(overflowPath);
        } catch (IOException e) {
            log.error("Failed to truncate overflow file {}: {}", overflowPath, e.getMessage());
        }
    }

    public boolean hasData() {
        return Files.exists(overflowPath) && overflowPath.toFile().length() > 0;
    }

    private void ensureDirectoryExists() throws IOException {
        Path parent = overflowPath.getParent();
        if (parent != null && !Files.exists(parent)) {
            Files.createDirectories(parent);
        }
    }
}
