package com.tickonomics.ingestion.options

import tools.jackson.databind.ObjectMapper
import com.tickonomics.cdm.adapter.YahooOptionsCdmAdapter
import org.springframework.web.client.RestClient
import spock.lang.Specification
import spock.lang.Subject

class YahooOptionsClientSpec extends Specification {

  RestClient.Builder restClientBuilder = Mock()
  RestClient restClient = Mock()
  YahooOptionsCdmAdapter cdmAdapter = new YahooOptionsCdmAdapter()

  @Subject
  YahooOptionsClient client

  def setup() {
    restClientBuilder.build() >> restClient
    client = new YahooOptionsClient(restClientBuilder, cdmAdapter)
    client.baseUrl = "https://query1.finance.yahoo.com"
  }

  def "given valid options response, when fetchOptionsChain, then return contracts"() {
    given:
      def json = '''
      {
        "optionChain": {
          "options": [{
            "calls": [
              {"strike": 500.0, "expiration": 1721001600, "bid": 5.5, "ask": 6.0,
               "lastPrice": 5.75, "impliedVolatility": 0.18, "delta": 0.55,
               "gamma": 0.02, "theta": -0.05, "vega": 0.3, "rho": 0.1,
               "openInterest": 15000, "underlyingPrice": 503.0}
            ],
            "puts": [
              {"strike": 500.0, "expiration": 1721001600, "bid": 2.5, "ask": 3.0,
               "lastPrice": 2.75, "impliedVolatility": 0.19, "delta": -0.45,
               "gamma": 0.02, "theta": -0.04, "vega": 0.28, "rho": -0.08,
               "openInterest": 12000, "underlyingPrice": 503.0}
            ]
          }]
        }
      }
      '''
      def requestHeadersUriSpec = Mock(RestClient.RequestHeadersUriSpec)
      def responseSpec = Mock(RestClient.ResponseSpec)

    when:
      def results = client.fetchOptionsChain("SPY")

    then:
      1 * restClient.get() >> requestHeadersUriSpec
      1 * requestHeadersUriSpec.uri(_ as String, _ as Object[]) >> requestHeadersUriSpec
      1 * requestHeadersUriSpec.retrieve() >> responseSpec
      1 * responseSpec.body(tools.jackson.databind.JsonNode.class) >> new ObjectMapper().readTree(json)

      results.size() == 2
      results[0].underlying() == "SPY"
      results[0].optionType() == "CALL"
      results[0].bid() == 5.5
      results[1].optionType() == "PUT"
  }

  def "given null response, when fetchOptionsChain, then return empty"() {
    given:
      def requestHeadersUriSpec = Mock(RestClient.RequestHeadersUriSpec)
      def responseSpec = Mock(RestClient.ResponseSpec)

    when:
      def results = client.fetchOptionsChain("SPY")

    then:
      1 * restClient.get() >> requestHeadersUriSpec
      1 * requestHeadersUriSpec.uri(_ as String, _ as Object[]) >> requestHeadersUriSpec
      1 * requestHeadersUriSpec.retrieve() >> responseSpec
      1 * responseSpec.body(tools.jackson.databind.JsonNode.class) >> null

      results.isEmpty()
  }

  def "given options with zero bid and ask, when fetchOptionsChain, then skip"() {
    given:
      def json = '''
      {
        "optionChain": {
          "options": [{
            "calls": [
              {"strike": 500.0, "expiration": 1721001600, "bid": 0, "ask": 0,
               "lastPrice": 0, "impliedVolatility": 0, "delta": 0,
               "gamma": 0, "theta": 0, "vega": 0, "rho": 0,
               "openInterest": 0, "underlyingPrice": 0}
            ],
            "puts": []
          }]
        }
      }
      '''
      def requestHeadersUriSpec = Mock(RestClient.RequestHeadersUriSpec)
      def responseSpec = Mock(RestClient.ResponseSpec)

    when:
      def results = client.fetchOptionsChain("SPY")

    then:
      1 * restClient.get() >> requestHeadersUriSpec
      1 * requestHeadersUriSpec.uri(_ as String, _ as Object[]) >> requestHeadersUriSpec
      1 * requestHeadersUriSpec.retrieve() >> responseSpec
      1 * responseSpec.body(tools.jackson.databind.JsonNode.class) >> new ObjectMapper().readTree(json)

      results.isEmpty()
  }

  def "given empty options array, when fetchOptionsChain, then return empty"() {
    given:
      def json = '{"optionChain":{"options":[]}}'
      def requestHeadersUriSpec = Mock(RestClient.RequestHeadersUriSpec)
      def responseSpec = Mock(RestClient.ResponseSpec)

    when:
      def results = client.fetchOptionsChain("SPY")

    then:
      1 * restClient.get() >> requestHeadersUriSpec
      1 * requestHeadersUriSpec.uri(_ as String, _ as Object[]) >> requestHeadersUriSpec
      1 * requestHeadersUriSpec.retrieve() >> responseSpec
      1 * responseSpec.body(tools.jackson.databind.JsonNode.class) >> new ObjectMapper().readTree(json)

      results.isEmpty()
  }
}
