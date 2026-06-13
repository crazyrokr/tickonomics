package com.tickonomics.computation.anomaly;

import com.tickonomics.computation.model.ModelArtifactService;
import com.tickonomics.contracts.client.AnalyticsWorkerClient;
import com.tickonomics.persistence.entity.IliHistory;
import com.tickonomics.persistence.entity.ModelArtifact;
import com.tickonomics.persistence.entity.RateSnapshot;
import com.tickonomics.persistence.repository.IliHistoryRepository;
import com.tickonomics.persistence.repository.RateSnapshotRepository;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnomalyScoringServiceTest {

  @Mock
  private IliHistoryRepository iliRepository;

  @Mock
  private RateSnapshotRepository rateRepository;

  @Mock
  private ModelArtifactService modelArtifactService;

  @Mock
  private AnalyticsWorkerClient analyticsClient;

  private AnomalyScoringService service;

  private static final Instant NOW = Instant.now();

  @BeforeEach
  void setUp() {
    service = new AnomalyScoringService(
        iliRepository, rateRepository, modelArtifactService, analyticsClient, 100);
  }

  private IliHistory buildIliHistory(Instant time) {
    return new IliHistory(
        time, 0.85 + Math.random(), 1.2, -0.5, 0.3, "VALID",
        "{}", null, null, null, null);
  }

  private RateSnapshot buildRateSnapshot(Instant time, String rateType) {
    return new RateSnapshot(time, rateType, 4.29, "NY_FED", null, null);
  }

  private Map<String, Object> buildSuccessResponse(List<Boolean> mask, List<Double> errors, double threshold) {
    Map<String, Object> response = new HashMap<>();
    response.put("anomaly_mask", mask);
    response.put("reconstruction_errors", errors);
    response.put("threshold", threshold);
    response.put("status", "TRAINED");
    return response;
  }

  @Nested
  class ScoreIliAnomalies {

    @Test
    void givenSufficientRows_whenScoreIli_thenScoresWrittenBack() {
      List<IliHistory> rows = java.util.stream.Stream.generate(() -> buildIliHistory(NOW.minusSeconds((long)(Math.random() * 86400))))
          .limit(25).toList();
      when(iliRepository.findLatestN(100)).thenReturn(rows);
      when(modelArtifactService.findCachedModel(anyString(), anyString())).thenReturn(Optional.empty());

      List<Boolean> mask = rows.stream().map(r -> false).toList();
      List<Double> errors = rows.stream().map(r -> 0.05).toList();
      when(analyticsClient.sendAnalysisRequest(anyString(), any())).thenReturn(
          buildSuccessResponse(mask, errors, 0.5));

      service.scoreIliAnomalies();

      verify(iliRepository, times(rows.size())).updateAnomalyScore(any(Instant.class), anyDouble(), anyBoolean());
      verify(modelArtifactService).persistModel(
          eq("autoencoder"), eq("1.0"), anyString(), any(), anyString(), eq(25), anyString(), any());
    }

    @Test
    void givenTooFewRows_whenScoreIli_thenSkip() {
      List<IliHistory> rows = java.util.stream.Stream.generate(() -> buildIliHistory(NOW))
          .limit(10).toList();
      when(iliRepository.findLatestN(100)).thenReturn(rows);

      service.scoreIliAnomalies();

      verifyNoInteractions(analyticsClient);
      verify(iliRepository, never()).updateAnomalyScore(any(), anyDouble(), anyBoolean());
    }

    @Test
    void givenApiFailure_whenScoreIli_thenNoExceptionThrown() {
      List<IliHistory> rows = java.util.stream.Stream.generate(() -> buildIliHistory(NOW))
          .limit(25).toList();
      when(iliRepository.findLatestN(100)).thenReturn(rows);
      when(modelArtifactService.findCachedModel(anyString(), anyString())).thenReturn(Optional.empty());
      when(analyticsClient.sendAnalysisRequest(anyString(), any())).thenReturn(Map.of("error", "connection refused"));

      service.scoreIliAnomalies();

      verify(iliRepository, never()).updateAnomalyScore(any(), anyDouble(), anyBoolean());
    }

    @Test
    void givenCachedModel_whenScoreIli_thenUsesDetectEndpoint() {
      List<IliHistory> rows = java.util.stream.Stream.generate(() -> buildIliHistory(NOW))
          .limit(25).toList();
      when(iliRepository.findLatestN(100)).thenReturn(rows);

      ModelArtifact cachedModel = new ModelArtifact(
          1L, "autoencoder", "1.0", "{}", new byte[]{1, 2, 3}, "{\"threshold\":0.5}",
          Instant.now(), 100, "hash", null, true);
      when(modelArtifactService.findCachedModel(anyString(), anyString())).thenReturn(Optional.of(cachedModel));

      List<Boolean> mask = rows.stream().map(r -> false).toList();
      List<Double> errors = rows.stream().map(r -> 0.03).toList();
      when(analyticsClient.sendAnalysisRequest(eq("/api/v1/anomaly/detect"), any())).thenReturn(
          buildSuccessResponse(mask, errors, 0.5));

      service.scoreIliAnomalies();

      ArgumentCaptor<String> endpointCaptor = ArgumentCaptor.forClass(String.class);
      verify(analyticsClient).sendAnalysisRequest(endpointCaptor.capture(), any());
      assertEquals("/api/v1/anomaly/detect", endpointCaptor.getValue());
      verify(iliRepository, times(rows.size())).updateAnomalyScore(any(), anyDouble(), anyBoolean());
    }

    @Test
    void givenThresholdBelowMinimum_whenScoreIli_thenNoScoresWritten() {
      List<IliHistory> rows = java.util.stream.Stream.generate(() -> buildIliHistory(NOW))
          .limit(25).toList();
      when(iliRepository.findLatestN(100)).thenReturn(rows);
      when(modelArtifactService.findCachedModel(anyString(), anyString())).thenReturn(Optional.empty());

      List<Boolean> mask = rows.stream().map(r -> false).toList();
      List<Double> errors = rows.stream().map(r -> 0.05).toList();
      when(analyticsClient.sendAnalysisRequest(anyString(), any())).thenReturn(
          buildSuccessResponse(mask, errors, 1e-9));

      service.scoreIliAnomalies();

      verify(iliRepository, never()).updateAnomalyScore(any(), anyDouble(), anyBoolean());
    }
  }

  @Nested
  class ScoreRateAnomalies {

    @Test
    void givenSufficientFilteredRows_whenScoreRate_thenScoresWrittenBack() {
      List<RateSnapshot> rows = java.util.stream.Stream.generate(() -> buildRateSnapshot(
          NOW.minusSeconds((long)(Math.random() * 86400)), "SOFR"))
          .limit(25).toList();
      when(rateRepository.findLatestN(100)).thenReturn(rows);
      when(modelArtifactService.findCachedModel(anyString(), anyString())).thenReturn(Optional.empty());

      List<Boolean> mask = rows.stream().map(r -> false).toList();
      List<Double> errors = rows.stream().map(r -> 0.04).toList();
      when(analyticsClient.sendAnalysisRequest(anyString(), any())).thenReturn(
          buildSuccessResponse(mask, errors, 0.5));

      service.scoreRateAnomalies("SOFR");

      verify(rateRepository, times(rows.size())).updateAnomalyScore(any(Instant.class), eq("SOFR"), anyDouble(), anyBoolean());
    }

    @Test
    void givenTooFewFilteredRows_whenScoreRate_thenSkip() {
      List<RateSnapshot> rows = List.of(
          buildRateSnapshot(NOW, "SOFR"),
          buildRateSnapshot(NOW.minusSeconds(86400), "EFFR"));
      when(rateRepository.findLatestN(100)).thenReturn(rows);

      service.scoreRateAnomalies("SOFR");

      verifyNoInteractions(analyticsClient);
      verify(rateRepository, never()).updateAnomalyScore(any(), anyString(), anyDouble(), anyBoolean());
    }
  }
}
