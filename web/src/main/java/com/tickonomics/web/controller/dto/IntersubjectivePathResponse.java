package com.tickonomics.web.controller.dto;

import java.util.List;
import java.util.UUID;

/** Typed {@code /api/v1/quant/audit/intersubjective-reproducibility/{id}} response; mirrors the
 * {@code IntersubjectivePath} contract schema. */
public record IntersubjectivePathResponse(
    UUID signalId,
    List<PathEntry> path,
    double compositeIrScore) {

  public record PathEntry(
      String ruleName,
      String ruleVersion,
      String inputHash,
      double outputValue,
      double irScore) {
  }
}
