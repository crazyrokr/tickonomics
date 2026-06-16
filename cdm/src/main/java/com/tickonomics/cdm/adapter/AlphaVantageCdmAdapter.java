package com.tickonomics.cdm.adapter;

import com.tickonomics.cdm.adapter.raw.AlphaVantageDailyBar;
import com.tickonomics.cdm.model.CdmTick;
import java.math.BigDecimal;

/**
 * Maps Alpha Vantage adjusted daily OHLCV bars to CDM tick objects. Uses the adjusted close price
 * (split + dividend corrected) as the canonical tick price.
 */
public class AlphaVantageCdmAdapter implements CdmAdapter<AlphaVantageDailyBar, CdmTick> {

  @Override
  public CdmTick toCdm(AlphaVantageDailyBar raw) {
    return new CdmTick(raw.time(), raw.symbol(),
        BigDecimal.valueOf(raw.adjustedClose()), raw.volume(), null);
  }
}
