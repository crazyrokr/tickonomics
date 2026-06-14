package com.tickonomics.ingestion.datahub;

import com.tickonomics.cdm.adapter.GoldPriceCdmAdapter;
import com.tickonomics.cdm.adapter.OilPriceCdmAdapter;
import com.tickonomics.cdm.adapter.ShillerSp500CdmAdapter;
import com.tickonomics.cdm.adapter.VixCdmAdapter;
import com.tickonomics.cdm.adapter.raw.DataHubPriceRow;
import com.tickonomics.cdm.adapter.raw.ShillerSp500Row;
import com.tickonomics.cdm.model.CdmRateSnapshot;
import com.tickonomics.ingestion.writer.TimescaleDbWriter;
import com.tickonomics.persistence.entity.RateSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * DataHub historical CSV backfill client. Fetches deep-historical datasets from DataHub CDN
 * (S&P 500 Shiller, VIX, oil WTI/Brent, gold) as CSV files. Supports full load on startup
 * when target tables are empty and incremental monthly updates.
 */
@Component
@ConditionalOnProperty(name = "monitor.datahub.enabled", havingValue = "true", matchIfMissing = false)
public class DataHubBackfillClient {

  private static final Logger log = LoggerFactory.getLogger(DataHubBackfillClient.class);
  private static final DateTimeFormatter YEAR_MONTH_FMT = DateTimeFormatter.ofPattern("yyyy-MM");
  private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

  private final RestClient restClient;
  private final TimescaleDbWriter writer;

  private final ShillerSp500CdmAdapter shillerAdapter = new ShillerSp500CdmAdapter();
  private final VixCdmAdapter vixAdapter = new VixCdmAdapter();
  private final OilPriceCdmAdapter wtiAdapter = new OilPriceCdmAdapter("DATAHUB_OIL_WTI");
  private final OilPriceCdmAdapter brentAdapter = new OilPriceCdmAdapter("DATAHUB_OIL_BRENT");
  private final GoldPriceCdmAdapter goldAdapter = new GoldPriceCdmAdapter();

  @Value("${monitor.datahub.base-url:https://datahub.io}")
  private String baseUrl;

  public DataHubBackfillClient(RestClient.Builder restClientBuilder, TimescaleDbWriter writer) {
    this.restClient = restClientBuilder != null ? restClientBuilder.build() : null;
    this.writer = writer;
  }

  public void backfillAll() {
    backfillShiller();
    backfillVix();
    backfillOilWti();
    backfillOilBrent();
    backfillGold();
  }

  public void backfillShiller() {
    String url = baseUrl + "/core/s-and-p-500/_r/-/data/data.csv";
    byte[] csv = fetchCsv(url);
    if (csv == null) return;

    List<ShillerSp500Row> rows = parseShillerCsv(csv);
    for (ShillerSp500Row row : rows) {
      shillerAdapter.toCdm(row);
    }
    log.info("Backfilled {} Shiller S&P 500 rows", rows.size());
  }

  public void backfillVix() {
    String url = baseUrl + "/core/finance-vix/_r/-/data/vix-daily.csv";
    backfillPriceCsv(url, "VIX", vixAdapter);
  }

  public void backfillOilWti() {
    String url = baseUrl + "/core/oil-prices/_r/-/data/wti-daily.csv";
    backfillPriceCsv(url, "OIL_WTI", wtiAdapter);
  }

  public void backfillOilBrent() {
    String url = baseUrl + "/core/oil-prices/_r/-/data/brent-daily.csv";
    backfillPriceCsv(url, "OIL_BRENT", brentAdapter);
  }

  public void backfillGold() {
    String url = baseUrl + "/core/gold-prices/_r/-/data/monthly.csv";
    backfillPriceCsv(url, "GOLD", goldAdapter);
  }

  private void backfillPriceCsv(String url, String rateType, Object adapter) {
    byte[] csv = fetchCsv(url);
    if (csv == null) return;

    List<DataHubPriceRow> rows = parsePriceCsv(csv, rateType);
    for (DataHubPriceRow row : rows) {
      if (adapter instanceof VixCdmAdapter vix) {
        CdmRateSnapshot snapshot = vix.toCdm(row);
        writer.writeRate(toEntity(snapshot));
      } else if (adapter instanceof OilPriceCdmAdapter oil) {
        CdmRateSnapshot snapshot = oil.toCdm(row);
        writer.writeRate(toEntity(snapshot));
      } else if (adapter instanceof GoldPriceCdmAdapter gold) {
        CdmRateSnapshot snapshot = gold.toCdm(row);
        writer.writeRate(toEntity(snapshot));
      }
    }
    log.info("Backfilled {} {} rows", rows.size(), rateType);
  }

  private byte[] fetchCsv(String url) {
    try {
      return restClient.get()
          .uri(url)
          .retrieve()
          .body(byte[].class);
    } catch (Exception e) {
      log.error("Failed to fetch CSV from {}: {}", url, e.getMessage());
      return null;
    }
  }

  List<ShillerSp500Row> parseShillerCsv(byte[] csv) {
    try (var reader = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(csv)))) {
      List<ShillerSp500Row> rows = new ArrayList<>();
      String header = reader.readLine();
      String line;

      while ((line = reader.readLine()) != null) {
        line = line.trim();
        if (line.isEmpty()) continue;

        String[] parts = line.split(",", -1);
        if (parts.length < 5) continue;

        Instant time = parseShillerDate(parts[0].trim());
        if (time == null) continue;

        double price = parseDouble(parts[1]);
        if (Double.isNaN(price) || price <= 0) continue;

        rows.add(new ShillerSp500Row(
            time, price,
            parseDouble(parts[2]),
            parseDouble(parts[3]),
            parseDouble(parts[4]),
            parts.length > 5 ? parseDouble(parts[5]) : Double.NaN,
            parts.length > 6 ? parseDouble(parts[6]) : Double.NaN,
            parts.length > 7 ? parseDouble(parts[7]) : Double.NaN,
            parts.length > 8 ? parseDouble(parts[8]) : Double.NaN,
            parts.length > 9 ? parseDouble(parts[9]) : Double.NaN));
      }

      return rows;
    } catch (Exception e) {
      log.error("Failed to parse Shiller CSV: {}", e.getMessage());
      return List.of();
    }
  }

  List<DataHubPriceRow> parsePriceCsv(byte[] csv, String rateType) {
    try (var reader = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(csv)))) {
      List<DataHubPriceRow> rows = new ArrayList<>();
      String header = reader.readLine();
      String line;

      while ((line = reader.readLine()) != null) {
        line = line.trim();
        if (line.isEmpty()) continue;

        String[] parts = line.split(",", -1);
        if (parts.length < 2) continue;

        String dateStr = parts[0].trim();
        double value = parseDouble(parts[1]);
        if (Double.isNaN(value) || value <= 0) continue;

        Instant time = parseDate(dateStr);
        if (time == null) continue;

        rows.add(new DataHubPriceRow(time, value));
      }

      return rows;
    } catch (Exception e) {
      log.error("Failed to parse {} CSV: {}", rateType, e.getMessage());
      return List.of();
    }
  }

  private Instant parseShillerDate(String dateStr) {
    try {
      if (dateStr.length() == 7) {
        return YearMonth.parse(dateStr, YEAR_MONTH_FMT)
            .atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC);
      }
      return null;
    } catch (Exception e) {
      return null;
    }
  }

  private Instant parseDate(String dateStr) {
    try {
      if (dateStr.length() == 10) {
        return LocalDate.parse(dateStr, DATE_FMT)
            .atStartOfDay().toInstant(ZoneOffset.UTC);
      } else if (dateStr.length() == 7) {
        return YearMonth.parse(dateStr, YEAR_MONTH_FMT)
            .atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC);
      }
      return null;
    } catch (Exception e) {
      return null;
    }
  }

  private double parseDouble(String value) {
    try {
      return Double.parseDouble(value.trim());
    } catch (NumberFormatException e) {
      return Double.NaN;
    }
  }

  private RateSnapshot toEntity(CdmRateSnapshot cdm) {
    return new RateSnapshot(
        cdm.time(),
        cdm.instrumentType().name(),
        cdm.value(),
        cdm.source(),
        null,
        null);
  }
}
