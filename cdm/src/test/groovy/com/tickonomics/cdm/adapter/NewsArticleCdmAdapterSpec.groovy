package com.tickonomics.cdm.adapter

import com.tickonomics.cdm.adapter.raw.NewsArticle
import spock.lang.Specification
import spock.lang.Subject

import java.time.Instant

class NewsArticleCdmAdapterSpec extends Specification {

  @Subject
  NewsArticleCdmAdapter adapter = new NewsArticleCdmAdapter()

  def "given news article, when toCdm, then return same article"() {
    given:
      def article = new NewsArticle(Instant.now(), "Test headline", "Summary text",
          "https://example.com", "FINNHUB", "MARKET_NEWS", "general")

    when:
      def result = adapter.toCdm(article)

    then:
      result.is(article)
      result.title() == "Test headline"
      result.source() == "FINNHUB"
      result.sourceType() == "MARKET_NEWS"
  }
}
