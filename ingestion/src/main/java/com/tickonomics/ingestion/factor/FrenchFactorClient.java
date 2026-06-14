package com.tickonomics.ingestion.factor;

import com.tickonomics.cdm.adapter.FrenchFactorCdmAdapter;
import com.tickonomics.cdm.adapter.raw.FrenchFactorRow;
import com.tickonomics.cdm.enums.FactorSet;
import com.tickonomics.cdm.model.FactorReturn;
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
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipInputStream;

/**
 * Ken French Data Library client. Downloads ZIP-compressed CSV files from the Dartmouth/Tuck FTP
 * server, parses factor return rows, and converts them to CDM records. No authentication required.
 */
@Component
@ConditionalOnProperty(name = "monitor.ken-french.enabled", havingValue = "true", matchIfMissing = false)
public class FrenchFactorClient {

  private static final Logger log = LoggerFactory.getLogger(FrenchFactorClient.class);

  private final RestClient restClient;
  private final FrenchFactorCdmAdapter cdmAdapter;

  @Value("${monitor.ken-french.base-url:https://mba.tuck.dartmouth.edu/pages/faculty/ken.french/ftp}")
  private String baseUrl;

  public FrenchFactorClient(RestClient.Builder restClientBuilder, FrenchFactorCdmAdapter cdmAdapter) {
    this.restClient = restClientBuilder != null ? restClientBuilder.build() : null;
    this.cdmAdapter = cdmAdapter;
  }

  /**
   * Downloads and parses a Ken French factor dataset ZIP file.
   *
   * @param zipFile the ZIP filename (e.g., "F-F_Research_Data_Factors_CSV.zip")
   * @param factorSet the factor set identifier
   * @param frequency "MONTHLY" or "DAILY"
   * @return parsed and CDM-converted factor returns
   */
  public List<FactorReturn> fetchDataset(String zipFile, FactorSet factorSet, String frequency) {
    String url = baseUrl + "/" + zipFile;
    try {
      byte[] zipBytes = restClient.get()
          .uri(url)
          .retrieve()
          .body(byte[].class);

      if (zipBytes == null || zipBytes.length == 0) {
        log.warn("Empty response for Ken French dataset {}", zipFile);
        return List.of();
      }

      List<FrenchFactorRow> rows = parseZipCsv(zipBytes, factorSet, frequency);
      List<FactorReturn> returns = rows.stream()
          .map(cdmAdapter::toCdm)
          .toList();

      log.info("Parsed {} factor return rows from {} ({}, {})", returns.size(), zipFile, factorSet, frequency);
      return returns;
    } catch (Exception e) {
      log.error("Failed to fetch Ken French dataset {}: {}", zipFile, e.getMessage());
      return List.of();
    }
  }

  List<FrenchFactorRow> parseZipCsv(byte[] zipBytes, FactorSet factorSet, String frequency) {
    try (var zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
      zis.getNextEntry();
      var reader = new BufferedReader(new InputStreamReader(zis));
      List<FrenchFactorRow> rows = new ArrayList<>();
      boolean dataStarted = false;
      String line;

      while ((line = reader.readLine()) != null) {
        line = line.trim();
        if (line.isEmpty()) {
          continue;
        }

        if (!dataStarted) {
          if (line.startsWith("--") || line.matches("\\d{4,6},.*")) {
            dataStarted = true;
          } else {
            continue;
          }
        }

        if (line.startsWith("--") || line.toLowerCase().contains("copyright")
            || line.toLowerCase().contains("http")) {
          continue;
        }

        FrenchFactorRow row = parseRow(line, factorSet, frequency);
        if (row != null) {
          rows.add(row);
        }
      }

      return rows;
    } catch (Exception e) {
      log.error("Failed to parse ZIP CSV: {}", e.getMessage());
      return List.of();
    }
  }

  FrenchFactorRow parseRow(String line, FactorSet factorSet, String frequency) {
    String[] parts = line.split(",\\s*");
    if (parts.length < 2) {
      return null;
    }

    String dateStr = parts[0].trim();
    Instant time = parseDate(dateStr, frequency);
    if (time == null) {
      return null;
    }

    return switch (factorSet) {
      case FACTOR_3 -> parse3FactorRow(time, factorSet, frequency, parts);
      case FACTOR_5 -> parse5FactorRow(time, factorSet, frequency, parts);
      case MOMENTUM -> parseMomentumRow(time, factorSet, frequency, parts);
      default -> null;
    };
  }

  private FrenchFactorRow parse3FactorRow(Instant time, FactorSet factorSet, String frequency, String[] parts) {
    if (parts.length < 4) return null;
    return new FrenchFactorRow(time, factorSet, frequency,
        parseDouble(parts[1]), parseDouble(parts[2]), parseDouble(parts[3]),
        Double.NaN, Double.NaN, Double.NaN, Double.NaN);
  }

  private FrenchFactorRow parse5FactorRow(Instant time, FactorSet factorSet, String frequency, String[] parts) {
    if (parts.length < 6) return null;
    return new FrenchFactorRow(time, factorSet, frequency,
        parseDouble(parts[1]), parseDouble(parts[2]), parseDouble(parts[3]),
        parseDouble(parts[4]), parseDouble(parts[5]),
        parts.length > 6 ? parseDouble(parts[6]) : Double.NaN, Double.NaN);
  }

  private FrenchFactorRow parseMomentumRow(Instant time, FactorSet factorSet, String frequency, String[] parts) {
    if (parts.length < 2) return null;
    return new FrenchFactorRow(time, factorSet, frequency,
        Double.NaN, Double.NaN, Double.NaN,
        Double.NaN, Double.NaN, Double.NaN, parseDouble(parts[1]));
  }

  private Instant parseDate(String dateStr, String frequency) {
    try {
      if ("MONTHLY".equals(frequency)) {
        if (dateStr.length() == 6) {
          int year = Integer.parseInt(dateStr.substring(0, 4));
          int month = Integer.parseInt(dateStr.substring(4, 6));
          return YearMonth.of(year, month).atDay(1)
              .atStartOfDay().toInstant(java.time.ZoneOffset.UTC);
        }
      } else {
        if (dateStr.length() == 8) {
          return LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("yyyyMMdd"))
              .atStartOfDay().toInstant(java.time.ZoneOffset.UTC);
        }
      }
    } catch (Exception e) {
      log.debug("Could not parse Ken French date '{}': {}", dateStr, e.getMessage());
    }
    return null;
  }

  private double parseDouble(String value) {
    try {
      return Double.parseDouble(value.trim());
    } catch (NumberFormatException e) {
      return Double.NaN;
    }
  }
}
