package com.tickonomics.ingestion.buffer;

import java.util.List;

public interface IngestionBuffer<T> {

    void add(T item);

    List<T> drain(int maxItems);

    int size();

    boolean isOverflowing();
}
