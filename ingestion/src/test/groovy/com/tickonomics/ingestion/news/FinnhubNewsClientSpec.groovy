package com.tickonomics.ingestion.news

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Value
import org.springframework.web.client.RestClient
import spock.lang.Specification
import spock.lang.Subject

class FinnhubNewsClientSpec extends Specification {

  RestClient.Builder restClientBuilder = Mock()
  RestClient restClient = Mock()

  @Subject
  FinnhubNewsClient client

  def setup() {
    restClientBuilder.build() >> restClient
    client = new FinnhubNewsClient(restClientBuilder, "test-key")
    client.restUrl = "https://finnhub.io/api/v1"
  }

  def "given apiKey parameter, when constructed, then it binds to the finnhub property"() {
    given: "the api-key constructor parameter"
      def ctor = FinnhubNewsClient.constructors.find { it.parameterCount == 2 }
      def paramAnnotations = ctor.parameterAnnotations[1]
      def valueAnno = paramAnnotations.find { it instanceof Value } as Value

    expect: "the property name matches application.yml (guards the finnub typo regression)"
      valueAnno != null
      valueAnno.value() == "\${monitor.finnhub.api-key:}"
  }

  def "given configured api key, when fetchNews, then key is sent as the token URI variable"() {
    given:
      def json = "[]"
      def requestHeadersUriSpec = Mock(RestClient.RequestHeadersUriSpec)
      def requestHeadersSpec = Mock(RestClient.RequestHeadersSpec)
      def responseSpec = Mock(RestClient.ResponseSpec)

    when:
      client.fetchNews()

    then:
      1 * restClient.get() >> requestHeadersUriSpec
      1 * requestHeadersUriSpec.uri(_ as String, "test-key") >> requestHeadersSpec
      1 * requestHeadersSpec.retrieve() >> responseSpec
      1 * responseSpec.body(com.fasterxml.jackson.databind.JsonNode.class) >> new ObjectMapper().readTree(json)
  }

  def "given valid news response, when fetchNews, then return articles"() {
    given:
      def json = '''[
        {"headline":"Fed signals rate pause","summary":"The Federal Reserve indicated rates may stay steady","url":"https://example.com/1","source":"Reuters","datetime":1716422400,"category":"general"},
        {"headline":"Markets rally on earnings","summary":"Strong earnings reports drive market gains","url":"https://example.com/2","source":"Bloomberg","datetime":1716422500,"category":"general"}
      ]'''
      def requestHeadersUriSpec = Mock(RestClient.RequestHeadersUriSpec)
      def requestHeadersSpec = Mock(RestClient.RequestHeadersSpec)
      def responseSpec = Mock(RestClient.ResponseSpec)

    when:
      def results = client.fetchNews()

    then:
      1 * restClient.get() >> requestHeadersUriSpec
      1 * requestHeadersUriSpec.uri(_ as String, _ as Object[]) >> requestHeadersSpec
      1 * requestHeadersSpec.retrieve() >> responseSpec
      1 * responseSpec.body(com.fasterxml.jackson.databind.JsonNode.class) >> new ObjectMapper().readTree(json)

      results.size() == 2
      results[0].title() == "Fed signals rate pause"
      results[0].sourceType() == "MARKET_NEWS"
      results[1].title() == "Markets rally on earnings"
  }

  def "given blank headline, when fetchNews, then skip article"() {
    given:
      def json = '''[
        {"headline":"","summary":"No title","url":"","source":"test","datetime":0,"category":"general"},
        {"headline":"Valid headline","summary":"Has title","url":"","source":"test","datetime":1716422400,"category":"general"}
      ]'''
      def requestHeadersUriSpec = Mock(RestClient.RequestHeadersUriSpec)
      def requestHeadersSpec = Mock(RestClient.RequestHeadersSpec)
      def responseSpec = Mock(RestClient.ResponseSpec)

    when:
      def results = client.fetchNews()

    then:
      1 * restClient.get() >> requestHeadersUriSpec
      1 * requestHeadersUriSpec.uri(_ as String, _ as Object[]) >> requestHeadersSpec
      1 * requestHeadersSpec.retrieve() >> responseSpec
      1 * responseSpec.body(com.fasterxml.jackson.databind.JsonNode.class) >> new ObjectMapper().readTree(json)

      results.size() == 1
      results[0].title() == "Valid headline"
  }

  def "given null response, when fetchNews, then return empty"() {
    given:
      def requestHeadersUriSpec = Mock(RestClient.RequestHeadersUriSpec)
      def requestHeadersSpec = Mock(RestClient.RequestHeadersSpec)
      def responseSpec = Mock(RestClient.ResponseSpec)

    when:
      def results = client.fetchNews()

    then:
      1 * restClient.get() >> requestHeadersUriSpec
      1 * requestHeadersUriSpec.uri(_ as String, _ as Object[]) >> requestHeadersSpec
      1 * requestHeadersSpec.retrieve() >> responseSpec
      1 * responseSpec.body(com.fasterxml.jackson.databind.JsonNode.class) >> null

      results.isEmpty()
  }

  def "given source name, when sourceName, then return FINNHUB_NEWS"() {
    expect:
      client.sourceName() == "FINNHUB_NEWS"
  }
}
