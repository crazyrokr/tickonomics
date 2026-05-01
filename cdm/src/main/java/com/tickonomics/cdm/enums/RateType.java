package com.tickonomics.cdm.enums;

/**
 * CDM-aligned rate type identifiers for rate snapshots. Values correspond to the CDM projection and CHECK constraints
 * in V7 migration.
 */
public enum RateType {
  SOFR,
  EFFR,
  TGCR,
  BGCR,
  IORB,
  OBFR,
  RRP,
  TGA,
  WALCL,
  TBILL_3M;

  /**
   * Resolves the InstrumentType used for grouping in the ILI computation pipeline.
   */
  public InstrumentType toInstrumentType() {
    return switch (this) {
      case SOFR, EFFR, TGCR, BGCR, IORB, OBFR -> InstrumentType.valueOf(name());
      case RRP, TGA, WALCL -> InstrumentType.REPO;
      case TBILL_3M -> InstrumentType.BILL_3M;
    };
  }
}
