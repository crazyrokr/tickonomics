package com.tickonomics.cdm.adapter;

import com.tickonomics.cdm.adapter.raw.YahooOptionContract;
import com.tickonomics.cdm.enums.DayCountConvention;
import com.tickonomics.cdm.enums.OptionType;
import com.tickonomics.cdm.model.CdmOptionSnapshot;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Maps Yahoo Finance options contract data to CDM option snapshot records. Converts string-based
 * option types to CDM enums and computes time-to-maturity in years.
 */
public class YahooOptionsCdmAdapter implements CdmAdapter<YahooOptionContract, CdmOptionSnapshot> {

  @Override
  public CdmOptionSnapshot toCdm(YahooOptionContract raw) {
    OptionType type = parseOptionType(raw.optionType());
    double ttmYears = computeTtmYears(raw.expiry());

    double ask = raw.ask();
    double bid = raw.bid();
    if (ask < bid) {
      ask = bid;
    }

    return new CdmOptionSnapshot(
        UUID.randomUUID(),
        raw.underlying(),
        raw.strike(),
        raw.expiry(),
        type,
        DayCountConvention.ACT_365_FIXED,
        raw.delta(),
        raw.gamma(),
        raw.theta(),
        raw.vega(),
        raw.rho(),
        raw.impliedVol(),
        ttmYears,
        bid,
        ask,
        raw.openInterest(),
        raw.time());
  }

  private double computeTtmYears(LocalDate expiry) {
    long days = ChronoUnit.DAYS.between(LocalDate.now(), expiry);
    return Math.max(0, days / 365.0);
  }

  private static OptionType parseOptionType(String raw) {
    var normalized = raw.trim().toUpperCase();
    return switch (normalized) {
      case "CALL", "C" -> OptionType.CALL;
      case "PUT", "P" -> OptionType.PUT;
      default -> throw new IllegalArgumentException("Unknown optionType: " + raw);
    };
  }
}
