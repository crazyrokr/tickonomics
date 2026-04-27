package com.tickonomics.cdm.model;

import com.tickonomics.cdm.enums.InstrumentType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CdmInstrumentRefTest {

    @Test
    void givenValidArgs_whenConstruct_thenFieldsSet() {
        var ref = new CdmInstrumentRef("SPY", InstrumentType.EQUITY, "POLYGON");
        assertEquals("SPY", ref.identifier());
        assertEquals(InstrumentType.EQUITY, ref.instrumentType());
        assertEquals("POLYGON", ref.source());
    }

    @Test
    void givenNullIdentifier_whenConstruct_thenThrows() {
        assertThrows(NullPointerException.class,
                () -> new CdmInstrumentRef(null, InstrumentType.EQUITY, "POLYGON"));
    }

    @Test
    void givenNullInstrumentType_whenConstruct_thenThrows() {
        assertThrows(NullPointerException.class,
                () -> new CdmInstrumentRef("SPY", null, "POLYGON"));
    }

    @Test
    void givenNullSource_whenConstruct_thenThrows() {
        assertThrows(NullPointerException.class,
                () -> new CdmInstrumentRef("SPY", InstrumentType.EQUITY, null));
    }
}
