package com.tickonomics.cdm.adapter;

import com.tickonomics.cdm.adapter.raw.DataHubPriceRow;
import com.tickonomics.cdm.model.CdmRateSnapshot;
import com.tickonomics.cdm.enums.InstrumentType;

/**
 * Maps DataHub oil price rows (WTI or Brent) to CDM rate snapshots. The rateType parameter
 * distinguishes between WTI and Brent benchmarks.
 */
public class OilPriceCdmAdapter implements CdmAdapter<DataHubPriceRow, CdmRateSnapshot> {

  private final String sourceName;

  public OilPriceCdmAdapter(String sourceName) {
    this.sourceName = sourceName;
  }

  @Override
  public CdmRateSnapshot toCdm(DataHubPriceRow raw) {
    return new CdmRateSnapshot(raw.time(), InstrumentType.COMMODITY_OIL, raw.value(), sourceName);
  }
}
