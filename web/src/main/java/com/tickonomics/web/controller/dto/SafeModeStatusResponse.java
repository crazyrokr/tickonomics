package com.tickonomics.web.controller.dto;

import java.time.Instant;
import java.util.List;

/** Typed safe-mode status response. */
public record SafeModeStatusResponse(
    boolean active,
    boolean autoActivated,
    boolean manualOverride,
    boolean enabled,
    String lastReason,
    Instant lastActivationAt,
    List<String> degradedIndicators,
    boolean recoveryReady) {
}
