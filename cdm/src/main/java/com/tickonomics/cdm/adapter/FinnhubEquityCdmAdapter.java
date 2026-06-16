package com.tickonomics.cdm.adapter;

import com.tickonomics.cdm.adapter.raw.FinnhubQuote;
import com.tickonomics.cdm.model.CdmTick;
import java.math.BigDecimal;

/**
 * Maps raw Finnhub real-time quotes to CDM tick objects. Uses the current price as the tick price.
 */
public class FinnhubEquityCdmAdapter implements CdmAdapter<FinnhubQuote, CdmTick> {

  @Override
  public CdmTick toCdm(FinnhubQuote raw) {
    return new CdmTick(raw.time(), raw.symbol(),
        BigDecimal.valueOf(raw.currentPrice()), raw.volume(), null);
  }
}
