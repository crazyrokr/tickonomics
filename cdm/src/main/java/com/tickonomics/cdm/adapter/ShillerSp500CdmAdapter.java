package com.tickonomics.cdm.adapter;

import com.tickonomics.cdm.adapter.raw.ShillerSp500Row;

/**
 * Maps Shiller S&P 500 CSV rows to an index snapshot representation. CAPE values of 0.0
 * (insufficient trailing history for 1871-1880) are preserved as-is for the persistence layer
 * to convert to NULL.
 */
public class ShillerSp500CdmAdapter implements CdmAdapter<ShillerSp500Row, ShillerSp500Row> {
  @Override
  public ShillerSp500Row toCdm(ShillerSp500Row raw) {
    return raw;
  }
}
