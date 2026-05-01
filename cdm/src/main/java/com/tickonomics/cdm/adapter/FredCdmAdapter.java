package com.tickonomics.cdm.adapter;

import com.tickonomics.cdm.adapter.raw.FredObservation;
import com.tickonomics.cdm.mapper.CdmInstrumentMapper;
import com.tickonomics.cdm.model.CdmInstrumentRef;
import com.tickonomics.cdm.model.CdmRateSnapshot;

/**
 * Maps raw FRED observations to CDM rate snapshots. Translates FRED series IDs to CDM-aligned instrument types.
 */
public class FredCdmAdapter implements CdmAdapter<FredObservation, CdmRateSnapshot> {

  @Override
  public CdmRateSnapshot toCdm(FredObservation raw) {
    CdmInstrumentRef ref = CdmInstrumentMapper.fromFredSeries(raw.seriesId());
    return new CdmRateSnapshot(raw.time(), ref.instrumentType(), raw.value(), raw.source());
  }
}
