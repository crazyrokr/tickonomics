package com.tickonomics.ingestion.filter;

import com.tickonomics.persistence.entity.TickData;

import java.util.List;

@FunctionalInterface
public interface TickFilter {

    TickData apply(TickData tick, List<TickData> recentTicks);
}
