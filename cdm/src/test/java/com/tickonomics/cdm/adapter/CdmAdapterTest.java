package com.tickonomics.cdm.adapter;

import com.tickonomics.cdm.adapter.raw.FredObservation;
import com.tickonomics.cdm.adapter.raw.NyFedRateResponse;
import com.tickonomics.cdm.adapter.raw.PolygonTick;
import com.tickonomics.cdm.enums.InstrumentType;
import com.tickonomics.cdm.model.CdmRateSnapshot;
import com.tickonomics.cdm.model.CdmTick;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class CdmAdapterTest {

    private static final Instant NOW = Instant.parse("2026-05-23T12:00:00Z");

    @Nested
    class FredCdmAdapterTests {
        private final FredCdmAdapter adapter = new FredCdmAdapter();

        @Test
        void givenFredEffrObservation_whenAdapt_thenReturnsCdmRateSnapshot() {
            var raw = new FredObservation(NOW, "EFFR", 4.33, "FRED");
            CdmRateSnapshot result = adapter.toCdm(raw);
            assertEquals(NOW, result.time());
            assertEquals(InstrumentType.EFFR, result.instrumentType());
            assertEquals(4.33, result.value());
            assertEquals("FRED", result.source());
        }

        @Test
        void givenFredRrpObservation_whenAdapt_thenReturnsRepoInstrumentType() {
            var raw = new FredObservation(NOW, "RRPONTSYD", 0.0, "FRED");
            CdmRateSnapshot result = adapter.toCdm(raw);
            assertEquals(InstrumentType.REPO, result.instrumentType());
        }

        @Test
        void givenFredIorbObservation_whenAdapt_thenReturnsIorbInstrumentType() {
            var raw = new FredObservation(NOW, "IORB", 4.40, "FRED");
            CdmRateSnapshot result = adapter.toCdm(raw);
            assertEquals(InstrumentType.IORB, result.instrumentType());
        }
    }

    @Nested
    class NyFedCdmAdapterTests {
        private final NyFedCdmAdapter adapter = new NyFedCdmAdapter();

        @Test
        void givenNyFedSofrResponse_whenAdapt_thenReturnsCdmRateSnapshot() {
            var raw = new NyFedRateResponse(NOW, "sofr", 4.29, "NY_FED");
            CdmRateSnapshot result = adapter.toCdm(raw);
            assertEquals(NOW, result.time());
            assertEquals(InstrumentType.SOFR, result.instrumentType());
            assertEquals(4.29, result.value());
            assertEquals("NY_FED", result.source());
        }

        @Test
        void givenNyFedTgcrResponse_whenAdapt_thenReturnsTgcrInstrumentType() {
            var raw = new NyFedRateResponse(NOW, "tgcr", 4.28, "NY_FED");
            CdmRateSnapshot result = adapter.toCdm(raw);
            assertEquals(InstrumentType.TGCR, result.instrumentType());
        }

        @Test
        void givenNyFedBgcrResponse_whenAdapt_thenReturnsBgcrInstrumentType() {
            var raw = new NyFedRateResponse(NOW, "bgcr", 4.30, "NY_FED");
            CdmRateSnapshot result = adapter.toCdm(raw);
            assertEquals(InstrumentType.BGCR, result.instrumentType());
        }
    }

    @Nested
    class PolygonTickCdmAdapterTests {
        private final PolygonTickCdmAdapter adapter = new PolygonTickCdmAdapter();

        @Test
        void givenPolygonTick_whenAdapt_thenReturnsCdmTick() {
            var raw = new PolygonTick(NOW, "SPY", 450.50, 1000, new int[]{0, 1});
            CdmTick result = adapter.toCdm(raw);
            assertEquals(NOW, result.time());
            assertEquals("SPY", result.symbol());
            assertEquals(450.50, result.price());
            assertEquals(1000, result.volume());
            assertArrayEquals(new int[]{0, 1}, result.conditions());
        }

        @Test
        void givenPolygonTickWithZeroVolume_whenAdapt_thenSucceeds() {
            var raw = new PolygonTick(NOW, "QQQ", 380.0, 0, new int[]{});
            CdmTick result = adapter.toCdm(raw);
            assertEquals(0, result.volume());
        }
    }
}
