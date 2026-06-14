package com.tickonomics.cdm.adapter;

import com.tickonomics.cdm.adapter.raw.NewsArticle;

/**
 * Identity adapter for news articles. News articles are already in CDM-friendly format — this
 * adapter exists for consistency with the CdmAdapter pattern and future field mapping needs.
 */
public class NewsArticleCdmAdapter implements CdmAdapter<NewsArticle, NewsArticle> {

  @Override
  public NewsArticle toCdm(NewsArticle raw) {
    return raw;
  }
}
