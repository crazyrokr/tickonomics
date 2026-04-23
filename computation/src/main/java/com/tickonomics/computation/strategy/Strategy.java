package com.tickonomics.computation.strategy;

import java.util.UUID;

public interface Strategy {
    UUID strategyId();
    String name();
    String category();
    boolean isActive();
}
