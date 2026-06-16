package com.tickonomics.ingestion.alphavantage

import tools.jackson.databind.ObjectMapper
import com.tickonomics.cdm.adapter.AlphaVantageCdmAdapter
import com.tickonomics.ingestion.writer.TimescaleDbWriter
import org.springframework.web.client.RestClient
import spock.lang.Specification
import spock.lang.Subject

class AlphaVantageClientSpec extends Specification {

  RestClient.Builder restClientBuilder = Mock()
  RestClient restClient = Mock()
  AlphaVantageCdmAdapter cdmAdapter = new AlphaVantageCdmAdapter()
  TimescaleDbWriter writer = Mock()

  @Subject
  AlphaVantageClient client

  def setup() {
    restClientBuilder.build() >> restClient
    client = new AlphaVantageClient(restClientBuilder, cdmAdapter, writer)
    client.baseUrl = "https://www.alphavantage.co/query"
    client.apiKey = "test-key"
    client.rateLimitDelayMs = 0
    client.symbols = ["SPY"]
    client.commodities = ["GOLD"]
  }

  def "given valid TIME_SERIES_DAILY_ADJUSTED response, when fetchAdjustedDaily, then return bars"() {
    given:
      def json = '''
      {
        "Time Series (Daily)": {
          "2024-05-22": {
            "1. open": "500.0",
            "2. high": "505.0",
            "3. low": "498.0",
            "4. close": "503.0",
            "5. adjusted close": "503.5",
            "6. volume": "10000000",
            "7. dividend amount": "0.0",
            "8. split coefficient": "1.0"
          },
          "2024-05-21": {
            "1. open": "498.0",
            "2. high": "502.0",
            "3. low": "496.0",
            "4. close": "500.0",
            "5. adjusted close": "500.5",
            "6. volume": "9500000",
            "7. dividend amount": "0.0",
            "8. split coefficient": "1.0"
          }
        }
      }
      '''
      def requestHeadersUriSpec = Mock(RestClient.RequestHeadersUriSpec)
      def requestHeadersSpec = Mock(RestClient.RequestHeadersSpec)
      def responseSpec = Mock(RestClient.ResponseSpec)

    when:
      def results = client.fetchAdjustedDaily("SPY", false)

    then:
      1 * restClient.get() >> requestHeadersUriSpec
      1 * requestHeadersUriSpec.uri(_ as String, _ as Object[]) >> requestHeadersSpec
      1 * requestHeadersSpec.retrieve() >> responseSpec
      1 * responseSpec.body(tools.jackson.databind.JsonNode.class) >> new ObjectMapper().readTree(json)

      results.size() == 2
      results[0].symbol() == "SPY"
      results[0].adjustedClose() == 503.5
      results[0].splitCoefficient() == 1.0
      results[1].adjustedClose() == 500.5
  }

  def "given null response, when fetchAdjustedDaily, then return empty"() {
    given:
      def requestHeadersUriSpec = Mock(RestClient.RequestHeadersUriSpec)
      def requestHeadersSpec = Mock(RestClient.RequestHeadersSpec)
      def responseSpec = Mock(RestClient.ResponseSpec)

    when:
      def results = client.fetchAdjustedDaily("SPY", false)

    then:
      1 * restClient.get() >> requestHeadersUriSpec
      1 * requestHeadersUriSpec.uri(_ as String, _ as Object[]) >> requestHeadersSpec
      1 * requestHeadersSpec.retrieve() >> responseSpec
      1 * responseSpec.body(tools.jackson.databind.JsonNode.class) >> null

      results.isEmpty()
  }

  def "given rate limit information message, when fetchAdjustedDaily, then log and return empty"() {
    given:
      def json = '{"Information":"Thank you for using Alpha Vantage! Our standard API call frequency is 5 calls per minute."}'
      def requestHeadersUriSpec = Mock(RestClient.RequestHeadersUriSpec)
      def requestHeadersSpec = Mock(RestClient.RequestHeadersSpec)
      def responseSpec = Mock(RestClient.ResponseSpec)

    when:
      def results = client.fetchAdjustedDaily("SPY", false)

    then:
      1 * restClient.get() >> requestHeadersUriSpec
      1 * requestHeadersUriSpec.uri(_ as String, _ as Object[]) >> requestHeadersSpec
      1 * requestHeadersSpec.retrieve() >> responseSpec
      1 * responseSpec.body(tools.jackson.databind.JsonNode.class) >> new ObjectMapper().readTree(json)

      results.isEmpty()
  }

  def "given bar with zero adjusted close, when fetchAdjustedDaily, then skip"() {
    given:
      def json = '''
      {
        "Time Series (Daily)": {
          "2024-05-22": {
            "1. open": "0", "2. high": "0", "3. low": "0", "4. close": "0",
            "5. adjusted close": "0", "6. volume": "0",
            "7. dividend amount": "0", "8. split coefficient": "1"
          }
        }
      }
      '''
      def requestHeadersUriSpec = Mock(RestClient.RequestHeadersUriSpec)
      def requestHeadersSpec = Mock(RestClient.RequestHeadersSpec)
      def responseSpec = Mock(RestClient.ResponseSpec)

    when:
      def results = client.fetchAdjustedDaily("SPY", false)

    then:
      1 * restClient.get() >> requestHeadersUriSpec
      1 * requestHeadersUriSpec.uri(_ as String, _ as Object[]) >> requestHeadersSpec
      1 * requestHeadersSpec.retrieve() >> responseSpec
      1 * responseSpec.body(tools.jackson.databind.JsonNode.class) >> new ObjectMapper().readTree(json)

      results.isEmpty()
  }

  def "given valid commodity response, when fetchCommodity, then return bars"() {
    given:
      def json = '''
      {
        "name": "Gold",
        "data": [
          {"date": "2024-05-22", "value": "2345.60"},
          {"date": "2024-05-21", "value": "2338.20"}
        ]
      }
      '''
      def requestHeadersUriSpec = Mock(RestClient.RequestHeadersUriSpec)
      def requestHeadersSpec = Mock(RestClient.RequestHeadersSpec)
      def responseSpec = Mock(RestClient.ResponseSpec)

    when:
      def results = client.fetchCommodity("GOLD")

    then:
      1 * restClient.get() >> requestHeadersUriSpec
      1 * requestHeadersUriSpec.uri(_ as String, _ as Object[]) >> requestHeadersSpec
      1 * requestHeadersSpec.retrieve() >> responseSpec
      1 * responseSpec.body(tools.jackson.databind.JsonNode.class) >> new ObjectMapper().readTree(json)

      results.size() == 2
      results[0].symbol() == "GOLD"
      results[0].adjustedClose() == 2345.60
  }

  def "given commodity with zero value, when fetchCommodity, then skip"() {
    given:
      def json = '''
      {
        "data": [
          {"date": "2024-05-22", "value": "0"},
          {"date": "2024-05-21", "value": "2338.20"}
        ]
      }
      '''
      def requestHeadersUriSpec = Mock(RestClient.RequestHeadersUriSpec)
      def requestHeadersSpec = Mock(RestClient.RequestHeadersSpec)
      def responseSpec = Mock(RestClient.ResponseSpec)

    when:
      def results = client.fetchCommodity("GOLD")

    then:
      1 * restClient.get() >> requestHeadersUriSpec
      1 * requestHeadersUriSpec.uri(_ as String, _ as Object[]) >> requestHeadersSpec
      1 * requestHeadersSpec.retrieve() >> responseSpec
      1 * responseSpec.body(tools.jackson.databind.JsonNode.class) >> new ObjectMapper().readTree(json)

      results.size() == 1
      results[0].adjustedClose() == 2338.20
  }
}
