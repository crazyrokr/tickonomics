package com.tickonomics.cdm.adapter;

import com.tickonomics.cdm.adapter.raw.NyFedRateResponse;
import com.tickonomics.cdm.mapper.CdmInstrumentMapper;
import com.tickonomics.cdm.model.CdmInstrumentRef;
import com.tickonomics.cdm.model.CdmRateSnapshot;

/**
 * Maps raw NY Fed rate responses to CDM rate snapshots.
 * Translates NY Fed rate types to CDM-aligned instrument types.
 */
public class NyFedCdmAdapter implements CdmAdapter<NyFedRateResponse, CdmRateSnapshot> {

    @Override
    public CdmRateSnapshot toCdm(NyFedRateResponse raw) {
        CdmInstrumentRef ref = CdmInstrumentMapper.fromNyFedRate(raw.rateType());
        return new CdmRateSnapshot(
                raw.time(),
                ref.instrumentType(),
                raw.value(),
                raw.source()
        );
    }
}
