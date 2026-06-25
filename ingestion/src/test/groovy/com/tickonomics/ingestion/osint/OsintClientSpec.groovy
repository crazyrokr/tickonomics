package com.tickonomics.ingestion.osint

import com.tickonomics.cdm.adapter.OsintCdmAdapter
import com.tickonomics.ingestion.tracing.IngestionTracer
import com.tickonomics.ingestion.writer.TimescaleDbWriter
import com.tickonomics.persistence.entity.NewsEvent
import org.springframework.web.client.RestClient
import spock.lang.Specification

import java.time.Instant

class OsintClientSpec extends Specification {

    RestClient.Builder builder = Mock()
    TimescaleDbWriter writer = Mock()
    IngestionTracer tracer = new IngestionTracer(null, false)
    OsintCdmAdapter adapter = new OsintCdmAdapter()

    def "given article with seendate and tone, when toEvent, then mapped with parsed time and tone"() {
        given:
            def article = new OsintClient.GdeltArticleRaw(
                "https://example.org/a", "Apple suppliers weak", "20260624T120000Z", "reuters.com", "-6.4,1.0,2.0", "US")

        when:
            def event = OsintClient.toEvent(article)

        then:
            event.time() == Instant.parse("2026-06-24T12:00:00Z")
            event.eventId() == "https://example.org/a"
            event.source() == "GDELT"
            event.headline() == "Apple suppliers weak"
            event.avgTone() == -6.4d
            event.actors() == "US"
    }

    def "given blank url, when toEvent, then null (no stable event id)"() {
        given:
            def article = new OsintClient.GdeltArticleRaw("", "title", "20260624T120000Z", "d", "1.0", "US")

        expect:
            OsintClient.toEvent(article) == null
    }

    def "given unparseable seendate, when toEvent, then null (cannot place in time)"() {
        given:
            def article = new OsintClient.GdeltArticleRaw("https://x", "title", "not-a-date", "d", "1.0", "US")

        expect:
            OsintClient.toEvent(article) == null
    }

    def "given blank or comma-list tone, when toEvent, then first token or zero"() {
        expect:
            toneOf(tone) == expected

        where:
            tone        | expected
            "3.5,4.1,9" | 3.5d
            ""          | 0.0d
            null        | 0.0d
            "garbage"   | 0.0d
    }

    def "given articles, when pollEvents, then each valid article written once"() {
        given:
            def client = Spy(OsintClient, constructorArgs: [builder, writer, adapter, tracer])
            client.fetchArticles() >> new OsintClient.GdeltDocResponse([
                new OsintClient.GdeltArticleRaw("https://a", "t1", "20260624T120000Z", "d", "1.0", "US"),
                new OsintClient.GdeltArticleRaw("", "t2", "20260624T120000Z", "d", "1.0", "US")
            ])

        when:
            client.pollEvents()

        then:
            1 * writer.writeNewsEvent({ NewsEvent e -> e.eventId() == "https://a" })
    }

    private double toneOf(String tone) {
        def article = new OsintClient.GdeltArticleRaw("https://x", "title", "20260624T120000Z", "d", tone, "US")
        OsintClient.toEvent(article).avgTone()
    }
}
