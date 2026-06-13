package com.tickonomics.ingestion.writer

import com.tickonomics.cdm.adapter.FredCdmAdapter
import com.tickonomics.cdm.adapter.NyFedCdmAdapter
import com.tickonomics.ingestion.fred.FredClient
import com.tickonomics.ingestion.nyfed.NyFedClient
import com.tickonomics.ingestion.tracing.IngestionTracer
import com.tickonomics.persistence.entity.RateSnapshot
import com.tickonomics.persistence.repository.RateSnapshotRepository
import com.tickonomics.persistence.repository.TickDataRepository
import org.springframework.web.client.RestClient
import spock.lang.Specification
import spock.lang.Subject

import java.time.Instant

class IdempotencyRoutingSpec extends Specification {

    static final Instant NOW = Instant.parse("2026-05-24T12:00:00Z")

    TickDataRepository tickDataRepository = Mock()
    RateSnapshotRepository rateSnapshotRepository = Mock()

    @Subject
    IdempotencyGuard idempotencyGuard = new IdempotencyGuard(86_400_000L)

    @Subject
    TimescaleDbWriter writer

    RestClient.Builder restClientBuilder = Mock()
    RestClient restClient = Mock()
    IngestionTracer tracer = new IngestionTracer(null, false)

    def setup() {
        restClientBuilder.build() >> restClient
        writer = new TimescaleDbWriter(tickDataRepository, rateSnapshotRepository, idempotencyGuard, tracer)
        writer.batchSize = 500
    }

    def "given FredClient writes same observation twice, when routed through writer, then no duplicate"() {
        given:
            def fredClient = new FredClient(restClientBuilder, writer, new FredCdmAdapter(), tracer)
            def obs = [
                new FredClient.FredObservationRaw("2026-05-23", "4.33")
            ]
            def response = new FredClient.FredSeriesResponse(obs)
            def requestHeadersUriSpec = Mock(RestClient.RequestHeadersUriSpec)
            def responseSpec = Mock(RestClient.ResponseSpec)

            restClient.get() >> requestHeadersUriSpec
            requestHeadersUriSpec.uri(_ as String, _ as Object[]) >> requestHeadersUriSpec
            requestHeadersUriSpec.retrieve() >> responseSpec
            responseSpec.body(FredClient.FredSeriesResponse) >> response

        when:
            fredClient.pollAllSeries()
            fredClient.pollAllSeries()
            writer.flushAll()

        then:
            1 * rateSnapshotRepository.saveAllIdempotent(_ as List) >> { List batch ->
                assert batch.size() == 1
            }
    }

    def "given NyFedClient writes same rate twice, when routed through writer, then no duplicate"() {
        given:
            def nyfedClient = new NyFedClient(restClientBuilder, writer, new NyFedCdmAdapter(), tracer)
            def rates = [
                new NyFedClient.NyFedRateRaw("2026-05-23", 4.29)
            ]
            def response = new NyFedClient.NyFedRatesApiResponse(rates)
            def requestHeadersUriSpec = Mock(RestClient.RequestHeadersUriSpec)
            def responseSpec = Mock(RestClient.ResponseSpec)

            restClient.get() >> requestHeadersUriSpec
            requestHeadersUriSpec.uri(_ as String) >> requestHeadersUriSpec
            requestHeadersUriSpec.retrieve() >> responseSpec
            responseSpec.body(NyFedClient.NyFedRatesApiResponse) >> response

        when:
            nyfedClient.pollAllRates()
            nyfedClient.pollAllRates()
            writer.flushAll()

        then:
            1 * rateSnapshotRepository.saveAllIdempotent(_ as List) >> { List batch ->
                assert batch.size() == 1
            }
    }

    def "given writeRate called directly, when different keys, then all buffered"() {
        given:
            def rate1 = new RateSnapshot(Instant.parse("2026-05-23T00:00:00Z"), "SOFR", 4.29, "NY_FED", null, null)
            def rate2 = new RateSnapshot(Instant.parse("2026-05-24T00:00:00Z"), "SOFR", 4.30, "NY_FED", null, null)

        when:
            writer.writeRate(rate1)
            writer.writeRate(rate2)
            writer.flushAll()

        then:
            1 * rateSnapshotRepository.saveAllIdempotent { List batch -> batch.size() == 2 }
    }
}
