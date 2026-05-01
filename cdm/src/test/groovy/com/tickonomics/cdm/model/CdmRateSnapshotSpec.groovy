package com.tickonomics.cdm.model

import com.tickonomics.cdm.enums.InstrumentType
import spock.lang.Specification
import spock.lang.Unroll

import java.time.Instant

class CdmRateSnapshotSpec extends Specification {

  def "valid rate data sets all fields correctly"() {
    given:
        def now = Instant.now()

    when:
        def snapshot = new CdmRateSnapshot(now, InstrumentType.SOFR, 5.31, "NY_FED")

    then:
        snapshot.time() == now
        snapshot.instrumentType() == InstrumentType.SOFR
        snapshot.value() == 5.31
        snapshot.source() == "NY_FED"
  }

  @Unroll
  def "constructor throws IllegalArgumentException when value is #desc"() {
    when:
        new CdmRateSnapshot(Instant.now(), type, value, "SRC")

    then:
        thrown(IllegalArgumentException)

    where:
        desc                | type                | value
        "NaN"               | InstrumentType.EFFR | Double.NaN
        "positive infinity" | InstrumentType.TGCR | Double.POSITIVE_INFINITY
  }
}
