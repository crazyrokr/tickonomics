package com.tickonomics.ingestion.news;

import com.tickonomics.cdm.adapter.raw.NewsArticle;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Federal Reserve RSS feed client. Fetches FOMC statements, Fed speeches, and press releases from
 * the official RSS feeds. Provides full text for FinBERT hawkish/dovish classification.
 */
@Component
@ConditionalOnProperty(name = "monitor.fed-rss.enabled", havingValue = "true", matchIfMissing = false)
public class FedRSSClient implements NewsIngestionClient {

  private static final Logger log = LoggerFactory.getLogger(FedRSSClient.class);
  private static final DateTimeFormatter RSS_DATE_FMT = DateTimeFormatter.RFC_1123_DATE_TIME;

  private final RestClient restClient;

  @Value("${monitor.fed-rss.speeches-url:https://www.federalreserve.gov/feeds/speeches.xml}")
  private String speechesUrl;

  @Value("${monitor.fed-rss.fomc-url:https://www.federalreserve.gov/feeds/press_monetary.xml}")
  private String fomcUrl;

  public FedRSSClient(RestClient.Builder restClientBuilder) {
    this.restClient = restClientBuilder.build();
  }

  @Override
  @Retry(name = "fedRssApi")
  public List<NewsArticle> fetchNews() {
    List<NewsArticle> articles = new ArrayList<>();
    articles.addAll(fetchFeed(speechesUrl, "FED_SPEECH"));
    articles.addAll(fetchFeed(fomcUrl, "FOMC_STATEMENT"));
    return articles;
  }

  @Override
  public String sourceName() {
    return "FED_RSS";
  }

  private List<NewsArticle> fetchFeed(String feedUrl, String sourceType) {
    try {
      byte[] xmlBytes = restClient.get()
          .uri(feedUrl)
          .retrieve()
          .body(byte[].class);

      if (xmlBytes == null || xmlBytes.length == 0) {
        return List.of();
      }

      return parseRssXml(xmlBytes, sourceType);
    } catch (Exception e) {
      log.error("Failed to fetch Fed RSS feed from {}: {}", feedUrl, e.getMessage());
      return List.of();
    }
  }

  List<NewsArticle> parseRssXml(byte[] xmlBytes, String sourceType) {
    try {
      var factory = DocumentBuilderFactory.newInstance();
      factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      var builder = factory.newDocumentBuilder();
      Document doc = builder.parse(new ByteArrayInputStream(xmlBytes));

      NodeList items = doc.getElementsByTagName("item");
      List<NewsArticle> articles = new ArrayList<>();

      for (int i = 0; i < items.getLength(); i++) {
        Element item = (Element) items.item(i);
        String title = getTextContent(item, "title");
        if (title == null || title.isBlank()) {
          continue;
        }

        String link = getTextContent(item, "link");
        String description = getTextContent(item, "description");
        String pubDate = getTextContent(item, "pubDate");

        Instant time = Instant.now();
        if (pubDate != null) {
          try {
            time = Instant.from(RSS_DATE_FMT.parse(pubDate.trim()));
          } catch (Exception e) {
            log.debug("Could not parse RSS date '{}', using current time", pubDate);
          }
        }

        articles.add(new NewsArticle(
            time, title, description != null ? description : "",
            link != null ? link : "", "FED_RSS", sourceType, sourceType));
      }

      log.info("Parsed {} {} articles from Fed RSS", articles.size(), sourceType);
      return articles;
    } catch (Exception e) {
      log.error("Failed to parse Fed RSS XML: {}", e.getMessage());
      return List.of();
    }
  }

  private String getTextContent(Element parent, String tagName) {
    NodeList nodes = parent.getElementsByTagName(tagName);
    if (nodes.getLength() > 0) {
      return nodes.item(0).getTextContent();
    }
    return null;
  }
}
