package com.tickonomics.cdm.enums;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RateTypeTest {

    @Test
    void givenFloatingRateType_whenToInstrumentType_thenReturnsMatchingInstrument() {
        assertEquals(InstrumentType.SOFR, RateType.SOFR.toInstrumentType());
        assertEquals(InstrumentType.EFFR, RateType.EFFR.toInstrumentType());
        assertEquals(InstrumentType.TGCR, RateType.TGCR.toInstrumentType());
        assertEquals(InstrumentType.BGCR, RateType.BGCR.toInstrumentType());
        assertEquals(InstrumentType.IORB, RateType.IORB.toInstrumentType());
        assertEquals(InstrumentType.OBFR, RateType.OBFR.toInstrumentType());
    }

    @Test
    void givenRepoRateType_whenToInstrumentType_thenReturnsRepo() {
        assertEquals(InstrumentType.REPO, RateType.RRP.toInstrumentType());
        assertEquals(InstrumentType.REPO, RateType.TGA.toInstrumentType());
        assertEquals(InstrumentType.REPO, RateType.WALCL.toInstrumentType());
    }

    @Test
    void givenTBillRateType_whenToInstrumentType_thenReturnsBill3M() {
        assertEquals(InstrumentType.BILL_3M, RateType.TBILL_3M.toInstrumentType());
    }

    @Test
    void givenAllRateTypes_whenToInstrumentType_thenNoneReturnNull() {
        for (RateType rt : RateType.values()) {
            assertNotNull(rt.toInstrumentType(), "RateType." + rt + " returned null instrument type");
        }
    }
}
