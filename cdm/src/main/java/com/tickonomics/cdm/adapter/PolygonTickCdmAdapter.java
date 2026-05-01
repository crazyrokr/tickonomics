package com.tickonomics.cdm.adapter;

import com.tickonomics.cdm.adapter.raw.PolygonTick;
import com.tickonomics.cdm.model.CdmTick;

/**
 * Maps raw Polygon WebSocket ticks to CDM tick objects. Direct field mapping — Polygon already provides normalized tick
 * data.
 */
public class PolygonTickCdmAdapter implements CdmAdapter<PolygonTick, CdmTick> {

  @Override
  public CdmTick toCdm(PolygonTick raw) {
    return new CdmTick(raw.time(), raw.symbol(), raw.price(), raw.volume(), raw.conditions());
  }
}
