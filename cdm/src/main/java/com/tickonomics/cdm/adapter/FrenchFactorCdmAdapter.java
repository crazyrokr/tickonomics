package com.tickonomics.cdm.adapter;

import com.tickonomics.cdm.adapter.raw.FrenchFactorRow;
import com.tickonomics.cdm.model.FactorReturn;

/**
 * Maps parsed Ken French CSV rows to CDM FactorReturn records. Handles the NaN sentinel values
 * used by the Ken French Data Library for missing observations.
 */
public class FrenchFactorCdmAdapter implements CdmAdapter<FrenchFactorRow, FactorReturn> {

  @Override
  public FactorReturn toCdm(FrenchFactorRow raw) {
    return new FactorReturn(
        raw.time(), raw.factorSet(), raw.frequency(), "US",
        sanitize(raw.rmRf()), sanitize(raw.smb()), sanitize(raw.hml()),
        sanitize(raw.rmw()), sanitize(raw.cma()), sanitize(raw.rf()),
        sanitize(raw.mom()), sanitize(raw.stRev()), sanitize(raw.ltRev()));
  }

  private static double sanitize(double value) {
    return FactorReturn.isMissing(value) ? Double.NaN : value / 100.0;
  }
}
