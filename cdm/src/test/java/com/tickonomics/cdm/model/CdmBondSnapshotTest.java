package com.tickonomics.cdm.model;

import com.tickonomics.cdm.enums.InstrumentType;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class CdmBondSnapshotTest {

    private static final Instant NOW = Instant.parse("2026-05-23T12:00:00Z");

    @Test
    void givenValidArgs_whenConstruct_thenFieldsSet() {
        var snap = new CdmBondSnapshot(NOW, InstrumentType.BILL_3M,
                4.25, 0.0025, 0.0001, 0.248, -0.0025, 0.0001, "NY_FED");
        assertEquals(NOW, snap.time());
        assertEquals(InstrumentType.BILL_3M, snap.instrumentType());
        assertEquals(4.25, snap.yieldValue());
        assertEquals(0.0025, snap.dv01());
        assertEquals("NY_FED", snap.source());
    }

    @Test
    void givenNullTime_whenConstruct_thenThrows() {
        assertThrows(NullPointerException.class,
                () -> new CdmBondSnapshot(null, InstrumentType.BILL_3M,
                        4.25, 0.0025, 0.0001, 0.248, -0.0025, 0.0001, "NY_FED"));
    }

    @Test
    void givenNullInstrumentType_whenConstruct_thenThrows() {
        assertThrows(NullPointerException.class,
                () -> new CdmBondSnapshot(NOW, null,
                        4.25, 0.0025, 0.0001, 0.248, -0.0025, 0.0001, "NY_FED"));
    }

    @Test
    void givenNaNYield_whenConstruct_thenThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> new CdmBondSnapshot(NOW, InstrumentType.BILL_3M,
                        Double.NaN, 0.0025, 0.0001, 0.248, -0.0025, 0.0001, "NY_FED"));
    }

    @Test
    void givenNaNDv01_whenConstruct_thenThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> new CdmBondSnapshot(NOW, InstrumentType.BILL_3M,
                        4.25, Double.NaN, 0.0001, 0.248, -0.0025, 0.0001, "NY_FED"));
    }

    @Test
    void givenNaNConvexity_whenConstruct_thenThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> new CdmBondSnapshot(NOW, InstrumentType.BILL_3M,
                        4.25, 0.0025, Double.NaN, 0.248, -0.0025, 0.0001, "NY_FED"));
    }

    @Test
    void givenNaNDuration_whenConstruct_thenThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> new CdmBondSnapshot(NOW, InstrumentType.BILL_3M,
                        4.25, 0.0025, 0.0001, Double.NaN, -0.0025, 0.0001, "NY_FED"));
    }
}
