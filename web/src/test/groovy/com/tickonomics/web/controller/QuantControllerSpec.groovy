package com.tickonomics.web.controller

import com.tickonomics.computation.audit.CodingRule
import com.tickonomics.computation.audit.IntersubjectiveAuditService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.web.servlet.MockMvc
import spock.lang.Specification

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(value = [QuantController, HealthController], excludeAutoConfiguration = [SecurityAutoConfiguration])
@Import(IntersubjectiveAuditService.class)
@ContextConfiguration(classes = [QuantController, HealthController, IntersubjectiveAuditService])
class QuantControllerSpec extends Specification {

  @Autowired
  MockMvc mockMvc

  @Autowired
  IntersubjectiveAuditService auditService

  def "GET /api/v1/quant/signals/active returns empty array when no active signals exist"() {
    expect:
        mockMvc.perform(get("/api/v1/quant/signals/active"))
            .andExpect(status().isOk())
            .andExpect(jsonPath('$').isArray())
            .andExpect(jsonPath('$').isEmpty())
  }

  def "GET /api/v1/quant/signals/active accepts optional category query parameter"() {
    expect:
        mockMvc.perform(get("/api/v1/quant/signals/active")
            .param("category", "OPTIONS"))
            .andExpect(status().isOk())
  }

  def "GET /api/v1/quant/strategies/active returns empty array when no active strategies exist"() {
    expect:
        mockMvc.perform(get("/api/v1/quant/strategies/active"))
            .andExpect(status().isOk())
            .andExpect(jsonPath('$').isArray())
            .andExpect(jsonPath('$').isEmpty())
  }

  def "POST /api/v1/quant/strategies/options/butterfly returns 200 with neutral alpha signal for given underlying"() {
    expect:
        mockMvc.perform(post("/api/v1/quant/strategies/options/butterfly")
            .param("underlying", "SPY"))
            .andExpect(status().isOk())
            .andExpect(jsonPath('$.symbol').value("SPY"))
            .andExpect(jsonPath('$.direction').value("NEUTRAL"))
            .andExpect(jsonPath('$.strength').value(0.0))
            .andExpect(jsonPath('$.confidence').value(0.0))
  }

  def "GET /api/v1/quant/risk/tail-parameters returns 200 with empty map"() {
    expect:
        mockMvc.perform(get("/api/v1/quant/risk/tail-parameters"))
            .andExpect(status().isOk())
            .andExpect(jsonPath('$').isEmpty())
  }

  def "GET /api/v1/quant/risk/evt-tail returns 200 with empty map"() {
    expect:
        mockMvc.perform(get("/api/v1/quant/risk/evt-tail"))
            .andExpect(status().isOk())
            .andExpect(jsonPath('$').isEmpty())
  }

  def "GET /api/v1/quant/audit/intersubjective-reproducibility/{id} returns empty path for unknown data point"() {
    given:
        def unknownId = UUID.randomUUID()

    expect:
        mockMvc.perform(get("/api/v1/quant/audit/intersubjective-reproducibility/{id}", unknownId))
            .andExpect(status().isOk())
            .andExpect(jsonPath('$.signalId').value(unknownId.toString()))
            .andExpect(jsonPath('$.path').isArray())
            .andExpect(jsonPath('$.path').isEmpty())
            .andExpect(jsonPath('$.compositeIrScore').value(0.0))
  }

  def "GET /api/v1/quant/audit/intersubjective-reproducibility/{id} returns audit path entries for data point with logged transformations"() {
    given:
        def dataPointId = UUID.randomUUID()
        auditService.logTransformation(dataPointId, CodingRule.RAW_FETCH, "payload1", 100.0D)
        auditService.logTransformation(dataPointId, CodingRule.NORMALIZATION, "payload2", 0.85D)

    expect:
        mockMvc.perform(get("/api/v1/quant/audit/intersubjective-reproducibility/{id}", dataPointId))
            .andExpect(status().isOk())
            .andExpect(jsonPath('$.signalId').value(dataPointId.toString()))
            .andExpect(jsonPath('$.path').isArray())
            .andExpect(jsonPath('$.path.length()').value(2))
            .andExpect(jsonPath('$.path[0].ruleName').value("01"))
            .andExpect(jsonPath('$.path[0].ruleVersion').value("1.0"))
            .andExpect(jsonPath('$.path[0].outputValue').value(100.0))
            .andExpect(jsonPath('$.path[1].ruleName').value("04"))
            .andExpect(jsonPath('$.compositeIrScore').value(1.0))
  }

  def "GET /api/v1/quant/macro/shock-response returns 200 with empty map"() {
    expect:
        mockMvc.perform(get("/api/v1/quant/macro/shock-response"))
            .andExpect(status().isOk())
            .andExpect(jsonPath('$').isEmpty())
  }

  def "GET /health returns UP status"() {
    expect:
        mockMvc.perform(get("/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath('$.status').value("UP"))
  }
}

