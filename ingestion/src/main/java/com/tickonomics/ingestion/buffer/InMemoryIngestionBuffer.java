package com.tickonomics.ingestion.buffer;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

public class InMemoryIngestionBuffer<T> implements IngestionBuffer<T> {

    private final ConcurrentLinkedQueue<T> queue = new ConcurrentLinkedQueue<>();
    private final int capacity;

    private InMemoryIngestionBuffer(int capacity) {
        this.capacity = capacity;
    }

    public static <T> InMemoryIngestionBuffer<T> create(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive, got " + capacity);
        }
        return new InMemoryIngestionBuffer<>(capacity);
    }

    @Override
    public void add(T item) {
        queue.add(item);
    }

    @Override
    public List<T> drain(int maxItems) {
        List<T> batch = new ArrayList<>();
        T item;
        while (batch.size() < maxItems && (item = queue.poll()) != null) {
            batch.add(item);
        }
        return batch;
    }

    @Override
    public int size() {
        return queue.size();
    }

    @Override
    public boolean isOverflowing() {
        return queue.size() >= capacity;
    }
}
