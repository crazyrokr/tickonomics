package com.tickonomics.ingestion.nyfed

import com.tickonomics.cdm.adapter.NyFedCdmAdapter
import com.tickonomics.persistence.repository.RateSnapshotRepository
import org.springframework.web.client.RestClient
import spock.lang.Specification
import spock.lang.Subject

class NyFedClientSpec extends Specification {

  RestClient.Builder restClientBuilder = Mock()
  RateSnapshotRepository rateRepository = Mock()
  RestClient restClient = Mock()

  @Subject
  NyFedClient client

  def setup() {
    restClientBuilder.build() >> restClient
    client = new NyFedClient(restClientBuilder, rateRepository, new NyFedCdmAdapter())
  }

  def "given valid rates, when fetchRates, then return mapped responses"() {
    given:
        def rates = [
            new NyFedClient.NyFedRateRaw("2026-05-23", 4.29),
            new NyFedClient.NyFedRateRaw("2026-05-22", 4.28)
        ]
        def response = new NyFedClient.NyFedRatesApiResponse(rates)

        def requestHeadersUriSpec = Mock(RestClient.RequestHeadersUriSpec)
        def responseSpec = Mock(RestClient.ResponseSpec)

    when:
        def results = client.fetchRates("sofr")

    then:
        1 * restClient.get() >> requestHeadersUriSpec
        1 * requestHeadersUriSpec.uri(_ as String) >> requestHeadersUriSpec
        1 * requestHeadersUriSpec.retrieve() >> responseSpec
        1 * responseSpec.body(NyFedClient.NyFedRatesApiResponse) >> response

        results.size() == 2
        results[0].value() == 4.29D
        results[0].rateType() == "sofr"
  }

  def "given null rate, when fetchRates, then filtered"() {
    given:
        def rates = [
            new NyFedClient.NyFedRateRaw("2026-05-23", null),
            new NyFedClient.NyFedRateRaw("2026-05-22", 4.28)
        ]
        def response = new NyFedClient.NyFedRatesApiResponse(rates)

        def requestHeadersUriSpec = Mock(RestClient.RequestHeadersUriSpec)
        def responseSpec = Mock(RestClient.ResponseSpec)

    when:
        def results = client.fetchRates("sofr")

    then:
        1 * restClient.get() >> requestHeadersUriSpec
        1 * requestHeadersUriSpec.uri(_ as String) >> requestHeadersUriSpec
        1 * requestHeadersUriSpec.retrieve() >> responseSpec
        1 * responseSpec.body(NyFedClient.NyFedRatesApiResponse) >> response

        results.size() == 1
        results[0].value() == 4.28D
  }

  def "given null response, when fetchRates, then return empty"() {
    given:
        def requestHeadersUriSpec = Mock(RestClient.RequestHeadersUriSpec)
        def responseSpec = Mock(RestClient.ResponseSpec)

    when:
        def results = client.fetchRates("sofr")

    then:
        1 * restClient.get() >> requestHeadersUriSpec
        1 * requestHeadersUriSpec.uri(_ as String) >> requestHeadersUriSpec
        1 * requestHeadersUriSpec.retrieve() >> responseSpec
        1 * responseSpec.body(NyFedClient.NyFedRatesApiResponse) >> null

        results.isEmpty()
  }
}
