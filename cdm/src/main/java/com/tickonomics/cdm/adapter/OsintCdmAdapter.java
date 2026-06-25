package com.tickonomics.cdm.adapter;

import com.tickonomics.cdm.adapter.raw.OsintEvent;
import com.tickonomics.cdm.model.CdmNewsEvent;

/**
 * Maps raw OSINT (GDELT) events to canonical news events. Identity-style mapping that preserves the
 * GDELT tone signal required by the perspective-mismatch engine.
 */
public class OsintCdmAdapter implements CdmAdapter<OsintEvent, CdmNewsEvent> {

  @Override
  public CdmNewsEvent toCdm(OsintEvent raw) {
    return new CdmNewsEvent(
        raw.time(), raw.eventId(), raw.source(), raw.headline(),
        raw.avgTone(), raw.themes(), raw.actors(), raw.url());
  }
}
