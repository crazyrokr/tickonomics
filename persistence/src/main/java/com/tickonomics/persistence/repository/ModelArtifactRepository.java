package com.tickonomics.persistence.repository;

import com.tickonomics.persistence.entity.ModelArtifact;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class ModelArtifactRepository {

  private final NamedParameterJdbcTemplate jdbc;

  private final RowMapper<ModelArtifact> rowMapper = (rs, rowNum) -> new ModelArtifact(
      rs.getLong("id"),
      rs.getString("model_type"),
      rs.getString("model_version"),
      rs.getString("parameters"),
      rs.getBytes("state_data"),
      rs.getString("training_stats"),
      rs.getTimestamp("trained_at").toInstant(),
      rs.getObject("trained_rows") != null ? rs.getInt("trained_rows") : null,
      rs.getString("data_hash"),
      rs.getString("git_sha"),
      rs.getBoolean("is_active"));

  public ModelArtifactRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public long save(ModelArtifact artifact) {
    var keyHolder = new GeneratedKeyHolder();
    jdbc.update(
        "INSERT INTO model_artifacts (model_type, model_version, parameters, state_data, "
            + "training_stats, trained_at, trained_rows, data_hash, git_sha, is_active) "
            + "VALUES (:modelType, :modelVersion, :parameters::jsonb, :stateData, "
            + ":trainingStats::jsonb, :trainedAt, :trainedRows, :dataHash, :gitSha, :isActive)",
        toParams(artifact),
        keyHolder,
        new String[]{"id"});
    return keyHolder.getKey().longValue();
  }

  public Optional<ModelArtifact> findActiveByModelType(String modelType) {
    List<ModelArtifact> results = jdbc.query(
        "SELECT id, model_type, model_version, parameters, state_data, training_stats, "
            + "trained_at, trained_rows, data_hash, git_sha, is_active "
            + "FROM model_artifacts "
            + "WHERE model_type = :modelType AND is_active = TRUE "
            + "ORDER BY trained_at DESC LIMIT 1",
        Map.of("modelType", modelType),
        rowMapper);
    return results.isEmpty() ? Optional.empty() : Optional.of(results.getFirst());
  }

  public Optional<ModelArtifact> findByModelTypeAndDataHash(String modelType, String dataHash) {
    List<ModelArtifact> results = jdbc.query(
        "SELECT id, model_type, model_version, parameters, state_data, training_stats, "
            + "trained_at, trained_rows, data_hash, git_sha, is_active "
            + "FROM model_artifacts "
            + "WHERE model_type = :modelType AND data_hash = :dataHash AND is_active = TRUE "
            + "ORDER BY trained_at DESC LIMIT 1",
        Map.of("modelType", modelType, "dataHash", dataHash),
        rowMapper);
    return results.isEmpty() ? Optional.empty() : Optional.of(results.getFirst());
  }

  public void deactivateOlderVersions(String modelType, int keepLatestN) {
    jdbc.update(
        "UPDATE model_artifacts SET is_active = FALSE "
            + "WHERE model_type = :modelType AND is_active = TRUE AND id NOT IN ("
            + "  SELECT id FROM model_artifacts "
            + "  WHERE model_type = :modelType AND is_active = TRUE "
            + "  ORDER BY trained_at DESC LIMIT :keepN"
            + ")",
        Map.of("modelType", modelType, "keepN", keepLatestN));
  }

  public void deactivateByTtl(String modelType, String ttlInterval) {
    jdbc.update(
        "UPDATE model_artifacts SET is_active = FALSE "
            + "WHERE model_type = :modelType AND is_active = TRUE "
            + "AND trained_at < now() - :ttlInterval::interval",
        Map.of("modelType", modelType, "ttlInterval", ttlInterval));
  }

  private MapSqlParameterSource toParams(ModelArtifact artifact) {
    return new MapSqlParameterSource()
        .addValue("modelType", artifact.modelType())
        .addValue("modelVersion", artifact.modelVersion())
        .addValue("parameters", artifact.parameters())
        .addValue("stateData", artifact.stateData())
        .addValue("trainingStats", artifact.trainingStats())
        .addValue("trainedAt", artifact.trainedAt())
        .addValue("trainedRows", artifact.trainedRows())
        .addValue("dataHash", artifact.dataHash())
        .addValue("gitSha", artifact.gitSha())
        .addValue("isActive", artifact.isActive());
  }
}
