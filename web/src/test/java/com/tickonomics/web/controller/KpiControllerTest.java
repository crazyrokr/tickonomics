package com.tickonomics.web.controller;

import com.tickonomics.computation.kpi.CorrelationEngine;
import com.tickonomics.computation.kpi.KpiProcessor;
import com.tickonomics.computation.kpi.KpiResult;
import com.tickonomics.computation.kpi.RegimeDetector;
import com.tickonomics.persistence.entity.IliHistory;
import com.tickonomics.persistence.repository.IliHistoryRepository;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class KpiControllerTest {

    private MockMvc mockMvc;

    @Mock
    private IliHistoryRepository iliHistoryRepository;

    @Mock
    private KpiProcessor kpiProcessor;

    @Mock
    private RegimeDetector regimeDetector;

    @Mock
    private CorrelationEngine correlationEngine;

    private KpiController controller;

    @BeforeEach
    void setUp() {
        controller = new KpiController(kpiProcessor, iliHistoryRepository,
                regimeDetector, correlationEngine);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Nested
    class IliTests {

        @Test
        void returnsIliValueWhenHistoryExists() throws Exception {
            var now = Instant.now();
            Mockito.when(iliHistoryRepository.findLatest())
                    .thenReturn(new IliHistory(now, 0.45, 0.2, 0.3, -0.1, "VALID",
                            "[0.4,0.35,0.25]", "NORMAL", 0.0, null, null));

            mockMvc.perform(get("/api/v1/kpi/ili"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.value").value(0.45))
                    .andExpect(jsonPath("$.status").value("VALID"))
                    .andExpect(jsonPath("$.activeWeights.RRP").value(0.4))
                    .andExpect(jsonPath("$.activeWeights.SPREAD").value(0.35))
                    .andExpect(jsonPath("$.activeWeights.VOL").value(0.25))
                    .andExpect(jsonPath("$.timestamp").isNotEmpty());
        }

        @Test
        void returnsFallbackWhenNoHistory() throws Exception {
            Mockito.when(iliHistoryRepository.findLatest()).thenReturn(null);

            mockMvc.perform(get("/api/v1/kpi/ili"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.value").value(0.0))
                    .andExpect(jsonPath("$.status").value("INSUFFICIENT_DATA"));
        }
    }

    @Nested
    class IliHistoryTests {

        @Test
        void returnsHistoryPoints() throws Exception {
            var now = Instant.now();
            Mockito.when(iliHistoryRepository.findLatestN(100))
                    .thenReturn(java.util.List.of(
                            new IliHistory(now.minusSeconds(3600), 0.5, 0.1, 0.2, -0.1,
                                    "VALID", "[0.4,0.35,0.25]", "NORMAL", 0.0, null, null),
                            new IliHistory(now, 0.45, 0.2, 0.3, -0.1,
                                    "VALID", "[0.4,0.35,0.25]", "NORMAL", 0.0, null, null)));

            mockMvc.perform(get("/api/v1/kpi/ili/history"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$.length()").value(2));
        }

        @Test
        void returnsEmptyArrayWhenNoHistory() throws Exception {
            Mockito.when(iliHistoryRepository.findLatestN(100))
                    .thenReturn(java.util.List.of());

            mockMvc.perform(get("/api/v1/kpi/ili/history"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$").isEmpty());
        }
    }

    @Nested
    class LiquidityStressTests {

        @Test
        void returnsIndex() throws Exception {
            Mockito.when(kpiProcessor.computeLiquidityStressIndex())
                    .thenReturn(new KpiResult("Liquidity Stress Index",
                            0.85, "ELEVATED", "z-score", "description"));

            mockMvc.perform(get("/api/v1/kpi/liquidity-stress"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.value").value(0.85))
                    .andExpect(jsonPath("$.trend").value("DECELERATING"))
                    .andExpect(jsonPath("$.timestamp").isNotEmpty());
        }

        @Test
        void returnsStableTrendWhenNormal() throws Exception {
            Mockito.when(kpiProcessor.computeLiquidityStressIndex())
                    .thenReturn(new KpiResult("Liquidity Stress Index",
                            0.2, "NORMAL", "z-score", "description"));

            mockMvc.perform(get("/api/v1/kpi/liquidity-stress"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.trend").value("STABLE"));
        }
    }

    @Nested
    class RepoEquityBetaTests {

        @Test
        void returnsBetaArray() throws Exception {
            Mockito.when(kpiProcessor.computeRepoEquityBeta(Mockito.any()))
                    .thenReturn(new KpiResult("Repo/Equity Beta",
                            1.2, "NORMAL", "beta", "description"));

            mockMvc.perform(get("/api/v1/kpi/repo-equity-beta"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$[0].symbol").value("SPY"))
                    .andExpect(jsonPath("$[0].beta").value(1.2));
        }

        @Test
        void returnsEmptyArrayWhenUnknown() throws Exception {
            Mockito.when(kpiProcessor.computeRepoEquityBeta(Mockito.any()))
                    .thenReturn(new KpiResult("Repo/Equity Beta",
                            Double.NaN, "UNKNOWN", "beta", "Insufficient data"));

            mockMvc.perform(get("/api/v1/kpi/repo-equity-beta"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$").isEmpty());
        }
    }

    @Nested
    class RrpDrainTests {

        @Test
        void returnsVelocity() throws Exception {
            Mockito.when(kpiProcessor.computeRrpDrainVelocity())
                    .thenReturn(new KpiResult("RRP Drain Velocity",
                            -3.5, "ELEVATED", "bps/day", "description"));

            mockMvc.perform(get("/api/v1/kpi/rrp-drain"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.velocity").value(-3.5))
                    .andExpect(jsonPath("$.trend").value("DECELERATING"))
                    .andExpect(jsonPath("$.timestamp").isNotEmpty());
        }
    }

    @Nested
    class VolatilityRegimeTests {

        @Test
        void returnsRegime() throws Exception {
            Mockito.when(kpiProcessor.computeVolatilityRegime())
                    .thenReturn(new KpiResult("Volatility Regime",
                            35.0, "NORMAL", "%", "description"));

            mockMvc.perform(get("/api/v1/kpi/volatility-regime"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.regime").value("NORMAL"))
                    .andExpect(jsonPath("$.currentPrice").value(35.0))
                    .andExpect(jsonPath("$.timestamp").isNotEmpty());
        }

        @Test
        void mapsLowVolStatusToLowVolRegime() throws Exception {
            Mockito.when(kpiProcessor.computeVolatilityRegime())
                    .thenReturn(new KpiResult("Volatility Regime",
                            15.0, "LOW_VOL", "%", "description"));

            mockMvc.perform(get("/api/v1/kpi/volatility-regime"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.regime").value("LOW_VOL"));
        }

        @Test
        void mapsExtremeStatusToHighVolRegime() throws Exception {
            Mockito.when(kpiProcessor.computeVolatilityRegime())
                    .thenReturn(new KpiResult("Volatility Regime",
                            85.0, "EXTREME", "%", "description"));

            mockMvc.perform(get("/api/v1/kpi/volatility-regime"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.regime").value("HIGH_VOL"));
        }
    }

    @Nested
    class SystemicRiskHeatmapTests {

        @Test
        void returnsValues() throws Exception {
            Mockito.when(kpiProcessor.computeSystemicRiskHeatmap())
                    .thenReturn(new KpiResult("Systemic Risk Heatmap",
                            45.0, "ELEVATED", "%", "description"));

            mockMvc.perform(get("/api/v1/kpi/systemic-risk-heatmap"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.sofrPctlRange").value(45.0))
                    .andExpect(jsonPath("$.timestamp").isNotEmpty());
        }
    }

    @Nested
    class NotImplementedTests {

        @Test
        void correlationMatrixReturns501() throws Exception {
            mockMvc.perform(get("/api/v1/kpi/correlation-matrix"))
                    .andExpect(status().isNotImplemented());
        }

        @Test
        void evtRiskReturns501() throws Exception {
            mockMvc.perform(get("/api/v1/kpi/evt-risk"))
                    .andExpect(status().isNotImplemented());
        }

        @Test
        void efficiencyGapReturns501() throws Exception {
            mockMvc.perform(get("/api/v1/kpi/efficiency-gap"))
                    .andExpect(status().isNotImplemented());
        }

        @Test
        void monetaryPolicyReturns501() throws Exception {
            mockMvc.perform(get("/api/v1/kpi/monetary-policy"))
                    .andExpect(status().isNotImplemented());
        }
    }
}
