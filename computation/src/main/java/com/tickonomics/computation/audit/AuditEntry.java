package com.tickonomics.computation.audit;

import java.time.Instant;
import java.util.UUID;

public record AuditEntry(
    Instant time,
    UUID dataPointId,
    String codingRule,
    String ruleVersion,
    String inputHash,
    double outputValue,
    double irScore) {}
