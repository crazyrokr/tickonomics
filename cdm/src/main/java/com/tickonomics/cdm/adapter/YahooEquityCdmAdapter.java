package com.tickonomics.cdm.adapter;

import com.tickonomics.cdm.adapter.raw.YahooOhlcv;
import com.tickonomics.cdm.model.CdmTick;

/**
 * Maps raw Yahoo Finance OHLCV data to CDM tick objects. Uses close price as the tick price and
 * carries full OHLCV volume.
 */
public class YahooEquityCdmAdapter implements CdmAdapter<YahooOhlcv, CdmTick> {

  @Override
  public CdmTick toCdm(YahooOhlcv raw) {
    return new CdmTick(raw.time(), raw.symbol(), raw.close(), raw.volume(), null);
  }
}
