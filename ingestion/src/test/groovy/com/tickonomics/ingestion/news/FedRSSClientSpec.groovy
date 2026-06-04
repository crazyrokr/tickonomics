package com.tickonomics.ingestion.news

import org.springframework.web.client.RestClient
import spock.lang.Specification
import spock.lang.Subject

class FedRSSClientSpec extends Specification {

  RestClient.Builder restClientBuilder = Mock()
  RestClient restClient = Mock()

  @Subject
  FedRSSClient client

  def setup() {
    restClientBuilder.build() >> restClient
    client = new FedRSSClient(restClientBuilder)
  }

  def "given valid RSS XML with items, when parseRssXml, then return articles"() {
    given:
      def xml = """<?xml version="1.0" encoding="UTF-8"?>
      <rss version="2.0">
        <channel>
          <item>
            <title>Chair Powell speaks on monetary policy</title>
            <link>https://www.federalreserve.gov/speech1.htm</link>
            <description>Full text of the Chair's remarks on monetary policy outlook.</description>
            <pubDate>Wed, 22 May 2024 14:00:00 GMT</pubDate>
          </item>
          <item>
            <title>FOMC statement released</title>
            <link>https://www.federalreserve.gov/fomc1.htm</link>
            <description>The Committee decided to maintain the target range.</description>
            <pubDate>Wed, 01 May 2024 18:00:00 GMT</pubDate>
          </item>
        </channel>
      </rss>""".stripIndent().trim().bytes

    when:
      def results = client.parseRssXml(xml, "FED_SPEECH")

    then:
      results.size() == 2
      results[0].title() == "Chair Powell speaks on monetary policy"
      results[0].sourceType() == "FED_SPEECH"
      results[0].source() == "FED_RSS"
      results[1].title() == "FOMC statement released"
  }

  def "given RSS with empty title, when parseRssXml, then skip item"() {
    given:
      def xml = """<?xml version="1.0" encoding="UTF-8"?>
      <rss version="2.0">
        <channel>
          <item>
            <title></title>
            <link>https://www.federalreserve.gov/empty.htm</link>
          </item>
          <item>
            <title>Valid item</title>
            <link>https://www.federalreserve.gov/valid.htm</link>
          </item>
        </channel>
      </rss>""".stripIndent().trim().bytes

    when:
      def results = client.parseRssXml(xml, "FOMC_STATEMENT")

    then:
      results.size() == 1
      results[0].title() == "Valid item"
      results[0].sourceType() == "FOMC_STATEMENT"
  }

  def "given malformed XML, when parseRssXml, then return empty without exception"() {
    given:
      def xml = "this is not xml at all".bytes

    when:
      def results = client.parseRssXml(xml, "FED_SPEECH")

    then:
      noExceptionThrown()
      results.isEmpty()
  }

  def "given RSS without pubDate, when parseRssXml, then use current time"() {
    given:
      def xml = """<?xml version="1.0" encoding="UTF-8"?>
      <rss version="2.0">
        <channel>
          <item>
            <title>No date item</title>
            <description>No date provided</description>
          </item>
        </channel>
      </rss>""".stripIndent().trim().bytes

    when:
      def results = client.parseRssXml(xml, "FED_SPEECH")

    then:
      results.size() == 1
      results[0].time() != null
  }

  def "given source name, when sourceName, then return FED_RSS"() {
    expect:
      client.sourceName() == "FED_RSS"
  }
}
