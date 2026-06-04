package com.tickonomics.cdm.adapter;

import com.tickonomics.cdm.adapter.raw.DataHubPriceRow;
import com.tickonomics.cdm.model.CdmRateSnapshot;
import com.tickonomics.cdm.enums.InstrumentType;

/**
 * Maps DataHub gold price rows to CDM rate snapshots with rate_type='GOLD'.
 */
public class GoldPriceCdmAdapter implements CdmAdapter<DataHubPriceRow, CdmRateSnapshot> {
  @Override
  public CdmRateSnapshot toCdm(DataHubPriceRow raw) {
    return new CdmRateSnapshot(raw.time(), InstrumentType.COMMODITY_GOLD, raw.value(), "DATAHUB_GOLD");
  }
}
