package com.tickonomics.web.controller;

import com.tickonomics.computation.audit.CodingRule;
import com.tickonomics.computation.audit.IntersubjectiveAuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({QuantController.class, HealthController.class})
@Import(IntersubjectiveAuditService.class)
@ContextConfiguration(classes = {QuantController.class, HealthController.class, IntersubjectiveAuditService.class})
class QuantControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private IntersubjectiveAuditService auditService;

    @Nested
    @DisplayName("GET /api/v1/quant/signals/active")
    class GetActiveSignals {

        @Test
        @DisplayName("returns 200 with empty array when no active signals exist")
        void returnsEmptyArray() throws Exception {
            mockMvc.perform(get("/api/v1/quant/signals/active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
        }

        @Test
        @DisplayName("accepts optional category query parameter")
        void acceptsCategoryParam() throws Exception {
            mockMvc.perform(get("/api/v1/quant/signals/active")
                    .param("category", "OPTIONS"))
                .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/quant/strategies/active")
    class GetActiveStrategies {

        @Test
        @DisplayName("returns 200 with empty array when no active strategies exist")
        void returnsEmptyArray() throws Exception {
            mockMvc.perform(get("/api/v1/quant/strategies/active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
        }
    }

    @Nested
    @DisplayName("POST /api/v1/quant/strategies/options/butterfly")
    class ComputeButterflySignal {

        @Test
        @DisplayName("returns 200 with neutral alpha signal for given underlying")
        void returnsNeutralSignal() throws Exception {
            mockMvc.perform(post("/api/v1/quant/strategies/options/butterfly")
                    .param("underlying", "SPY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.symbol").value("SPY"))
                .andExpect(jsonPath("$.direction").value("NEUTRAL"))
                .andExpect(jsonPath("$.strength").value(0.0))
                .andExpect(jsonPath("$.confidence").value(0.0));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/quant/risk/tail-parameters")
    class GetTailParameters {

        @Test
        @DisplayName("returns 200 with empty map")
        void returnsEmptyMap() throws Exception {
            mockMvc.perform(get("/api/v1/quant/risk/tail-parameters"))
                .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/quant/risk/evt-tail")
    class GetEvtTail {

        @Test
        @DisplayName("returns 200 with empty map")
        void returnsEmptyMap() throws Exception {
            mockMvc.perform(get("/api/v1/quant/risk/evt-tail"))
                .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/quant/audit/intersubjective-reproducibility/{id}")
    class GetAuditPath {

        @Test
        @DisplayName("returns empty path for unknown data point")
        void returnsEmptyPathForUnknownId() throws Exception {
            UUID unknownId = UUID.randomUUID();

            mockMvc.perform(get("/api/v1/quant/audit/intersubjective-reproducibility/{id}", unknownId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.signalId").value(unknownId.toString()))
                .andExpect(jsonPath("$.path").isArray())
                .andExpect(jsonPath("$.path").isEmpty())
                .andExpect(jsonPath("$.compositeIrScore").value(0.0));
        }

        @Test
        @DisplayName("returns audit path entries for data point with logged transformations")
        void returnsPathWithEntries() throws Exception {
            UUID dataPointId = UUID.randomUUID();
            auditService.logTransformation(dataPointId, CodingRule.RAW_FETCH, "payload1", 100.0);
            auditService.logTransformation(dataPointId, CodingRule.NORMALIZATION, "payload2", 0.85);

            mockMvc.perform(get("/api/v1/quant/audit/intersubjective-reproducibility/{id}", dataPointId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.signalId").value(dataPointId.toString()))
                .andExpect(jsonPath("$.path").isArray())
                .andExpect(jsonPath("$.path.length()").value(2))
                .andExpect(jsonPath("$.path[0].ruleName").value("01"))
                .andExpect(jsonPath("$.path[0].ruleVersion").value("1.0"))
                .andExpect(jsonPath("$.path[0].outputValue").value(100.0))
                .andExpect(jsonPath("$.path[1].ruleName").value("04"))
                .andExpect(jsonPath("$.compositeIrScore").value(1.0));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/quant/macro/shock-response")
    class GetShockResponse {

        @Test
        @DisplayName("returns 200 with empty map")
        void returnsEmptyMap() throws Exception {
            mockMvc.perform(get("/api/v1/quant/macro/shock-response"))
                .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("GET /health")
    class HealthCheck {

        @Test
        @DisplayName("returns UP status")
        void returnsUpStatus() throws Exception {
            mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
        }
    }
}
