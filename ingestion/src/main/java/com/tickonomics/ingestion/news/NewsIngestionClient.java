package com.tickonomics.ingestion.news;

import com.tickonomics.cdm.adapter.raw.NewsArticle;

import java.util.List;

/**
 * Abstraction over news data sources. Implementations fetch articles from specific providers
 * (Finnhub market news, Fed RSS feeds) and return source-agnostic news records.
 */
public interface NewsIngestionClient {

  List<NewsArticle> fetchNews();

  String sourceName();
}
