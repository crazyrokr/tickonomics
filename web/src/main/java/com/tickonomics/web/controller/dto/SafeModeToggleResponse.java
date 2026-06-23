package com.tickonomics.web.controller.dto;

/** Typed safe-mode manual toggle response. */
public record SafeModeToggleResponse(boolean active, String source) {
}
