package com.tickonomics.web.exception;

/**
 * Thrown when a rate-limited endpoint is invoked beyond its configured token budget. Translated to
 * HTTP 429 by {@link GlobalExceptionHandler}.
 */
public class RateLimitExceededException extends RuntimeException {

  public RateLimitExceededException(String message) {
    super(message);
  }
}
