package com.tickonomics.cdm.adapter;

import com.tickonomics.cdm.adapter.raw.DataHubPriceRow;
import com.tickonomics.cdm.model.CdmRateSnapshot;
import com.tickonomics.cdm.enums.InstrumentType;

/**
 * Maps DataHub VIX daily price rows to CDM rate snapshots with rate_type='VIX'.
 */
public class VixCdmAdapter implements CdmAdapter<DataHubPriceRow, CdmRateSnapshot> {
  @Override
  public CdmRateSnapshot toCdm(DataHubPriceRow raw) {
    return new CdmRateSnapshot(raw.time(), InstrumentType.EQUITY, raw.value(), "DATAHUB_VIX");
  }
}
