package com.tickonomics.ingestion.options

import com.tickonomics.ingestion.writer.TimescaleDbWriter
import org.springframework.web.client.RestClient
import spock.lang.Specification
import spock.lang.Subject

import java.time.LocalDate

class OptionsDataClientSpec extends Specification {

    RestClient.Builder restClientBuilder = Mock()
    TimescaleDbWriter writer = Mock()
    RestClient restClient = Mock()

    @Subject
    OptionsDataClient client

    def setup() {
        restClientBuilder.build() >> restClient
        client = new OptionsDataClient(restClientBuilder, writer)
    }

    def "given valid response, when fetchOptionsSnapshot, then return mapped snapshots"() {
        given:
            def results = [
                new OptionsDataClient.PolygonOptionResult("SPY", new BigDecimal("450"), LocalDate.now(), "call",
                    5.50, 5.60, 5.55, 0.25, 0.55, 0.04, -0.02, 0.15, 0.01, 1000, 450.50)
            ]
            def response = new OptionsDataClient.PolygonOptionsResponse(results)
            def requestHeadersUriSpec = Mock(RestClient.RequestHeadersUriSpec)
            def responseSpec = Mock(RestClient.ResponseSpec)

        when:
            def snapshots = client.fetchOptionsSnapshot("SPY")

        then:
            1 * restClient.get() >> requestHeadersUriSpec
            1 * requestHeadersUriSpec.uri(_ as String, _ as Object[]) >> requestHeadersUriSpec
            1 * requestHeadersUriSpec.retrieve() >> responseSpec
            1 * responseSpec.body(OptionsDataClient.PolygonOptionsResponse) >> response

            snapshots.size() == 1
            snapshots[0].underlying() == "SPY"
            snapshots[0].strike() == new BigDecimal("450")
    }

    def "given null response, when fetchOptionsSnapshot, then return empty"() {
        given:
            def requestHeadersUriSpec = Mock(RestClient.RequestHeadersUriSpec)
            def responseSpec = Mock(RestClient.ResponseSpec)

        when:
            def snapshots = client.fetchOptionsSnapshot("SPY")

        then:
            1 * restClient.get() >> requestHeadersUriSpec
            1 * requestHeadersUriSpec.uri(_ as String, _ as Object[]) >> requestHeadersUriSpec
            1 * requestHeadersUriSpec.retrieve() >> responseSpec
            1 * responseSpec.body(OptionsDataClient.PolygonOptionsResponse) >> null

            snapshots.isEmpty()
    }
}
