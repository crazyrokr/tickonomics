package com.tickonomics.ingestion.tracing

import io.micrometer.tracing.Span
import io.micrometer.tracing.Tracer
import spock.lang.Specification

class IngestionTracingConfigSpec extends Specification {

    def "given enabled tracer, when span, then starts named span and closes scope"() {
        given: "recording fakes capturing the span lifecycle"
            String recordedName = null
            boolean started = false
            boolean ended = false
            boolean scopeClosed = false

            // Hand-rolled fakes (not Spock mocks) because Micrometer's Span declares a covariant
            // name(String) (abstract Span + inherited default SpanCustomizer) that confuses Spock's
            // mock interceptor. Map coercion dispatches name/start/end to recorders reliably. The
            // cell array lets the fluent name()/start() return the proxy without a self-reference.
            def spanRef = new Object[1]
            Span spanProxy = [
                name:  { String n -> recordedName = n; (Span) spanRef[0] },
                start: { -> started = true; (Span) spanRef[0] },
                end:   { -> ended = true }
            ] as Span
            spanRef[0] = spanProxy

            Tracer.SpanInScope scopeProxy = [close: { -> scopeClosed = true }] as Tracer.SpanInScope

            Tracer tracer = [
                nextSpan: { -> spanProxy },
                withSpan: { Span s -> scopeProxy }
            ] as Tracer

            def ingestionTracer = new IngestionTracer(tracer, true)

        when:
            ingestionTracer.span(IngestionTracingConfig.SPAN_FRED_FETCH).close()

        then: "span is named, started, scoped, then ended on close"
            recordedName == IngestionTracingConfig.SPAN_FRED_FETCH
            started
            scopeClosed
            ended
    }

    def "given disabled tracing, when span, then noop scope and no tracer interaction"() {
        given:
            Tracer tracer = Mock()
            def ingestionTracer = new IngestionTracer(tracer, false)

        when:
            def scope = ingestionTracer.span(IngestionTracingConfig.SPAN_NYFED_FETCH)
            scope.close()

        then:
            scope.is(IngestionTracer.SpanScope.NOOP)
            0 * tracer.nextSpan()
    }

    def "given null tracer, when span, then noop scope"() {
        given:
            def ingestionTracer = new IngestionTracer(null, true)

        when:
            def scope = ingestionTracer.span(IngestionTracingConfig.SPAN_TIMESCALEDB_WRITE)

        then:
            scope.is(IngestionTracer.SpanScope.NOOP)
    }

    def "given config constants, then all seven operations named"() {
        expect:
            IngestionTracingConfig.SPAN_FRED_FETCH == "ingestion.fred.fetch"
            IngestionTracingConfig.SPAN_NYFED_FETCH == "ingestion.nyfed.fetch"
            IngestionTracingConfig.SPAN_FINNHUB_MESSAGE == "ingestion.finnhub.message"
            IngestionTracingConfig.SPAN_TIMESCALEDB_WRITE == "ingestion.timescaledb.write"
            IngestionTracingConfig.SPAN_QUALITY_CHECK == "ingestion.quality.check"
            IngestionTracingConfig.SPAN_ANOMALY_DETECT == "ingestion.anomaly.detect"
            IngestionTracingConfig.SPAN_DISASTER_POLL == "ingestion.disaster.poll"
    }
}
