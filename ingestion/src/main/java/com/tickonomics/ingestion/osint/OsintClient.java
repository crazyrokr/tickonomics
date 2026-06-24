package com.tickonomics.ingestion.osint;

import com.tickonomics.cdm.adapter.OsintCdmAdapter;
import com.tickonomics.cdm.adapter.raw.OsintEvent;
import com.tickonomics.cdm.model.CdmNewsEvent;
import com.tickonomics.ingestion.tracing.IngestionTracer;
import com.tickonomics.ingestion.tracing.IngestionTracingConfig;
import com.tickonomics.ingestion.writer.TimescaleDbWriter;
import com.tickonomics.persistence.entity.NewsEvent;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Polls the GDELT DOC 2.0 API for OSINT news articles (with tone) and persists them through the CDM
 * adapter + {@link TimescaleDbWriter}. Disabled by default (ADR-037); enable via
 * {@code monitor.osint.enabled=true}.
 */
@Component
@ConditionalOnProperty(name = "monitor.osint.enabled", havingValue = "true", matchIfMissing = false)
public class OsintClient {

  private static final Logger log = LoggerFactory.getLogger(OsintClient.class);
  private static final DateTimeFormatter SEENDATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");

  private final RestClient restClient;
  private final TimescaleDbWriter writer;
  private final OsintCdmAdapter cdmAdapter;
  private final IngestionTracer tracer;

  @Value("${monitor.osint.base-url:https://api.gdeltproject.org/api/v2/doc/doc}")
  private String baseUrl;

  @Value("${monitor.osint.query:stock market earnings}")
  private String query;

  @Value("${monitor.osint.max-records:75}")
  private int maxRecords;

  public OsintClient(
      RestClient.Builder restClientBuilder,
      TimescaleDbWriter writer,
      OsintCdmAdapter cdmAdapter,
      IngestionTracer tracer) {
    this.restClient = restClientBuilder.build();
    this.writer = writer;
    this.cdmAdapter = cdmAdapter;
    this.tracer = tracer;
  }

  @Scheduled(fixedDelayString = "${monitor.osint.poll-interval-ms:600000}")
  @Bulkhead(name = "criticalIngestion")
  public void pollEvents() {
    try {
      GdeltDocResponse response = fetchArticles();
      if (response == null || response.articles() == null) {
        return;
      }
      int written = 0;
      for (GdeltArticleRaw article : response.articles()) {
        try {
          OsintEvent raw = toEvent(article);
          if (raw == null) {
            continue;
          }
          CdmNewsEvent cdm = cdmAdapter.toCdm(raw);
          writer.writeNewsEvent(toEntity(cdm));
          written++;
        } catch (Exception e) {
          log.debug("Skipping GDELT article: {}", e.getMessage());
        }
      }
      log.info("Fetched {} GDELT articles, wrote {} events", response.articles().size(), written);
    } catch (Exception e) {
      log.error("Failed to poll GDELT articles: {}", e.getMessage());
    }
  }

  @Retryable(includes = RestClientException.class, maxRetries = 2, delay = 1000, multiplier = 2)
  public GdeltDocResponse fetchArticles() {
    String url = baseUrl + "?query={query}&mode=artlist&format=json&maxrecords={max}&sort=datedesc";
    try (var scope = tracer.span(IngestionTracingConfig.SPAN_OSINT_FETCH)) {
      return restClient
          .get()
          .uri(url, query, maxRecords)
          .retrieve()
          .body(GdeltDocResponse.class);
    }
  }

  /**
   * Maps a raw GDELT article to an {@link OsintEvent}. Returns {@code null} when the article lacks a
   * url (used as the stable event id) or a parseable seen-date, which keeps unplaceable payloads out.
   */
  static OsintEvent toEvent(GdeltArticleRaw article) {
    if (article == null || isBlank(article.url())) {
      return null;
    }
    Instant time = parseSeenDate(article.seendate());
    if (time == null) {
      return null;
    }
    return new OsintEvent(
        time, article.url(), "GDELT",
        isBlank(article.title()) ? article.url() : article.title(),
        parseTone(article.tone()), null, article.sourcecountry(), article.url());
  }

  private NewsEvent toEntity(CdmNewsEvent cdm) {
    return new NewsEvent(
        cdm.time(), cdm.eventId(), cdm.source(), cdm.headline(),
        cdm.avgTone(), cdm.themes(), cdm.actors(), cdm.url());
  }

  private static Instant parseSeenDate(String seenDate) {
    if (isBlank(seenDate)) {
      return null;
    }
    try {
      return LocalDateTime.parse(seenDate, SEENDATE_FMT).toInstant(ZoneOffset.UTC);
    } catch (DateTimeParseException e) {
      return null;
    }
  }

  private static double parseTone(String tone) {
    if (isBlank(tone)) {
      return 0.0;
    }
    String first = tone.contains(",") ? tone.substring(0, tone.indexOf(',')) : tone;
    try {
      return Double.parseDouble(first.trim());
    } catch (NumberFormatException e) {
      return 0.0;
    }
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }

  record GdeltDocResponse(List<GdeltArticleRaw> articles) {}

  record GdeltArticleRaw(
      String url, String title, String seendate, String domain, String tone, String sourcecountry) {}
}
