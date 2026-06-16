package com.tickonomics.cdm.adapter;

import com.tickonomics.cdm.adapter.raw.FinnhubTrade;
import com.tickonomics.cdm.model.CdmTick;
import java.math.BigDecimal;

/**
 * Maps normalized WebSocket trade events to CDM tick objects. Provider-agnostic — works with any
 * trade event that has been mapped to FinnhubTrade.
 */
public class WsTradeCdmAdapter implements CdmAdapter<FinnhubTrade, CdmTick> {

  @Override
  public CdmTick toCdm(FinnhubTrade raw) {
    return new CdmTick(raw.time(), raw.symbol(),
        BigDecimal.valueOf(raw.price()), raw.volume(), null);
  }
}
