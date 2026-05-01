package com.tickonomics.computation.fixedincome;

public record BondPosition(
    String tenor, double duration, double yield, double weight) {}
