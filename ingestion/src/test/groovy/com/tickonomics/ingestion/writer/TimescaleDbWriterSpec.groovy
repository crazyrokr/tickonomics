package com.tickonomics.ingestion.writer

import com.tickonomics.persistence.entity.RateSnapshot
import com.tickonomics.persistence.entity.TickData
import com.tickonomics.persistence.repository.RateSnapshotRepository
import com.tickonomics.persistence.repository.TickDataRepository
import spock.lang.Specification
import spock.lang.Subject

import java.time.Instant

class TimescaleDbWriterSpec extends Specification {

  static final Instant NOW = Instant.parse("2026-05-24T12:00:00Z")

  TickDataRepository tickDataRepository = Mock()
  RateSnapshotRepository rateSnapshotRepository = Mock()

  @Subject
  TimescaleDbWriter writer

  def setup() {
    writer = new TimescaleDbWriter(tickDataRepository, rateSnapshotRepository)
    writer.batchSize = 10
  }

  def "given single tick, when write, then buffered not flushed"() {
    given:
        def tick = new TickData(NOW, "SPY", 450.50, 1000, new int[0])

    when:
        writer.writeTick(tick)

    then:
        writer.pendingTickCount() == 1
        0 * tickDataRepository.saveAll(_)
  }

  def "given batch size ticks, when write, then auto flushed"() {
    when:
        10.times { i ->
          writer.writeTick(new TickData(NOW.plusMillis(i), "SPY", 450.0 + i, 100, new int[0]))
        }

    then:
        writer.pendingTickCount() == 0
        1 * tickDataRepository.saveAll(_)
  }

  def "given flush all, when buffered, then drained"() {
    given:
        writer.writeTick(new TickData(NOW, "SPY", 450.50, 1000, new int[0]))
        writer.writeRate(new RateSnapshot(NOW, "SOFR", 4.29, "NY_FED"))

    when:
        writer.flushAll()

    then:
        writer.pendingTickCount() == 0
        writer.pendingRateCount() == 0
        1 * tickDataRepository.saveAll(_)
        1 * rateSnapshotRepository.saveAll(_)
  }

  def "given single rate, when write, then buffered not flushed"() {
    given:
        def rate = new RateSnapshot(NOW, "SOFR", 4.29, "NY_FED")

    when:
        writer.writeRate(rate)

    then:
        writer.pendingRateCount() == 1
        0 * rateSnapshotRepository.saveAll(_)
  }

  def "given batch size rates, when write, then auto flushed"() {
    when:
        10.times { i ->
          writer.writeRate(new RateSnapshot(NOW.plusMillis(i), "SOFR", 4.29 + i * 0.01, "NY_FED"))
        }

    then:
        writer.pendingRateCount() == 0
        1 * rateSnapshotRepository.saveAll(_)
  }

  def "given flush failure, when flush, then rebuffered"() {
    given:
        tickDataRepository.saveAll(_ as List) >> { throw new RuntimeException("DB error") }
        writer.writeTick(new TickData(NOW, "SPY", 450.50, 1000, new int[0]))

    when:
        writer.flushTicks()

    then:
        writer.pendingTickCount() > 0
  }
}
