package com.tickonomics.ingestion.quality

import com.tickonomics.persistence.entity.RateSnapshot
import spock.lang.Specification
import spock.lang.Subject

import java.time.Duration
import java.time.Instant

class DataQualityCheckerSpec extends Specification {

  static final Instant NOW = Instant.parse("2026-05-24T12:00:00Z")

  @Subject
  DataQualityChecker checker = new DataQualityChecker()

  def "given various rate scenarios, when checkRate, then return expected result"() {
    expect:
        checker.checkRate(curr, prev) == expected

    where:
        curr                                            | prev                                                               | expected
        null                                            | null                                                               | DataQualityChecker.DataQualityResult.MISSING
        new RateSnapshot(NOW, "SOFR", 4.2910, "NY_FED") | new RateSnapshot(NOW.minusSeconds(3600), "SOFR", 4.2900, "NY_FED") | DataQualityChecker.DataQualityResult.VALID
        new RateSnapshot(NOW, "SOFR", 5.00, "NY_FED")   | new RateSnapshot(NOW.minusSeconds(3600), "SOFR", 4.29, "NY_FED")   | DataQualityChecker.DataQualityResult.OUTLIER
        new RateSnapshot(NOW, "SOFR", 4.29, "NY_FED")   | null                                                               | DataQualityChecker.DataQualityResult.VALID
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
        [new RateSnapshot(NOW.minusSeconds(7200), "SOFR", 4.2900, "NY_FED"), new RateSnapshot(NOW.minusSeconds(3600), "SOFR", 4.2905, "NY_FED"), new RateSnapshot(NOW, "SOFR", 4.2910, "NY_FED")] | DataQualityChecker.DataQualityResult.VALID
        [new RateSnapshot(NOW.minusSeconds(7200), "SOFR", 4.28, "NY_FED"), new RateSnapshot(NOW, "SOFR", 5.50, "NY_FED")]                                                                         | DataQualityChecker.DataQualityResult.OUTLIER
  }
}
