package com.tickonomics.cdm.mapper;

import com.tickonomics.cdm.enums.InstrumentType;
import com.tickonomics.cdm.enums.RateType;
import com.tickonomics.cdm.model.CdmInstrumentRef;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CdmInstrumentMapperTest {

    @Nested
    class FromFredSeries {
        @Test
        void givenEffrSeries_whenMapped_thenReturnsEffrInstrument() {
            var ref = CdmInstrumentMapper.fromFredSeries("EFFR");
            assertEquals("EFFR", ref.identifier());
            assertEquals(InstrumentType.EFFR, ref.instrumentType());
            assertEquals("FRED", ref.source());
        }

        @Test
        void givenRrpSeries_whenMapped_thenReturnsRepoInstrument() {
            var ref = CdmInstrumentMapper.fromFredSeries("RRPONTSYD");
            assertEquals("RRP", ref.identifier());
            assertEquals(InstrumentType.REPO, ref.instrumentType());
        }

        @Test
        void givenTgaSeries_whenMapped_thenReturnsRepoInstrument() {
            var ref = CdmInstrumentMapper.fromFredSeries("WTREGEN");
            assertEquals("TGA", ref.identifier());
            assertEquals(InstrumentType.REPO, ref.instrumentType());
        }

        @Test
        void givenWalclSeries_whenMapped_thenReturnsRepoInstrument() {
            var ref = CdmInstrumentMapper.fromFredSeries("WALCL");
            assertEquals("WALCL", ref.identifier());
            assertEquals(InstrumentType.REPO, ref.instrumentType());
        }

        @Test
        void givenIorbSeries_whenMapped_thenReturnsIorbInstrument() {
            var ref = CdmInstrumentMapper.fromFredSeries("IORB");
            assertEquals(InstrumentType.IORB, ref.instrumentType());
        }

        @Test
        void givenUnknownSeries_whenMapped_thenThrows() {
            assertThrows(IllegalArgumentException.class,
                    () -> CdmInstrumentMapper.fromFredSeries("UNKNOWN"));
        }
    }

    @Nested
    class FromNyFedRate {
        @Test
        void givenSofrRate_whenMapped_thenReturnsSofrInstrument() {
            var ref = CdmInstrumentMapper.fromNyFedRate("sofr");
            assertEquals("SOFR", ref.identifier());
            assertEquals(InstrumentType.SOFR, ref.instrumentType());
            assertEquals("NY_FED", ref.source());
        }

        @Test
        void givenTgcrRate_whenMapped_thenReturnsTgcrInstrument() {
            var ref = CdmInstrumentMapper.fromNyFedRate("tgcr");
            assertEquals(InstrumentType.TGCR, ref.instrumentType());
        }

        @Test
        void givenBgcrRate_whenMapped_thenReturnsBgcrInstrument() {
            var ref = CdmInstrumentMapper.fromNyFedRate("bgcr");
            assertEquals(InstrumentType.BGCR, ref.instrumentType());
        }

        @Test
        void givenUnknownRate_whenMapped_thenThrows() {
            assertThrows(IllegalArgumentException.class,
                    () -> CdmInstrumentMapper.fromNyFedRate("unknown"));
        }
    }

    @Nested
    class FromPolygonSymbol {
        @Test
        void givenSpySymbol_whenMapped_thenReturnsEquityInstrument() {
            var ref = CdmInstrumentMapper.fromPolygonSymbol("SPY");
            assertEquals("SPY", ref.identifier());
            assertEquals(InstrumentType.EQUITY, ref.instrumentType());
            assertEquals("POLYGON", ref.source());
        }
    }

    @Nested
    class FromRateType {
        @Test
        void givenAllRateTypes_whenMapped_thenAllResolve() {
            for (RateType rt : RateType.values()) {
                CdmInstrumentRef ref = CdmInstrumentMapper.fromRateType(rt);
                assertNotNull(ref);
                assertEquals(rt.name(), ref.identifier());
                assertEquals(rt.toInstrumentType(), ref.instrumentType());
            }
        }
    }
}
