package com.tickonomics.computation.model;

import com.tickonomics.persistence.entity.ModelArtifact;
import com.tickonomics.persistence.repository.ModelArtifactRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ModelArtifactServiceTest {

  @Mock
  private ModelArtifactRepository repository;

  private ModelArtifactService service;

  @BeforeEach
  void setUp() {
    service = new ModelArtifactService(repository);
  }

  private ModelArtifact buildArtifact(Long id, String modelType, String dataHash) {
    return new ModelArtifact(
        id, modelType, "1.0", "{\"p\":1}", null,
        "{\"loss\":0.01}", Instant.now(), 500, dataHash, null, true);
  }

  @Nested
  class FindCachedModel {

    @Test
    void givenMatchingDataHash_whenFindCachedModel_thenReturnPresent() {
      String trainingData = "[0.01, 0.02, 0.03]";
      String expectedHash = service.computeSha256(trainingData);
      when(repository.findByModelTypeAndDataHash("garch", expectedHash))
          .thenReturn(Optional.of(buildArtifact(1L, "garch", expectedHash)));

      Optional<ModelArtifact> result = service.findCachedModel("garch", trainingData);

      assertTrue(result.isPresent());
      assertEquals(expectedHash, result.get().dataHash());
      verify(repository).findByModelTypeAndDataHash("garch", expectedHash);
    }

    @Test
    void givenNoMatchingHash_whenFindCachedModel_thenReturnEmpty() {
      String trainingData = "[0.01, 0.02]";
      String expectedHash = service.computeSha256(trainingData);
      when(repository.findByModelTypeAndDataHash("garch", expectedHash))
          .thenReturn(Optional.empty());

      Optional<ModelArtifact> result = service.findCachedModel("garch", trainingData);

      assertTrue(result.isEmpty());
    }
  }

  @Nested
  class FindActiveModel {

    @Test
    void givenActiveModel_whenFindActive_thenReturnPresent() {
      when(repository.findActiveByModelType("autoencoder"))
          .thenReturn(Optional.of(buildArtifact(5L, "autoencoder", "hash5")));

      Optional<ModelArtifact> result = service.findActiveModel("autoencoder");

      assertTrue(result.isPresent());
      assertEquals(5L, result.get().id());
    }
  }

  @Nested
  class PersistModel {

    @Test
    void givenValidInputs_whenPersistModel_thenSaveAndPrune() {
      String trainingData = "[0.01, 0.02, 0.03]";
      when(repository.save(any(ModelArtifact.class))).thenReturn(42L);

      long id = service.persistModel(
          "garch", "1.0", "{\"p\":1,\"q\":1}", null,
          "{\"loss\":0.01}", 500, trainingData, "abc123");

      assertEquals(42L, id);
      verify(repository).save(any(ModelArtifact.class));
      verify(repository).deactivateOlderVersions("garch", 3);
    }

    @Test
    void givenNeuralNetModel_whenPersistModel_thenStateDataIncluded() {
      String trainingData = "[[0.1, 0.2], [0.3, 0.4]]";
      byte[] stateData = new byte[]{1, 2, 3, 4};
      when(repository.save(any(ModelArtifact.class))).thenReturn(10L);

      long id = service.persistModel(
          "autoencoder", "1.0", "{\"encoding_dim\":8}", stateData,
          "{\"epochs\":50,\"threshold\":0.05}", 1000, trainingData, null);

      assertEquals(10L, id);
      verify(repository).save(any(ModelArtifact.class));
      verify(repository).deactivateOlderVersions("autoencoder", 3);
    }
  }

  @Nested
  class InvalidateByTtl {

    @Test
    void givenOldModels_whenInvalidateByTtl_thenDeactivateCalled() {
      service.invalidateByTtl("garch", "24 hours");

      verify(repository).deactivateByTtl("garch", "24 hours");
    }
  }

  @Nested
  class ComputeSha256 {

    @Test
    void givenSameInput_whenComputeSha256Twice_thenSameHash() {
      String data = "test-data-for-hashing";

      String hash1 = service.computeSha256(data);
      String hash2 = service.computeSha256(data);

      assertEquals(hash1, hash2);
    }

    @Test
    void givenDifferentInputs_whenComputeSha256_thenDifferentHashes() {
      String hash1 = service.computeSha256("data-A");
      String hash2 = service.computeSha256("data-B");

      assertFalse(hash1.equals(hash2));
    }

    @Test
    void givenAnyInput_whenComputeSha256_then64CharHex() {
      String hash = service.computeSha256("any-input");

      assertNotNull(hash);
      assertEquals(64, hash.length());
      assertTrue(hash.matches("[0-9a-f]+"));
    }
  }
}
