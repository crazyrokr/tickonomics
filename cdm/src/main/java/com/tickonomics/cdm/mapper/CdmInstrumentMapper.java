package com.tickonomics.cdm.mapper;

import com.tickonomics.cdm.enums.InstrumentType;
import com.tickonomics.cdm.enums.RateType;
import com.tickonomics.cdm.model.CdmInstrumentRef;

/**
 * Converts between internal domain types and CDM types.
 * Provides canonical instrument references for the ILI computation pipeline.
 */
public final class CdmInstrumentMapper {

    private CdmInstrumentMapper() {}

    /**
     * Maps a FRED series ID to a CDM instrument reference.
     *
     * @param fredSeriesId FRED series identifier (e.g., "EFFR", "RRPONTSYD")
     * @return CDM instrument reference
     * @throws IllegalArgumentException if the series ID is unknown
     */
    public static CdmInstrumentRef fromFredSeries(String fredSeriesId) {
        return switch (fredSeriesId) {
            case "EFFR" -> new CdmInstrumentRef("EFFR", InstrumentType.EFFR, "FRED");
            case "RRPONTSYD" -> new CdmInstrumentRef("RRP", InstrumentType.REPO, "FRED");
            case "WTREGEN" -> new CdmInstrumentRef("TGA", InstrumentType.REPO, "FRED");
            case "WALCL" -> new CdmInstrumentRef("WALCL", InstrumentType.REPO, "FRED");
            case "IORB" -> new CdmInstrumentRef("IORB", InstrumentType.IORB, "FRED");
            default -> throw new IllegalArgumentException("Unknown FRED series: " + fredSeriesId);
        };
    }

    /**
     * Maps a NY Fed rate type string to a CDM instrument reference.
     *
     * @param nyFedRateType NY Fed rate type (e.g., "sofr", "tgcr")
     * @return CDM instrument reference
     * @throws IllegalArgumentException if the rate type is unknown
     */
    public static CdmInstrumentRef fromNyFedRate(String nyFedRateType) {
        return switch (nyFedRateType.toLowerCase()) {
            case "sofr" -> new CdmInstrumentRef("SOFR", InstrumentType.SOFR, "NY_FED");
            case "tgcr" -> new CdmInstrumentRef("TGCR", InstrumentType.TGCR, "NY_FED");
            case "bgcr" -> new CdmInstrumentRef("BGCR", InstrumentType.BGCR, "NY_FED");
            default -> throw new IllegalArgumentException("Unknown NY Fed rate type: " + nyFedRateType);
        };
    }

    /**
     * Maps a Polygon equity symbol to a CDM instrument reference.
     */
    public static CdmInstrumentRef fromPolygonSymbol(String symbol) {
        return new CdmInstrumentRef(symbol, InstrumentType.EQUITY, "POLYGON");
    }

    /**
     * Maps a RateType to a CDM instrument reference.
     */
    public static CdmInstrumentRef fromRateType(RateType rateType) {
        return new CdmInstrumentRef(rateType.name(), rateType.toInstrumentType(), rateType.name());
    }
}
