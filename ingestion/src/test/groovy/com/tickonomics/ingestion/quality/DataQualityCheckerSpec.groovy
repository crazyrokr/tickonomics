package com.tickonomics.ingestion.quality

import com.tickonomics.contracts.client.AnalyticsWorkerClient
import com.tickonomics.persistence.entity.RateSnapshot
import spock.lang.Specification
import spock.lang.Subject

import java.time.Duration
import java.time.Instant

class DataQualityCheckerSpec extends Specification {

  static final Instant NOW = Instant.parse("2026-05-24T12:00:00Z")

  AnalyticsWorkerClient analyticsClient = Mock()

  @Subject
  DataQualityChecker checker = new DataQualityChecker(analyticsClient, false, 5, true)

  def "given various rate scenarios, when checkRate, then return expected result"() {
    expect:
        checker.checkRate(curr, prev) == expected

    where:
        curr                                            | prev                                                               | expected
        null                                            | null                                                               | DataQualityChecker.DataQualityResult.MISSING
        new RateSnapshot(NOW, "SOFR", 4.2910, "NY_FED", null, null) | new RateSnapshot(NOW.minusSeconds(3600), "SOFR", 4.2900, "NY_FED", null, null) | DataQualityChecker.DataQualityResult.VALID
        new RateSnapshot(NOW, "SOFR", 5.00, "NY_FED", null, null)   | new RateSnapshot(NOW.minusSeconds(3600), "SOFR", 4.29, "NY_FED", null, null)   | DataQualityChecker.DataQualityResult.OUTLIER
        new RateSnapshot(NOW, "SOFR", 4.29, "NY_FED", null, null)   | null                                                               | DataQualityChecker.DataQualityResult.VALID
  }

  def "given various time scenarios, when isStale, then return expected result"() {
    expect:
        checker.isStale(Instant.now().minus(minusDuration), threshold) == expected

    where:
        minusDuration          | threshold             | expected
        Duration.ofSeconds(60) | Duration.ofMinutes(5) | false
        Duration.ofMinutes(10) | Duration.ofMinutes(5) | true
  }

  def "given various batch scenarios, when checkBatch, then return expected result"() {
    expect:
        checker.checkBatch(batch) == expected

    where:
        batch                                                                                                                                                                                     | expected
        null                                                                                                                                                                                      | DataQualityChecker.DataQualityResult.MISSING
        []                                                                                                                                                                                        | DataQualityChecker.DataQualityResult.MISSING
        [new RateSnapshot(NOW.minusSeconds(7200), "SOFR", 4.2900, "NY_FED", null, null), new RateSnapshot(NOW.minusSeconds(3600), "SOFR", 4.2905, "NY_FED", null, null), new RateSnapshot(NOW, "SOFR", 4.2910, "NY_FED", null, null)] | DataQualityChecker.DataQualityResult.VALID
        [new RateSnapshot(NOW.minusSeconds(7200), "SOFR", 4.28, "NY_FED", null, null), new RateSnapshot(NOW, "SOFR", 5.50, "NY_FED", null, null)]                                                                         | DataQualityChecker.DataQualityResult.OUTLIER
  }

  private static RateSnapshot rate(double value) {
    new RateSnapshot(NOW, "EFFR", value, "FRED", null, null)
  }

  // Hand-rolled stubs (rather than Spok mocks) because the anomaly worker is invoked on a
  // CompletableFuture worker thread; plain objects keep that cross-thread call deterministic.
  private AnalyticsWorkerClient workerReturning(Map<String, Object> response) {
    new AnalyticsWorkerClient() {
      @Override
      Map<String, Object> sendAnalysisRequest(String function, Map<String, Object> payload) { response }

      @Override
      byte[] sendArrowRequest(byte[] arrowPayload) { null }

      @Override
      boolean isHealthy() { true }
    }
  }

  private AnalyticsWorkerClient workerThrowing(RuntimeException failure) {
    new AnalyticsWorkerClient() {
      @Override
      Map<String, Object> sendAnalysisRequest(String function, Map<String, Object> payload) { throw failure }

      @Override
      byte[] sendArrowRequest(byte[] arrowPayload) { null }

      @Override
      boolean isHealthy() { true }
    }
  }

  def "given analytics worker flags value as anomalous, when checkRateWithAnomaly, then suspect anomaly"() {
    given:
      def client = workerReturning([anomaly_mask: [true]])
      def anomalyChecker = new DataQualityChecker(client, true, 5, true)

    when:
      def result = anomalyChecker.checkRateWithAnomaly(rate(5.25), rate(5.25)).join()

    then:
      result == DataQualityChecker.DataQualityResult.SUSPECT_ANOMALY
  }

  def "given analytics worker flags value as normal, when checkRateWithAnomaly, then threshold valid"() {
    given:
      def client = workerReturning([anomaly_mask: [false]])
      def anomalyChecker = new DataQualityChecker(client, true, 5, true)

    when:
      def result = anomalyChecker.checkRateWithAnomaly(rate(5.251), rate(5.25)).join()

    then:
      result == DataQualityChecker.DataQualityResult.VALID
  }

  def "given anomaly detection disabled, when checkRateWithAnomaly, then no worker call"() {
    when:
      def result = checker.checkRateWithAnomaly(rate(5.251), rate(5.25)).join()

    then:
      result == DataQualityChecker.DataQualityResult.VALID
      0 * analyticsClient.sendAnalysisRequest(*_)
  }

  def "given error response and fallback enabled, when checkRateWithAnomaly, then threshold result"() {
    given:
      def client = workerReturning([error: "connection refused"])
      def anomalyChecker = new DataQualityChecker(client, true, 5, true)

    when:
      def result = anomalyChecker.checkRateWithAnomaly(rate(5.251), rate(5.25)).join()

    then: "degrades to threshold check rather than blocking"
      result == DataQualityChecker.DataQualityResult.VALID
  }

  def "given worker throws and fallback enabled, when checkRateWithAnomaly, then threshold outlier"() {
    given:
      def client = workerThrowing(new RuntimeException("worker down"))
      def anomalyChecker = new DataQualityChecker(client, true, 5, true)

    when:
      def result = anomalyChecker.checkRateWithAnomaly(rate(5.30), rate(5.25)).join()

    then: "falls back to the threshold check which detects the 500bps outlier"
      result == DataQualityChecker.DataQualityResult.OUTLIER
  }

  def "given worker throws and fallback disabled, when checkRateWithAnomaly, then missing"() {
    given:
      def client = workerThrowing(new RuntimeException("worker down"))
      def anomalyChecker = new DataQualityChecker(client, true, 5, false)

    when:
      def result = anomalyChecker.checkRateWithAnomaly(rate(5.251), rate(5.25)).join()

    then: "no threshold fallback; unvalidated data reported as missing"
      result == DataQualityChecker.DataQualityResult.MISSING
  }
}
