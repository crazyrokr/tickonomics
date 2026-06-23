package com.tickonomics.ingestion.buffer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class TieredIngestionBuffer<T> implements IngestionBuffer<T> {

    private static final Logger log = LoggerFactory.getLogger(TieredIngestionBuffer.class);

    private final InMemoryIngestionBuffer<T> memoryBuffer;
    private final FileOverflowBuffer<T> fileOverflow;

    public TieredIngestionBuffer(int memoryCapacity, Path overflowPath, Class<T> itemClass) {
        this.memoryBuffer = InMemoryIngestionBuffer.create(memoryCapacity);
        this.fileOverflow = new FileOverflowBuffer<>(overflowPath, itemClass);
        recoverFromOverflow();
    }

    @Override
    public void add(T item) {
        if (memoryBuffer.isOverflowing()) {
            fileOverflow.append(item);
            log.debug("Overflowed item to file buffer");
        } else {
            memoryBuffer.add(item);
        }
    }

    @Override
    public List<T> drain(int maxItems) {
        List<T> batch = new ArrayList<>(memoryBuffer.drain(maxItems));

        if (batch.size() < maxItems && fileOverflow.hasData()) {
            List<T> overflowItems = fileOverflow.replayAll();
            int remaining = maxItems - batch.size();
            batch.addAll(overflowItems.subList(0, Math.min(remaining, overflowItems.size())));
            fileOverflow.truncate();
        }

        return batch;
    }

    @Override
    public int size() {
        return memoryBuffer.size();
    }

    @Override
    public boolean isOverflowing() {
        return memoryBuffer.isOverflowing() && fileOverflow.hasData();
    }

    private void recoverFromOverflow() {
        if (!fileOverflow.hasData()) {
            return;
        }
        List<T> recovered = fileOverflow.replayAll();
        for (T item : recovered) {
            memoryBuffer.add(item);
        }
        fileOverflow.truncate();
        log.info("Recovered {} items from overflow file", recovered.size());
    }
}
