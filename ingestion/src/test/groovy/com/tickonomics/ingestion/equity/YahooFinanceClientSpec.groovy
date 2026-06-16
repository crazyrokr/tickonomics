package com.tickonomics.ingestion.equity

import tools.jackson.databind.ObjectMapper
import com.tickonomics.ingestion.writer.TimescaleDbWriter
import org.springframework.web.client.RestClient
import spock.lang.Specification
import spock.lang.Subject

class YahooFinanceClientSpec extends Specification {

  RestClient.Builder restClientBuilder = Mock()
  RestClient restClient = Mock()

  @Subject
  YahooFinanceClient client

  def setup() {
    restClientBuilder.build() >> restClient
    client = new YahooFinanceClient(restClientBuilder)
  }

  def "given valid chart response, when fetchHistoricalOhlcv, then return OHLCV bars"() {
    given:
      def json = '''
      {
        "chart": {
          "result": [{
            "meta": {"symbol": "SPY"},
            "timestamp": [1716422400, 1716508800],
            "indicators": {
              "quote": [{
                "open": [500.0, 502.0],
                "high": [505.0, 508.0],
                "low": [498.0, 500.0],
                "close": [503.0, 506.0],
                "volume": [100000, 120000]
              }]
            }
          }]
        }
      }
      '''
      def requestHeadersUriSpec = Mock(RestClient.RequestHeadersUriSpec)
      def requestHeadersSpec = Mock(RestClient.RequestHeadersSpec)
      def responseSpec = Mock(RestClient.ResponseSpec)

    when:
      def results = client.fetchHistoricalOhlcv("SPY", "1d")

    then:
      1 * restClient.get() >> requestHeadersUriSpec
      1 * requestHeadersUriSpec.uri(_ as String, _ as Object[]) >> requestHeadersSpec
      1 * requestHeadersSpec.retrieve() >> responseSpec
      1 * responseSpec.body(tools.jackson.databind.JsonNode.class) >> new ObjectMapper().readTree(json)

      results.size() == 2
      results[0].symbol() == "SPY"
      results[0].close() == 503.0
      results[0].volume() == 100000L
      results[1].close() == 506.0
  }

  def "given null response, when fetchHistoricalOhlcv, then return empty"() {
    given:
      def requestHeadersUriSpec = Mock(RestClient.RequestHeadersUriSpec)
      def requestHeadersSpec = Mock(RestClient.RequestHeadersSpec)
      def responseSpec = Mock(RestClient.ResponseSpec)

    when:
      def results = client.fetchHistoricalOhlcv("SPY", "1d")

    then:
      1 * restClient.get() >> requestHeadersUriSpec
      1 * requestHeadersUriSpec.uri(_ as String, _ as Object[]) >> requestHeadersSpec
      1 * requestHeadersSpec.retrieve() >> responseSpec
      1 * responseSpec.body(tools.jackson.databind.JsonNode.class) >> null

      results.isEmpty()
  }

  def "given response with zero close, when fetchHistoricalOhlcv, then skip invalid bars"() {
    given:
      def json = '''
      {
        "chart": {
          "result": [{
            "meta": {"symbol": "SPY"},
            "timestamp": [1716422400, 1716508800],
            "indicators": {
              "quote": [{
                "open": [500.0, 502.0],
                "high": [505.0, 508.0],
                "low": [498.0, 500.0],
                "close": [503.0, 0.0],
                "volume": [100000, 120000]
              }]
            }
          }]
        }
      }
      '''
      def requestHeadersUriSpec = Mock(RestClient.RequestHeadersUriSpec)
      def requestHeadersSpec = Mock(RestClient.RequestHeadersSpec)
      def responseSpec = Mock(RestClient.ResponseSpec)

    when:
      def results = client.fetchHistoricalOhlcv("SPY", "1d")

    then:
      1 * restClient.get() >> requestHeadersUriSpec
      1 * requestHeadersUriSpec.uri(_ as String, _ as Object[]) >> requestHeadersSpec
      1 * requestHeadersSpec.retrieve() >> responseSpec
      1 * responseSpec.body(tools.jackson.databind.JsonNode.class) >> new ObjectMapper().readTree(json)

      results.size() == 1
      results[0].close() == 503.0
  }

  def "given empty result array, when fetchHistoricalOhlcv, then return empty"() {
    given:
      def json = '{"chart":{"result":[]}}'
      def requestHeadersUriSpec = Mock(RestClient.RequestHeadersUriSpec)
      def requestHeadersSpec = Mock(RestClient.RequestHeadersSpec)
      def responseSpec = Mock(RestClient.ResponseSpec)

    when:
      def results = client.fetchHistoricalOhlcv("SPY", "1d")

    then:
      1 * restClient.get() >> requestHeadersUriSpec
      1 * requestHeadersUriSpec.uri(_ as String, _ as Object[]) >> requestHeadersSpec
      1 * requestHeadersSpec.retrieve() >> responseSpec
      1 * responseSpec.body(tools.jackson.databind.JsonNode.class) >> new ObjectMapper().readTree(json)

      results.isEmpty()
  }

  def "given source name, when sourceName, then return YAHOO_FINANCE"() {
    expect:
      client.sourceName() == "YAHOO_FINANCE"
  }
}
