package com.tickonomics.persistence.repository;

import com.tickonomics.persistence.entity.ModelArtifact;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ModelArtifactRepositoryTest {

  @Mock
  private NamedParameterJdbcTemplate jdbc;

  private ModelArtifactRepository repository;

  private static final Instant NOW = Instant.now();

  @BeforeEach
  void setUp() {
    repository = new ModelArtifactRepository(jdbc);
  }

  private ModelArtifact buildArtifact(Long id) {
    return new ModelArtifact(
        id, "garch", "1.0", "{\"p\":1,\"q\":1}", null,
        "{\"loss\":0.01}", NOW, 500, "abc123", null, true);
  }

  @Nested
  class Save {

    @Test
    @SuppressWarnings("unchecked")
    void givenArtifact_whenSave_thenJdbcUpdateCalledWithKeyHolder() {
      when(jdbc.update(anyString(), any(MapSqlParameterSource.class),
          any(GeneratedKeyHolder.class), any(String[].class)))
          .thenAnswer(invocation -> {
            GeneratedKeyHolder kh = invocation.getArgument(2);
            kh.getKeyList().add(Map.of("id", 1L));
            return 1;
          });

      long id = repository.save(buildArtifact(null));

      assertEquals(1L, id);
      verify(jdbc).update(anyString(), any(MapSqlParameterSource.class),
          any(GeneratedKeyHolder.class), any(String[].class));
    }
  }

  @Nested
  class FindActiveByModelType {

    @Test
    @SuppressWarnings("unchecked")
    void givenExistingActiveModel_whenFindByType_thenReturnPresent() {
      when(jdbc.query(anyString(), any(Map.class), any(RowMapper.class)))
          .thenReturn(List.of(buildArtifact(1L)));

      Optional<ModelArtifact> result = repository.findActiveByModelType("garch");

      assertTrue(result.isPresent());
      assertEquals(1L, result.get().id());
    }

    @Test
    @SuppressWarnings("unchecked")
    void givenNoActiveModel_whenFindByType_thenReturnEmpty() {
      when(jdbc.query(anyString(), any(Map.class), any(RowMapper.class)))
          .thenReturn(List.of());

      Optional<ModelArtifact> result = repository.findActiveByModelType("garch");

      assertTrue(result.isEmpty());
    }
  }

  @Nested
  class FindByModelTypeAndDataHash {

    @Test
    @SuppressWarnings("unchecked")
    void givenMatchingHash_whenFindByTypeAndHash_thenReturnPresent() {
      when(jdbc.query(anyString(), any(Map.class), any(RowMapper.class)))
          .thenReturn(List.of(buildArtifact(1L)));

      Optional<ModelArtifact> result = repository.findByModelTypeAndDataHash("garch", "abc123");

      assertTrue(result.isPresent());
      assertEquals("abc123", result.get().dataHash());
    }

    @Test
    @SuppressWarnings("unchecked")
    void givenNoMatchingHash_whenFindByTypeAndHash_thenReturnEmpty() {
      when(jdbc.query(anyString(), any(Map.class), any(RowMapper.class)))
          .thenReturn(List.of());

      Optional<ModelArtifact> result = repository.findByModelTypeAndDataHash("garch", "nonexistent");

      assertTrue(result.isEmpty());
    }
  }

  @Nested
  class DeactivateOlderVersions {

    @Test
    void givenMultipleVersions_whenDeactivate_thenUpdateCalled() {
      when(jdbc.update(anyString(), any(Map.class))).thenReturn(2);

      repository.deactivateOlderVersions("garch", 3);

      verify(jdbc).update(anyString(), any(Map.class));
    }
  }

  @Nested
  class DeactivateByTtl {

    @Test
    void givenOldModels_whenDeactivateByTtl_thenUpdateCalled() {
      when(jdbc.update(anyString(), any(Map.class))).thenReturn(1);

      repository.deactivateByTtl("garch", "24 hours");

      verify(jdbc).update(anyString(), any(Map.class));
    }
  }
}
