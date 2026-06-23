package com.tickonomics.web.controller.dto;

/** Typed kill-switch response for activate/deactivate/status. {@code liquidatedTrades} is only
 * populated when activation supplies liquidation prices. */
public record KillSwitchResponse(boolean active, Integer liquidatedTrades) {
}
