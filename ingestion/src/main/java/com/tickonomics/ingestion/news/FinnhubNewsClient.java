package com.tickonomics.ingestion.news;

import com.fasterxml.jackson.databind.JsonNode;
import com.tickonomics.cdm.adapter.raw.NewsArticle;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Finnhub market news client. Fetches general financial news headlines and summaries via the free
 * Finnhub REST API. Polls at configurable intervals (default 6 hours).
 */
@Component
@ConditionalOnProperty(name = "monitor.finnhub.news-enabled", havingValue = "true", matchIfMissing = false)
public class FinnhubNewsClient implements NewsIngestionClient {

  private static final Logger log = LoggerFactory.getLogger(FinnhubNewsClient.class);

  private final RestClient restClient;

  @Value("${monitor.finnhub.rest-url:https://finnhub.io/api/v1}")
  private String restUrl;

  @Value("${monitor.finnub.api-key:}")
  private String apiKey;

  public FinnhubNewsClient(RestClient.Builder restClientBuilder) {
    this.restClient = restClientBuilder.build();
  }

  @Override
  @Retry(name = "finnhubApi")
  public List<NewsArticle> fetchNews() {
    String url = restUrl + "/news?category=general&token={token}";
    var response = restClient.get()
        .uri(url, apiKey)
        .retrieve()
        .body(JsonNode.class);

    if (response == null || !response.isArray()) {
      return List.of();
    }

    List<NewsArticle> articles = new ArrayList<>();
    for (JsonNode item : response) {
      String headline = item.path("headline").asText("");
      if (headline.isBlank()) {
        continue;
      }

      long timestamp = item.path("datetime").asLong(0);
      Instant time = timestamp > 0 ? Instant.ofEpochSecond(timestamp) : Instant.now();

      articles.add(new NewsArticle(
          time,
          headline,
          item.path("summary").asText(""),
          item.path("url").asText(""),
          item.path("source").asText("FINNHUB"),
          "MARKET_NEWS",
          item.path("category").asText("general")));
    }

    log.info("Fetched {} news articles from Finnhub", articles.size());
    return articles;
  }

  @Override
  public String sourceName() {
    return "FINNHUB_NEWS";
  }
}
