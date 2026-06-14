package com.tickonomics.ingestion.news;

import com.tickonomics.cdm.adapter.NewsArticleCdmAdapter;
import com.tickonomics.cdm.adapter.raw.NewsArticle;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Scheduled polling coordinator for news data. Collects articles from all configured news clients
 * (Finnhub market news, Fed RSS feeds) and forwards them for sentiment analysis processing.
 */
@Component
@ConditionalOnProperty(name = "monitor.news.enabled", havingValue = "true", matchIfMissing = false)
public class NewsScheduler {

  private static final Logger log = LoggerFactory.getLogger(NewsScheduler.class);

  private final List<NewsIngestionClient> clients;
  private final NewsArticleCdmAdapter adapter;

  public NewsScheduler(List<NewsIngestionClient> clients, NewsArticleCdmAdapter adapter) {
    this.clients = clients;
    this.adapter = adapter;
  }

  @Scheduled(fixedDelayString = "${monitor.news.poll-interval-ms:21600000}")
  @Bulkhead(name = "highVolumeIngestion")
  public void pollNews() {
    for (NewsIngestionClient client : clients) {
      try {
        List<NewsArticle> articles = client.fetchNews();
        for (NewsArticle raw : articles) {
          adapter.toCdm(raw);
        }
        log.info("Collected {} articles from {}", articles.size(), client.sourceName());
      } catch (Exception e) {
        log.warn("News client {} failed: {}", client.sourceName(), e.getMessage());
      }
    }
  }
}
