package com.tickonomics.computation.demo;

import com.tickonomics.persistence.entity.DailyClose;
import com.tickonomics.persistence.repository.OhlcvDailyRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class OhlcvDailyMarketPriceLookup implements MarketPriceLookup {

  private final OhlcvDailyRepository ohlcvDailyRepository;

  public OhlcvDailyMarketPriceLookup(OhlcvDailyRepository ohlcvDailyRepository) {
    this.ohlcvDailyRepository = ohlcvDailyRepository;
  }

  @Override
  public Optional<Double> closingPrice(String symbol, LocalDate day) {
    return ohlcvDailyRepository.findClose(symbol, day);
  }

  @Override
  public List<Double> closingPrices(String symbol, LocalDate from, LocalDate toExclusive) {
    return ohlcvDailyRepository.findClosesBetween(symbol, from, toExclusive).stream()
        .map(DailyClose::close)
        .toList();
  }
}
