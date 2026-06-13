package com.tickonomics.ingestion.tracing;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Thin helper over the Micrometer {@link Tracer} that starts named spans for ingestion operations.
 * Callers use the returned {@link SpanScope} in a try-with-resources block; when tracing is disabled
 * ({@code monitor.tracing.enabled=false}) a no-op scope is returned so there is no overhead.
 */
@Component
public class IngestionTracer {

  private final Tracer tracer;
  private final boolean enabled;

  public IngestionTracer(Tracer tracer, @Value("${monitor.tracing.enabled:true}") boolean enabled) {
    this.tracer = tracer;
    this.enabled = enabled;
  }

  public SpanScope span(String name) {
    if (!enabled || tracer == null) {
      return SpanScope.NOOP;
    }
    Span span = tracer.nextSpan().name(name).start();
    Tracer.SpanInScope scope = tracer.withSpan(span);
    return new SpanScope(span, scope);
  }

  /** Auto-closeable handle that ends the span and its scope on exit. */
  public static final class SpanScope implements AutoCloseable {

    /** Sentinel no-op scope used when tracing is disabled; safe to close repeatedly. */
    public static final SpanScope NOOP = new SpanScope(null, null);

    private final Span span;
    private final Tracer.SpanInScope scope;

    private SpanScope(Span span, Tracer.SpanInScope scope) {
      this.span = span;
      this.scope = scope;
    }

    @Override
    public void close() {
      if (scope != null) {
        scope.close();
      }
      if (span != null) {
        span.end();
      }
    }
  }
}
