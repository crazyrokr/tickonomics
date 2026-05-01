package com.tickonomics.cdm.model;

import com.tickonomics.cdm.enums.InstrumentType;

import java.util.Objects;

/**
 * Instrument reference linking to CDM product taxonomy. Provides canonical identification for instruments consumed by
 * tickonomics.
 */
public record CdmInstrumentRef(
    String identifier, InstrumentType instrumentType, String source) {
  public CdmInstrumentRef {
    Objects.requireNonNull(identifier, "identifier must not be null");
    Objects.requireNonNull(instrumentType, "instrumentType must not be null");
    Objects.requireNonNull(source, "source must not be null");
  }
}
