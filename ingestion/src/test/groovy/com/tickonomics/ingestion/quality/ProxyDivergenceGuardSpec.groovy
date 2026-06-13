package com.tickonomics.ingestion.quality

import com.tickonomics.persistence.entity.RateSnapshot
import com.tickonomics.persistence.repository.ProxyDivergenceEventRepository
import com.tickonomics.persistence.repository.RateSnapshotRepository
import spock.lang.Specification
import spock.lang.Subject

import java.time.Instant

class ProxyDivergenceGuardSpec extends Specification {

  static final Instant NOW = Instant.parse("2026-05-24T12:00:00Z")

  RateSnapshotRepository rateRepository = Mock()
  ProxyDivergenceEventRepository divergenceRepository = Mock()

  @Subject
  ProxyDivergenceGuard guard

  def setup() {
    guard = new ProxyDivergenceGuard(rateRepository, divergenceRepository)
  }

  def "given identical series, when computeCorrelation, then one"() {
    given:
        def series = [
            new RateSnapshot(NOW, "TBILL_3M", 4.25, "NY_FED", null, null),
            new RateSnapshot(NOW.plusSeconds(3600), "TBILL_3M", 4.26, "NY_FED", null, null)
        ]

    when:
        double corr = guard.computeCorrelation(series, series)

    then:
        Math.abs(corr - 1.0) < 1e-9
  }

  def "given single point, when computeCorrelation, then one"() {
    given:
        def series = [new RateSnapshot(NOW, "TBILL_3M", 4.25, "NY_FED", null, null)]

    when:
        double corr = guard.computeCorrelation(series, series)

    then:
        Math.abs(corr - 1.0) < 1e-9
  }

  def "given constant difference, when computeDivergenceScore, then zero"() {
    given:
        def x = [
            new RateSnapshot(NOW.minusSeconds(7200), "TBILL_3M", 4.250, "NY_FED", null, null),
            new RateSnapshot(NOW.minusSeconds(3600), "TBILL_3M", 4.250, "NY_FED", null, null),
            new RateSnapshot(NOW, "TBILL_3M", 4.250, "NY_FED", null, null)
        ]
        def y = [
            new RateSnapshot(NOW.minusSeconds(7200), "SOFR", 4.350, "NY_FED", null, null),
            new RateSnapshot(NOW.minusSeconds(3600), "SOFR", 4.350, "NY_FED", null, null),
            new RateSnapshot(NOW, "SOFR", 4.350, "NY_FED", null, null)
        ]

    when:
        double score = guard.computeDivergenceScore(x, y, 1.0)

    then:
        Math.abs(score - 0.0) < 1e-9
  }

  def "given empty input, when computeDivergenceScore, then zero"() {
    expect:
        guard.computeDivergenceScore([], [], 1.0) == 0.0
  }
}
