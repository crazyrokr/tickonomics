package com.tickonomics.web.perspex

import com.tickonomics.contracts.client.AnalyticsWorkerClient
import com.tickonomics.persistence.repository.NewsEventRepository
import com.tickonomics.persistence.repository.PredictionMarketQuoteRepository
import spock.lang.Specification
import spock.lang.Subject

class PerspexControllerSpec extends Specification {

    PredictionMarketQuoteRepository quoteRepository = Mock()
    NewsEventRepository newsRepository = Mock()
    AnalyticsWorkerClient workerClient = Mock()

    @Subject
    PerspexController controller = new PerspexController(quoteRepository, newsRepository, workerClient)

    def "given worker returns mismatches, when GET mismatches, then 200 with report body"() {
        given:
            quoteRepository.findRecent(_, _) >> []
            newsRepository.findRecent(_, _) >> []
            workerClient.sendAnalysisRequest(_, _) >> [mismatches: [], evaluated_markets: 0]

        when:
            def resp = controller.mismatches()

        then:
            resp.statusCode.is2xxSuccessful()
            (resp.body as Map).containsKey("mismatches")
    }

    def "given worker unavailable, when GET mismatches, then 503"() {
        given:
            quoteRepository.findRecent(_, _) >> []
            newsRepository.findRecent(_, _) >> []
            workerClient.sendAnalysisRequest(_, _) >> [error: "worker down"]

        when:
            def resp = controller.mismatches()

        then:
            resp.statusCode.value() == 503
    }
}
