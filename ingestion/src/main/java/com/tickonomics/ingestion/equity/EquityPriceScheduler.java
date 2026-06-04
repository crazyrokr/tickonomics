package com.tickonomics.ingestion.equity;

import com.tickonomics.cdm.adapter.FinnhubEquityCdmAdapter;
import com.tickonomics.cdm.adapter.YahooEquityCdmAdapter;
import com.tickonomics.cdm.adapter.raw.YahooOhlcv;
import com.tickonomics.cdm.model.CdmTick;
import com.tickonomics.ingestion.writer.TimescaleDbWriter;
import com.tickonomics.persistence.entity.TickData;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Scheduled polling coordinator for equity price data. Tries the primary Yahoo Finance client first,
 * falls back to Finnhub on failure. Writes CDM-adapted ticks through the existing TimescaleDbWriter
 * pipeline.
 */
@Component
@ConditionalOnProperty(name = "monitor.equity-price.enabled", havingValue = "true", matchIfMissing = true)
public class EquityPriceScheduler {

  private static final Logger log = LoggerFactory.getLogger(EquityPriceScheduler.class);
  private static final List<String> DEFAULT_SYMBOLS = List.of("SPY", "QQQ", "IWM", "TLT", "HYG", "GLD");

  private final List<EquityPriceClient> clients;
  private final TimescaleDbWriter writer;
  private final YahooEquityCdmAdapter yahooAdapter;
  private final FinnhubEquityCdmAdapter finnhubAdapter;
  private final List<String> symbols;

  public EquityPriceScheduler(
      List<EquityPriceClient> clients,
      TimescaleDbWriter writer,
      YahooEquityCdmAdapter yahooAdapter,
      FinnhubEquityCdmAdapter finnhubAdapter,
      @Value("${monitor.equity-price.symbols:SPY,QQQ,IWM,TLT,HYG,GLD}") List<String> symbols) {
    this.clients = clients;
    this.writer = writer;
    this.yahooAdapter = yahooAdapter;
    this.finnhubAdapter = finnhubAdapter;
    this.symbols = symbols != null && !symbols.isEmpty() ? symbols : DEFAULT_SYMBOLS;
  }

  @Scheduled(fixedDelayString = "${monitor.equity-price.poll-interval-ms:21600000}")
  @Bulkhead(name = "highVolumeIngestion")
  public void pollEquityPrices() {
    for (String symbol : symbols) {
      boolean fetched = false;

      for (EquityPriceClient client : clients) {
        try {
          List<YahooOhlcv> bars = client.fetchHistoricalOhlcv(symbol, "1d");
          if (!bars.isEmpty()) {
            for (YahooOhlcv bar : bars) {
              CdmTick tick = yahooAdapter.toCdm(bar);
              writer.writeTick(toEntity(tick));
            }
            log.info("Fetched {} OHLCV bars for {} from {}", bars.size(), symbol, client.sourceName());
            fetched = true;
            break;
          }
        } catch (Exception e) {
          log.warn("Client {} failed for {}: {}", client.sourceName(), symbol, e.getMessage());
        }
      }

      if (!fetched) {
        log.warn("All equity price clients failed for symbol {}", symbol);
      }
    }
  }

  private TickData toEntity(CdmTick cdm) {
    return new TickData(cdm.time(), cdm.symbol(), cdm.price(), cdm.volume(), cdm.conditions());
  }
}
