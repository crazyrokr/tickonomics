package com.tickonomics.cdm.adapter;

import com.tickonomics.cdm.adapter.raw.YahooOhlcv;
import com.tickonomics.cdm.model.CdmTick;
import java.math.BigDecimal;

/**
 * Maps raw Yahoo Finance OHLCV data to CDM tick objects. Uses close price as the tick price and
 * carries full OHLCV volume.
 */
public class YahooEquityCdmAdapter implements CdmAdapter<YahooOhlcv, CdmTick> {

  @Override
  public CdmTick toCdm(YahooOhlcv raw) {
    return new CdmTick(raw.time(), raw.symbol(),
        BigDecimal.valueOf(raw.close()), raw.volume(), null);
  }
}
