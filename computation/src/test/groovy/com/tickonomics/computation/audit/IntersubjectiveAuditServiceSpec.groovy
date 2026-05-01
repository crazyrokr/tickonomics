package com.tickonomics.computation.audit

import spock.lang.Specification
import spock.lang.Subject

class IntersubjectiveAuditServiceSpec extends Specification {

  @Subject
  IntersubjectiveAuditService service = new IntersubjectiveAuditService()

  def "reconstructPath returns ordered entries with correct hashes"() {
    given: "a data point ID"
        def dataPointId = UUID.randomUUID()

    when: "logging several transformations"
        service.logTransformation(dataPointId, CodingRule.RAW_FETCH, '{"rate":5.33}', 5.33)
        service.logTransformation(dataPointId, CodingRule.GAP_FILL_LOCF, '{"rate":5.33}', 5.33)
        service.logTransformation(dataPointId, CodingRule.NORMALIZATION, '{"rate":5.33}', 0.85)

    and: "reconstructing the path"
        def path = service.reconstructPath(dataPointId)

    then: "entries appear in order"
        path.size() == 3
        path[0].codingRule() == CodingRule.RAW_FETCH.ruleId()
        path[1].codingRule() == CodingRule.GAP_FILL_LOCF.ruleId()
        path[2].codingRule() == CodingRule.NORMALIZATION.ruleId()

    and: "hashes are valid and dataPointId is consistent"
        path.every { it.inputHash() != null && it.inputHash().length() == 64 && it.dataPointId() == dataPointId }
  }

  def "composite IR score reflects minimum IR score across steps"() {
    given: "a data point ID"
        def dataPointId = UUID.randomUUID()

    when: "logging transformations with different IR scores"
        service.logTransformation(dataPointId, CodingRule.RAW_FETCH, "raw", 5.33)
        service.logTransformation(dataPointId, CodingRule.GAP_FILL_LOCF, "gap", 5.33) // GAP_FILL_LOCF has IR 0.8
        service.logTransformation(dataPointId, CodingRule.NORMALIZATION, "norm", 0.85)

    then: "the composite IR score is the minimum of all steps"
        service.computeCompositeIrScore(dataPointId) == 0.8D
  }

  def "data point is not actionable if IR score is below 0.9 threshold"() {
    given: "a data point ID"
        def dataPointId = UUID.randomUUID()

    when: "logging a transformation with IR score 0.8"
        service.logTransformation(dataPointId, CodingRule.RAW_FETCH, "raw", 5.33)
        service.logTransformation(dataPointId, CodingRule.GAP_FILL_LOCF, "gap", 5.33)

    then: "it is not actionable"
        !service.isActionable(dataPointId)
  }

  def "data point is actionable if all steps have IR score >= 0.9"() {
    given: "a data point ID"
        def dataPointId = UUID.randomUUID()

    when: "logging transformations with IR scores >= 0.9"
        service.logTransformation(dataPointId, CodingRule.RAW_FETCH, "raw", 5.33)
        service.logTransformation(dataPointId, CodingRule.ADAPTER_MAP, "map", 5.33)
        service.logTransformation(dataPointId, CodingRule.NORMALIZATION, "norm", 0.85)

    then: "it is actionable"
        service.isActionable(dataPointId)
  }

  def "unknown data point returns empty path and zero IR score"() {
    expect:
        service.reconstructPath(UUID.randomUUID()).isEmpty()
        service.computeCompositeIrScore(UUID.randomUUID()) == 0.0D
  }

  def "GAP_FILL_LINEAR (irScore=0.5) makes the path not actionable"() {
    given: "a data point ID"
        def dataPointId = UUID.randomUUID()

    when: "logging a GAP_FILL_LINEAR transformation"
        service.logTransformation(dataPointId, CodingRule.RAW_FETCH, "raw", 5.33)
        service.logTransformation(dataPointId, CodingRule.GAP_FILL_LINEAR, "gap", 5.33)

    then: "it is not actionable and IR score is 0.5"
        !service.isActionable(dataPointId)
        service.computeCompositeIrScore(dataPointId) == 0.5D
  }

  def "sha256 produces consistent and correct length hash"() {
    given:
        def input = '{"sofr":5.33}'

    when:
        def hash1 = IntersubjectiveAuditService.sha256(input)
        def hash2 = IntersubjectiveAuditService.sha256(input)

    then:
        hash1 == hash2
        hash1.length() == 64
  }

  def "sha256 produces different hashes for different inputs"() {
    expect:
        IntersubjectiveAuditService.sha256('{"sofr":5.33}') != IntersubjectiveAuditService.sha256('{"sofr":5.34}')
  }
}
