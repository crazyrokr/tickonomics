package com.tickonomics.computation.backtest;

import com.tickonomics.computation.client.RestClientAnalyticsWorkerClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReproducibilityServiceTest {

  @Mock
  private RestClientAnalyticsWorkerClient analyticsClient;

  private ReproducibilityService service;

  @BeforeEach
  void setUp() {
    service = new ReproducibilityService(analyticsClient, "abc123def");
  }

  @Nested
  class CaptureContext {

    @Test
    void givenValidInput_whenCaptureContext_thenAllFieldsPopulated() {
      when(analyticsClient.sendAnalysisRequest(anyString(), any()))
          .thenReturn(Map.of("rds_score", 2));

      ReproducibilityService.ReproducibilityContext ctx =
          service.captureContext("RSI_model", "{\"strategy\":\"RSI\"}", Map.of("lr", 0.01));

      assertEquals("abc123def", ctx.gitSha());
      assertNotNull(ctx.datasetHash());
      assertFalse(ctx.datasetHash().isEmpty());
      assertEquals(2, ctx.rdsScore());
      assertFalse(ctx.modelHyperparams().isEmpty());
    }

    @Test
    void givenWorkerError_whenCaptureContext_thenRdsScoreZero() {
      when(analyticsClient.sendAnalysisRequest(anyString(), any()))
          .thenReturn(Map.of("error", "unavailable"));

      ReproducibilityService.ReproducibilityContext ctx =
          service.captureContext("model", "{}", Map.of());

      assertEquals(0, ctx.rdsScore());
    }

    @Test
    void givenNullHyperparams_whenCaptureContext_thenEmptyJson() {
      when(analyticsClient.sendAnalysisRequest(anyString(), any()))
          .thenReturn(Map.of("rds_score", 0));

      ReproducibilityService.ReproducibilityContext ctx =
          service.captureContext("model", "{}", null);

      assertEquals("{}", ctx.modelHyperparams());
    }

    @Test
    void givenSameInput_whenCaptureContext_thenSameHash() {
      when(analyticsClient.sendAnalysisRequest(anyString(), any()))
          .thenReturn(Map.of("rds_score", 1));

      ReproducibilityService.ReproducibilityContext ctx1 =
          service.captureContext("model", "same_data", Map.of());
      ReproducibilityService.ReproducibilityContext ctx2 =
          service.captureContext("model", "same_data", Map.of());

      assertEquals(ctx1.datasetHash(), ctx2.datasetHash());
    }

    @Test
    void givenDifferentInput_whenCaptureContext_thenDifferentHash() {
      when(analyticsClient.sendAnalysisRequest(anyString(), any()))
          .thenReturn(Map.of("rds_score", 1));

      ReproducibilityService.ReproducibilityContext ctx1 =
          service.captureContext("model", "data_a", Map.of());
      ReproducibilityService.ReproducibilityContext ctx2 =
          service.captureContext("model", "data_b", Map.of());

      assertFalse(ctx1.datasetHash().equals(ctx2.datasetHash()));
    }
  }
}
