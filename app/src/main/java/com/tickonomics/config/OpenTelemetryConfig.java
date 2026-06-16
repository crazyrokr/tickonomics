package com.tickonomics.config;

import io.micrometer.tracing.otel.bridge.OtelCurrentTraceContext;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Provides the OTel beans that Spring Boot 4's {@code OpenTelemetryTracingAutoConfiguration}
 * requires but no longer auto-creates (Spring Boot 4 removed its own {@code
 * OpenTelemetryAutoConfiguration}).
 *
 * <p>{@link OpenTelemetry} is built self-contained via the OTel SDK auto-configuration, which
 * reads standard OTel auto-config (e.g. {@code OTEL_EXPORTER_OTLP_ENDPOINT},
 * {@code OTEL_SERVICE_NAME}); {@code OpenTelemetryTracingAutoConfiguration} then bridges it into
 * Micrometer's {@code Tracer}. {@link Resource} and {@link OtelCurrentTraceContext} satisfy the
 * remaining hard dependencies of that auto-configuration.
 */
@Configuration
public class OpenTelemetryConfig {

  @Bean
  public Resource otelResource() {
    return Resource.getDefault();
  }

  @Bean
  public OpenTelemetry openTelemetry() {
    return AutoConfiguredOpenTelemetrySdk.initialize().getOpenTelemetrySdk();
  }

  @Bean
  public OtelCurrentTraceContext otelCurrentTraceContext() {
    return new OtelCurrentTraceContext();
  }
}
