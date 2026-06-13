package com.tickonomics.ingestion.bulkhead

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.github.resilience4j.bulkhead.Bulkhead
import io.github.resilience4j.bulkhead.BulkheadConfig
import io.github.resilience4j.bulkhead.BulkheadRegistry
import org.slf4j.LoggerFactory
import spock.lang.Specification
import spock.lang.Subject

class IngestionBulkheadConfigSpec extends Specification {

    BulkheadRegistry registry = BulkheadRegistry.ofDefaults()
    ListAppender<ILoggingEvent> appender = new ListAppender<>()

    @Subject
    IngestionBulkheadConfig.BulkheadPressureMonitor monitor =
        new IngestionBulkheadConfig.BulkheadPressureMonitor(registry)

    def setup() {
        appender.start()
        pressureMonitorLogger().addAppender(appender)
    }

    def cleanup() {
        pressureMonitorLogger().detachAppender(appender)
    }

    private long pressureLogCount() {
        appender.list.count { ILoggingEvent event -> event.formattedMessage.contains("BULKHEAD_POOL_PRESSURE") }
    }

    def "given saturated bulkhead, when checkPressure, then logs pressure alert"() {
        given: "a single-slot critical bulkhead held at 100% utilization"
            Bulkhead critical = registry.bulkhead(
                IngestionBulkheadConfig.CRITICAL_INGESTION,
                BulkheadConfig.custom().maxConcurrentCalls(1).build())
            critical.acquirePermission()

        when:
            monitor.checkPressure()

        then:
            pressureLogCount() == 1
            appender.list[0].formattedMessage.contains("criticalIngestion")
    }

    def "given idle bulkhead, when checkPressure, then no pressure alert"() {
        given: "an empty high-volume pool"
            registry.bulkhead(
                IngestionBulkheadConfig.HIGH_VOLUME_INGESTION,
                BulkheadConfig.custom().maxConcurrentCalls(16).build())

        when:
            monitor.checkPressure()

        then:
            pressureLogCount() == 0
    }

    def "given sustained pressure, when checkPressure twice, then logs once per transition"() {
        given: "a saturated critical bulkhead"
            Bulkhead critical = registry.bulkhead(
                IngestionBulkheadConfig.CRITICAL_INGESTION,
                BulkheadConfig.custom().maxConcurrentCalls(1).build())
            critical.acquirePermission()

        when: "still pressured on the second sample"
            monitor.checkPressure()
            monitor.checkPressure()

        then: "the alert fires once, not on every sample"
            pressureLogCount() == 1

        and: "recovery clears the flag without logging"
            critical.releasePermission()
            monitor.checkPressure()
            pressureLogCount() == 1

        and: "re-entering pressure logs again"
            critical.acquirePermission()
            monitor.checkPressure()
            pressureLogCount() == 2
    }

    def "given unregistered bulkheads, when checkPressure, then no failure"() {
        given: "a monitor whose registry has none of the named bulkheads"
            def isolatedMonitor =
                new IngestionBulkheadConfig.BulkheadPressureMonitor(BulkheadRegistry.ofDefaults())

        when:
            isolatedMonitor.checkPressure()

        then: "missing bulkheads are skipped, not fatal"
            pressureLogCount() == 0
    }

    private static Logger pressureMonitorLogger() {
        (Logger) LoggerFactory.getLogger(IngestionBulkheadConfig.BulkheadPressureMonitor)
    }
}
