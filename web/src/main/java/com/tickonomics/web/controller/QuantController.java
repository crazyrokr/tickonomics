package com.tickonomics.web.controller;

import com.tickonomics.computation.audit.AuditEntry;
import com.tickonomics.computation.audit.IntersubjectiveAuditService;
import com.tickonomics.computation.strategy.AlphaSignal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/quant")
public class QuantController {

  private final IntersubjectiveAuditService auditService;

  public QuantController(IntersubjectiveAuditService auditService) {
    this.auditService = auditService;
  }

  @GetMapping("/signals/active")
  public ResponseEntity<List<AlphaSignal>> getActiveSignals(
      @RequestParam(required = false) String category) {
    return ResponseEntity.ok(List.of());
  }

  @GetMapping("/strategies/active")
  public ResponseEntity<List<Map<String, Object>>> getActiveStrategies() {
    return ResponseEntity.ok(List.of());
  }

  @PostMapping("/strategies/options/butterfly")
  public ResponseEntity<AlphaSignal> computeButterflySignal(
      @RequestParam String underlying) {
    return ResponseEntity.ok(AlphaSignal.neutral(UUID.randomUUID(), underlying));
  }

  @GetMapping("/risk/tail-parameters")
  public ResponseEntity<Map<String, Object>> getTailParameters() {
    return ResponseEntity.ok(Map.of());
  }

  @GetMapping("/risk/evt-tail")
  public ResponseEntity<Map<String, Object>> getEvtTail() {
    return ResponseEntity.ok(Map.of());
  }

  @GetMapping("/audit/intersubjective-reproducibility/{id}")
  public ResponseEntity<Map<String, Object>> getAuditPath(@PathVariable UUID id) {
    List<AuditEntry> path = auditService.reconstructPath(id);
    double irScore = auditService.computeCompositeIrScore(id);

    List<Map<String, Object>> pathEntries = path
        .stream()
        .map(e -> Map.<String, Object>of(
            "ruleName",
            e.codingRule(),
            "ruleVersion",
            e.ruleVersion(),
            "inputHash",
            e.inputHash(),
            "outputValue",
            e.outputValue(),
            "irScore",
            e.irScore()))
        .toList();

    return ResponseEntity.ok(Map.of("signalId", id.toString(), "path", pathEntries, "compositeIrScore", irScore));
  }

  @GetMapping("/macro/shock-response")
  public ResponseEntity<Map<String, Object>> getShockResponse() {
    return ResponseEntity.ok(Map.of());
  }
}
