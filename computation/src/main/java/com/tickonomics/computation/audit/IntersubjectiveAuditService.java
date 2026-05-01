package com.tickonomics.computation.audit;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class IntersubjectiveAuditService {

  static final double IR_SCORE_THRESHOLD = 0.90;

  private final ConcurrentHashMap<UUID, List<AuditEntry>> auditStore = new ConcurrentHashMap<>();

  public void logTransformation(UUID dataPointId, CodingRule rule, String inputPayload, double outputValue) {
    String inputHash = sha256(inputPayload);
    AuditEntry entry = new AuditEntry(
        Instant.now(),
        dataPointId,
        rule.ruleId(),
        rule.version(),
        inputHash,
        outputValue,
        rule.defaultIrScore());
    auditStore
        .computeIfAbsent(dataPointId, k -> Collections.synchronizedList(new ArrayList<>()))
        .add(entry);
  }

  public List<AuditEntry> reconstructPath(UUID dataPointId) {
    List<AuditEntry> entries = auditStore.get(dataPointId);
    if (entries == null) {
      return List.of();
    }
    return List.copyOf(entries);
  }

  public double computeCompositeIrScore(UUID dataPointId) {
    List<AuditEntry> entries = auditStore.get(dataPointId);
    if (entries == null || entries.isEmpty()) {
      return 0.0;
    }
    return entries
        .stream()
        .mapToDouble(AuditEntry::irScore)
        .min()
        .orElse(0.0);
  }

  public boolean isActionable(UUID dataPointId) {
    return computeCompositeIrScore(dataPointId) >= IR_SCORE_THRESHOLD;
  }

  public void clear() {
    auditStore.clear();
  }

  static String sha256(String input) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
      return HexFormat
          .of()
          .formatHex(hash);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 not available", e);
    }
  }
}
