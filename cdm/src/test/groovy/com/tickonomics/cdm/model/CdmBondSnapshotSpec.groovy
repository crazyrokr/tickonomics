package com.tickonomics.cdm.model

import com.tickonomics.cdm.enums.InstrumentType
import spock.lang.Specification
import spock.lang.Unroll

import java.time.Instant

class CdmBondSnapshotSpec extends Specification {

  private static final Instant NOW = Instant.parse("2026-05-23T12:00:00Z")

  def "valid arguments set all fields correctly"() {
    when:
        def snap = new CdmBondSnapshot(NOW, InstrumentType.BILL_3M,
            4.25, 0.0025, 0.0001, 0.248, -0.0025, 0.0001, "NY_FED")

    then:
        snap.time() == NOW
        snap.instrumentType() == InstrumentType.BILL_3M
        snap.yieldValue() == 4.25
        snap.dv01() == 0.0025
        snap.source() == "NY_FED"
  }

  @Unroll
  def "constructor throws exception when #desc"() {
    when:
        new CdmBondSnapshot(time, type, yield, dv01, convexity, duration, basisPointValue, oas, source)

    then:
        thrown(expectedException)

    where:
        desc                      | time | type                   | yield      | dv01       | convexity  | duration   | basisPointValue | oas    | source   | expectedException
        "time is null"            | null | InstrumentType.BILL_3M | 4.25       | 0.0025     | 0.0001     | 0.248      | -0.0025         | 0.0001 | "NY_FED" | NullPointerException
        "instrument type is null" | NOW  | null                   | 4.25       | 0.0025     | 0.0001     | 0.248      | -0.0025         | 0.0001 | "NY_FED" | NullPointerException
        "yield is NaN"            | NOW  | InstrumentType.BILL_3M | Double.NaN | 0.0025     | 0.0001     | 0.248      | -0.0025         | 0.0001 | "NY_FED" | IllegalArgumentException
        "dv01 is NaN"             | NOW  | InstrumentType.BILL_3M | 4.25       | Double.NaN | 0.0001     | 0.248      | -0.0025         | 0.0001 | "NY_FED" | IllegalArgumentException
        "convexity is NaN"        | NOW  | InstrumentType.BILL_3M | 4.25       | 0.0025     | Double.NaN | 0.248      | -0.0025         | 0.0001 | "NY_FED" | IllegalArgumentException
        "duration is NaN"         | NOW  | InstrumentType.BILL_3M | 4.25       | 0.0025     | 0.0001     | Double.NaN | -0.0025         | 0.0001 | "NY_FED" | IllegalArgumentException
  }
}
