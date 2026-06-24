package com.tickonomics.cdm.adapter;

import com.tickonomics.cdm.adapter.raw.PolymarketQuote;
import com.tickonomics.cdm.model.CdmPredictionMarketQuote;

/**
 * Maps raw Polymarket quotes to canonical prediction-market quotes. Drops the provider slug, which is
 * not needed downstream.
 */
public class PolymarketCdmAdapter implements CdmAdapter<PolymarketQuote, CdmPredictionMarketQuote> {

  @Override
  public CdmPredictionMarketQuote toCdm(PolymarketQuote raw) {
    return new CdmPredictionMarketQuote(
        raw.time(), raw.marketId(), raw.question(),
        raw.outcomeYesPrice(), raw.volume(), raw.liquidity(), raw.source());
  }
}
