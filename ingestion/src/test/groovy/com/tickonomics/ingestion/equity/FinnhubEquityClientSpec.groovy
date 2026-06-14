package com.tickonomics.ingestion.equity

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.web.client.RestClient
import spock.lang.Specification
import spock.lang.Subject

class FinnhubEquityClientSpec extends Specification {

  RestClient.Builder restClientBuilder = Mock()
  RestClient restClient = Mock()

  @Subject
  FinnhubEquityClient client

  def setup() {
    restClientBuilder.build() >> restClient
    client = new FinnhubEquityClient(restClientBuilder)
    client.restUrl = "https://finnhub.io/api/v1"
    client.apiKey = "test-key"
  }

  def "given valid quote response, when fetchQuote, then return quote"() {
    given:
      def json = '{"c":503.5,"h":508.0,"l":500.0,"o":501.0,"pc":499.0,"v":150000}'
      def requestHeadersUriSpec = Mock(RestClient.RequestHeadersUriSpec)
      def requestHeadersSpec = Mock(RestClient.RequestHeadersSpec)
      def responseSpec = Mock(RestClient.ResponseSpec)

    when:
      def result = client.fetchQuote("SPY")

    then:
      1 * restClient.get() >> requestHeadersUriSpec
      1 * requestHeadersUriSpec.uri(_ as String, _ as Object[]) >> requestHeadersSpec
      1 * requestHeadersSpec.retrieve() >> responseSpec
      1 * responseSpec.body(com.fasterxml.jackson.databind.JsonNode.class) >> new ObjectMapper().readTree(json)

      result != null
      result.currentPrice() == 503.5
      result.symbol() == "SPY"
      result.high() == 508.0
      result.low() == 500.0
      result.open() == 501.0
      result.previousClose() == 499.0
      result.volume() == 150000L
  }

  def "given zero price in response, when fetchQuote, then return null"() {
    given:
      def json = '{"c":0.0,"h":0.0,"l":0.0,"o":0.0,"pc":0.0,"v":0}'
      def requestHeadersUriSpec = Mock(RestClient.RequestHeadersUriSpec)
      def requestHeadersSpec = Mock(RestClient.RequestHeadersSpec)
      def responseSpec = Mock(RestClient.ResponseSpec)

    when:
      def result = client.fetchQuote("SPY")

    then:
      1 * restClient.get() >> requestHeadersUriSpec
      1 * requestHeadersUriSpec.uri(_ as String, _ as Object[]) >> requestHeadersSpec
      1 * requestHeadersSpec.retrieve() >> responseSpec
      1 * responseSpec.body(com.fasterxml.jackson.databind.JsonNode.class) >> new ObjectMapper().readTree(json)

      result == null
  }

  def "given null response, when fetchQuote, then return null"() {
    given:
      def requestHeadersUriSpec = Mock(RestClient.RequestHeadersUriSpec)
      def requestHeadersSpec = Mock(RestClient.RequestHeadersSpec)
      def responseSpec = Mock(RestClient.ResponseSpec)

    when:
      def result = client.fetchQuote("SPY")

    then:
      1 * restClient.get() >> requestHeadersUriSpec
      1 * requestHeadersUriSpec.uri(_ as String, _ as Object[]) >> requestHeadersSpec
      1 * requestHeadersSpec.retrieve() >> responseSpec
      1 * responseSpec.body(com.fasterxml.jackson.databind.JsonNode.class) >> null

      result == null
  }

  def "given valid candle response, when fetchHistoricalOhlcv, then return bars"() {
    given:
      def json = '{"s":"ok","t":[1716422400,1716508800],"o":[500.0,502.0],"h":[505.0,508.0],"l":[498.0,500.0],"c":[503.0,506.0],"v":[100000,120000]}'
      def requestHeadersUriSpec = Mock(RestClient.RequestHeadersUriSpec)
      def requestHeadersSpec = Mock(RestClient.RequestHeadersSpec)
      def responseSpec = Mock(RestClient.ResponseSpec)

    when:
      def results = client.fetchHistoricalOhlcv("SPY", "1d")

    then:
      1 * restClient.get() >> requestHeadersUriSpec
      1 * requestHeadersUriSpec.uri(_ as String, _ as Object[]) >> requestHeadersSpec
      1 * requestHeadersSpec.retrieve() >> responseSpec
      1 * responseSpec.body(com.fasterxml.jackson.databind.JsonNode.class) >> new ObjectMapper().readTree(json)

      results.size() == 2
      results[0].symbol() == "SPY"
      results[0].close() == 503.0
      results[1].close() == 506.0
  }

  def "given no data status, when fetchHistoricalOhlcv, then return empty"() {
    given:
      def json = '{"s":"no_data"}'
      def requestHeadersUriSpec = Mock(RestClient.RequestHeadersUriSpec)
      def requestHeadersSpec = Mock(RestClient.RequestHeadersSpec)
      def responseSpec = Mock(RestClient.ResponseSpec)

    when:
      def results = client.fetchHistoricalOhlcv("INVALID", "1d")

    then:
      1 * restClient.get() >> requestHeadersUriSpec
      1 * requestHeadersUriSpec.uri(_ as String, _ as Object[]) >> requestHeadersSpec
      1 * requestHeadersSpec.retrieve() >> responseSpec
      1 * responseSpec.body(com.fasterxml.jackson.databind.JsonNode.class) >> new ObjectMapper().readTree(json)

      results.isEmpty()
  }

  def "given source name, when sourceName, then return FINNHUB"() {
    expect:
      client.sourceName() == "FINNHUB"
  }
}
