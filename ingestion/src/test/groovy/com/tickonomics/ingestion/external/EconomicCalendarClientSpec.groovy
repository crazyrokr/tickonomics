package com.tickonomics.ingestion.external

import org.springframework.web.client.RestClient
import spock.lang.Specification
import spock.lang.Subject

class EconomicCalendarClientSpec extends Specification {

    RestClient.Builder restClientBuilder = Mock()
    RestClient restClient = Mock()

    @Subject
    EconomicCalendarClient client

    def setup() {
        restClientBuilder.build() >> restClient
        client = new EconomicCalendarClient(restClientBuilder)
    }

    def "given valid releases, when fetchCalendarEvents, then return mapped events"() {
        given:
            def releases = [
                new EconomicCalendarClient.FredRelease(1, "Employment Situation"),
                new EconomicCalendarClient.FredRelease(2, "GDP")
            ]
            def response = new EconomicCalendarClient.FredReleasesResponse(releases)
            def requestHeadersUriSpec = Mock(RestClient.RequestHeadersUriSpec)
            def responseSpec = Mock(RestClient.ResponseSpec)

        when:
            def events = client.fetchCalendarEvents()

        then:
            1 * restClient.get() >> requestHeadersUriSpec
            1 * requestHeadersUriSpec.uri(_ as String, _ as Object[]) >> requestHeadersUriSpec
            1 * requestHeadersUriSpec.retrieve() >> responseSpec
            1 * responseSpec.body(EconomicCalendarClient.FredReleasesResponse) >> response

            events.size() == 2
            events[0].name() == "Employment Situation"
            events[0].eventType() == "ECONOMIC_EVENT"
    }

    def "given null response, when fetchCalendarEvents, then return empty"() {
        given:
            def requestHeadersUriSpec = Mock(RestClient.RequestHeadersUriSpec)
            def responseSpec = Mock(RestClient.ResponseSpec)

        when:
            def events = client.fetchCalendarEvents()

        then:
            1 * restClient.get() >> requestHeadersUriSpec
            1 * requestHeadersUriSpec.uri(_ as String, _ as Object[]) >> requestHeadersUriSpec
            1 * requestHeadersUriSpec.retrieve() >> responseSpec
            1 * responseSpec.body(EconomicCalendarClient.FredReleasesResponse) >> null

            events.isEmpty()
    }
}
