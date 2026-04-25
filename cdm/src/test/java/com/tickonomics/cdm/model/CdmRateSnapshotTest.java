package com.tickonomics.cdm.model;

import com.tickonomics.cdm.enums.InstrumentType;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class CdmRateSnapshotTest {

    @Test
    void givenValidRateData_whenConstructing_thenFieldsAreAccessible() {
        Instant now = Instant.now();

        CdmRateSnapshot snapshot = new CdmRateSnapshot(
                now,
                InstrumentType.SOFR,
                5.31,
                "NY_FED"
        );

        assertEquals(now, snapshot.time());
        assertEquals(InstrumentType.SOFR, snapshot.instrumentType());
        assertEquals(5.31, snapshot.value());
        assertEquals("NY_FED", snapshot.source());
    }

    @Test
    void givenNaNValue_whenConstructing_thenThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> new CdmRateSnapshot(
                Instant.now(),
                InstrumentType.EFFR,
                Double.NaN,
                "FRED"
        ));
    }

    @Test
    void givenPositiveInfinityValue_whenConstructing_thenThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> new CdmRateSnapshot(
                Instant.now(),
                InstrumentType.TGCR,
                Double.POSITIVE_INFINITY,
                "NY_FED"
        ));
    }
}
