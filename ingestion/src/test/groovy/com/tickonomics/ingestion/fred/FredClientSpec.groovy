package com.tickonomics.ingestion.fred

import com.tickonomics.cdm.adapter.FredCdmAdapter
import com.tickonomics.persistence.repository.RateSnapshotRepository
import org.springframework.web.client.RestClient
import spock.lang.Specification
import spock.lang.Subject

class FredClientSpec extends Specification {

  RestClient.Builder restClientBuilder = Mock()
  RateSnapshotRepository rateRepository = Mock()
  RestClient restClient = Mock()

  @Subject
  FredClient client

  def setup() {
    restClientBuilder.build() >> restClient
    client = new FredClient(restClientBuilder, rateRepository, new FredCdmAdapter())
  }

  def "given valid observations, when fetchSeries, then return mapped observations"() {
    given:
        def obs = [
            new FredClient.FredObservationRaw("2026-05-23", "4.33"),
            new FredClient.FredObservationRaw("2026-05-22", "4.35")
        ]
        def response = new FredClient.FredSeriesResponse(obs)

        def requestHeadersUriSpec = Mock(RestClient.RequestHeadersUriSpec)
        def responseSpec = Mock(RestClient.ResponseSpec)

    when:
        def results = client.fetchSeries("EFFR")

    then:
        1 * restClient.get() >> requestHeadersUriSpec
        1 * requestHeadersUriSpec.uri(_ as String, _ as Object[]) >> requestHeadersUriSpec
        1 * requestHeadersUriSpec.retrieve() >> responseSpec
        1 * responseSpec.body(FredClient.FredSeriesResponse) >> response

        results.size() == 2
        results[0].value() == 4.33D
        results[0].seriesId() == "EFFR"
  }

  def "given dot value, when fetchSeries, then filtered"() {
    given:
        def obs = [
            new FredClient.FredObservationRaw("2026-05-23", "."),
            new FredClient.FredObservationRaw("2026-05-22", "4.35")
        ]
        def response = new FredClient.FredSeriesResponse(obs)

        def requestHeadersUriSpec = Mock(RestClient.RequestHeadersUriSpec)
        def responseSpec = Mock(RestClient.ResponseSpec)

    when:
        def results = client.fetchSeries("EFFR")

    then:
        1 * restClient.get() >> requestHeadersUriSpec
        1 * requestHeadersUriSpec.uri(_ as String, _ as Object[]) >> requestHeadersUriSpec
        1 * requestHeadersUriSpec.retrieve() >> responseSpec
        1 * responseSpec.body(FredClient.FredSeriesResponse) >> response

        results.size() == 1
        results[0].value() == 4.35D
  }

  def "given null response, when fetchSeries, then return empty"() {
    given:
        def requestHeadersUriSpec = Mock(RestClient.RequestHeadersUriSpec)
        def responseSpec = Mock(RestClient.ResponseSpec)

    when:
        def results = client.fetchSeries("EFFR")

    then:
        1 * restClient.get() >> requestHeadersUriSpec
        1 * requestHeadersUriSpec.uri(_ as String, _ as Object[]) >> requestHeadersUriSpec
        1 * requestHeadersUriSpec.retrieve() >> responseSpec
        1 * responseSpec.body(FredClient.FredSeriesResponse) >> null

        results.isEmpty()
  }
}
