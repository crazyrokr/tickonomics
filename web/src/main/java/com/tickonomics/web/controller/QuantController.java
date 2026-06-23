package com.tickonomics.web.controller;

import com.tickonomics.computation.audit.IntersubjectiveAuditService;
import com.tickonomics.web.controller.dto.IntersubjectivePathResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
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

  /**
   * Several quant endpoints are declared in the contract (openapi.yaml) but not yet backed by
   * wired services. They return HTTP 501 rather than a misleading 200 with empty data, so callers
   * can distinguish "no data" from "not implemented".
   */
  private static ResponseEntity<ProblemDetail> notImplemented(String operation) {
    var problem = ProblemDetail.forStatusAndDetail(
        HttpStatus.NOT_IMPLEMENTED,
        operation + " is declared in the API contract but not yet implemented.");
    problem.setTitle("Not implemented");
    return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).body(problem);
  }

  @GetMapping("/signals/active")
  public ResponseEntity<ProblemDetail> getActiveSignals(
      @RequestParam(required = false) String category) {
    return notImplemented("GET /api/v1/quant/signals/active");
  }

  @GetMapping("/strategies/active")
  public ResponseEntity<ProblemDetail> getActiveStrategies() {
    return notImplemented("GET /api/v1/quant/strategies/active");
  }

  @PostMapping("/strategies/options/butterfly")
  public ResponseEntity<ProblemDetail> computeButterflySignal(
      @RequestParam String underlying) {
    return notImplemented("POST /api/v1/quant/strategies/options/butterfly");
  }

  @GetMapping("/risk/tail-parameters")
  public ResponseEntity<ProblemDetail> getTailParameters() {
    return notImplemented("GET /api/v1/quant/risk/tail-parameters");
  }

  @GetMapping("/risk/evt-tail")
  public ResponseEntity<ProblemDetail> getEvtTail() {
    return notImplemented("GET /api/v1/quant/risk/evt-tail");
  }

  @GetMapping("/audit/intersubjective-reproducibility/{id}")
  public ResponseEntity<IntersubjectivePathResponse> getAuditPath(@PathVariable UUID id) {
    double irScore = auditService.computeCompositeIrScore(id);
    List<IntersubjectivePathResponse.PathEntry> path = auditService.reconstructPath(id).stream()
        .map(e -> new IntersubjectivePathResponse.PathEntry(
            e.codingRule(),
            e.ruleVersion(),
            e.inputHash(),
            e.outputValue(),
            e.irScore()))
        .toList();
    return ResponseEntity.ok(new IntersubjectivePathResponse(id, path, irScore));
  }

  @GetMapping("/macro/shock-response")
  public ResponseEntity<ProblemDetail> getShockResponse() {
    return notImplemented("GET /api/v1/quant/macro/shock-response");
  }
}
