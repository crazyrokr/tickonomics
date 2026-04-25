package com.tickonomics.cdm.model;

import com.tickonomics.cdm.enums.DayCountConvention;
import com.tickonomics.cdm.enums.OptionType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CdmOptionSnapshotTest {

    private static CdmOptionSnapshot validSnapshot() {
        return new CdmOptionSnapshot(
                UUID.randomUUID(),
                "SPY",
                new BigDecimal("450.00"),
                LocalDate.of(2026, 6, 20),
                OptionType.CALL,
                DayCountConvention.ACT_365_FIXED,
                0.55,
                0.12,
                -0.03,
                0.85,
                0.15,
                0.22,
                0.08,
                5.10,
                5.30,
                1500L,
                Instant.now()
        );
    }

    @Test
    void givenValidOptionData_whenConstructing_thenAllFieldsAreAccessible() {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();

        CdmOptionSnapshot snapshot = new CdmOptionSnapshot(
                id,
                "AAPL",
                new BigDecimal("200.00"),
                LocalDate.of(2026, 9, 19),
                OptionType.PUT,
                DayCountConvention.ACT_360,
                0.45,
                0.10,
                -0.02,
                0.70,
                0.10,
                0.18,
                0.25,
                3.50,
                3.70,
                800L,
                now
        );

        assertEquals(id, snapshot.id());
        assertEquals("AAPL", snapshot.underlyingSymbol());
        assertEquals(0, new BigDecimal("200.00").compareTo(snapshot.strike()));
        assertEquals(LocalDate.of(2026, 9, 19), snapshot.expiryDate());
        assertEquals(OptionType.PUT, snapshot.type());
        assertEquals(DayCountConvention.ACT_360, snapshot.dayCountConvention());
        assertEquals(0.45, snapshot.delta());
        assertEquals(0.10, snapshot.gamma());
        assertEquals(-0.02, snapshot.theta());
        assertEquals(0.70, snapshot.vega());
        assertEquals(0.10, snapshot.rho());
        assertEquals(0.18, snapshot.impliedVol());
        assertEquals(0.25, snapshot.ttmYears());
        assertEquals(3.50, snapshot.bid());
        assertEquals(3.70, snapshot.ask());
        assertEquals(800L, snapshot.openInterest());
        assertEquals(now, snapshot.observationTime());
    }

    @Test
    void givenZeroStrike_whenConstructing_thenThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> new CdmOptionSnapshot(
                UUID.randomUUID(), "SPY", BigDecimal.ZERO,
                LocalDate.of(2026, 6, 20), OptionType.CALL,
                DayCountConvention.ACT_365_FIXED,
                0.5, 0.1, -0.01, 0.5, 0.1,
                0.2, 0.1, 1.0, 1.2, 100L, Instant.now()
        ));
    }

    @Test
    void givenNegativeStrike_whenConstructing_thenThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> new CdmOptionSnapshot(
                UUID.randomUUID(), "SPY", new BigDecimal("-100.00"),
                LocalDate.of(2026, 6, 20), OptionType.CALL,
                DayCountConvention.ACT_365_FIXED,
                0.5, 0.1, -0.01, 0.5, 0.1,
                0.2, 0.1, 1.0, 1.2, 100L, Instant.now()
        ));
    }

    @Test
    void givenBidGreaterThanAsk_whenConstructing_thenThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> new CdmOptionSnapshot(
                UUID.randomUUID(), "SPY", new BigDecimal("450.00"),
                LocalDate.of(2026, 6, 20), OptionType.CALL,
                DayCountConvention.ACT_365_FIXED,
                0.5, 0.1, -0.01, 0.5, 0.1,
                0.2, 0.1, 5.50, 5.00, 100L, Instant.now()
        ));
    }

    @Test
    void givenNegativeTtmYears_whenConstructing_thenThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> new CdmOptionSnapshot(
                UUID.randomUUID(), "SPY", new BigDecimal("450.00"),
                LocalDate.of(2026, 6, 20), OptionType.CALL,
                DayCountConvention.ACT_365_FIXED,
                0.5, 0.1, -0.01, 0.5, 0.1,
                0.2, -0.01, 5.00, 5.20, 100L, Instant.now()
        ));
    }
}
