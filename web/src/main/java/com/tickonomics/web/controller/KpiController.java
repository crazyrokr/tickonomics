package com.tickonomics.web.controller;

import com.tickonomics.computation.kpi.CorrelationEngine;
import com.tickonomics.computation.kpi.KpiProcessor;
import com.tickonomics.computation.kpi.KpiResult;
import com.tickonomics.computation.kpi.RegimeDetector;
import com.tickonomics.computation.kpi.RegimeResult;
import com.tickonomics.persistence.repository.IliHistoryRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/kpi")
public class KpiController {

    private final KpiProcessor kpiProcessor;
    private final IliHistoryRepository iliHistoryRepository;
    private final RegimeDetector regimeDetector;
    private final ObjectProvider<CorrelationEngine> correlationEngine;

    public KpiController(KpiProcessor kpiProcessor,
                         IliHistoryRepository iliHistoryRepository,
                         RegimeDetector regimeDetector,
                         ObjectProvider<CorrelationEngine> correlationEngine) {
        this.kpiProcessor = kpiProcessor;
        this.iliHistoryRepository = iliHistoryRepository;
        this.regimeDetector = regimeDetector;
        this.correlationEngine = correlationEngine;
    }

    // ── ILI snapshot ────────────────────────────────────────────────────────

    @GetMapping("/ili")
    public ResponseEntity<?> getIli() {
        var latest = iliHistoryRepository.findLatest();
        if (latest == null) {
            return ResponseEntity.ok(new IliValueResponse(
                    Instant.now(), 0.0, "INSUFFICIENT_DATA",
                    Map.of(), List.of()));
        }
        double[] weights = parseWeights(latest.activeWeights());
        return ResponseEntity.ok(new IliValueResponse(
                latest.time(), latest.iliValue(), latest.dataStatus(),
                Map.of(
                        "RRP", weights.length > 0 ? weights[0] : 0.0,
                        "SPREAD", weights.length > 1 ? weights[1] : 0.0,
                        "VOL", weights.length > 2 ? weights[2] : 0.0),
                latest.proxyDivergenceStatus() != null ? List.of("PROXY_DIVERGENT") : List.of()));
    }

    // ── ILI history ─────────────────────────────────────────────────────────

    @GetMapping("/ili/history")
    public ResponseEntity<List<IliHistoryPointResponse>> getIliHistory(
            @RequestParam(defaultValue = "100") int limit) {
        var entries = iliHistoryRepository.findLatestN(Math.min(limit, 500));
        var points = entries.stream()
                .map(e -> new IliHistoryPointResponse(
                        e.time(), e.iliValue(), e.dataStatus(), e.anomalyScore()))
                .toList();
        return ResponseEntity.ok(points);
    }

    // ── Liquidity Stress Index ──────────────────────────────────────────────

    @GetMapping("/liquidity-stress")
    public ResponseEntity<LiquidityStressResponse> getLiquidityStress() {
        var result = kpiProcessor.computeLiquidityStressIndex();
        String trend = switch (result.status()) {
            case "STRESSED" -> "ACCELERATING";
            case "ELEVATED" -> "DECELERATING";
            default -> "STABLE";
        };
        return ResponseEntity.ok(new LiquidityStressResponse(
                Instant.now(),
                Double.isNaN(result.value()) ? 0.0 : result.value(),
                trend));
    }

    // ── Repo/Equity Beta ────────────────────────────────────────────────────

    @GetMapping("/repo-equity-beta")
    public ResponseEntity<List<RepoEquityBetaResponse>> getRepoEquityBeta(
            @RequestParam(required = false) String symbol) {
        var engine = correlationEngine.getIfAvailable();
        if (engine == null) {
            return ResponseEntity.ok(List.of());
        }
        var result = kpiProcessor.computeRepoEquityBeta(engine);
        if (result.status().equals(KpiResult.STATUS_UNKNOWN)) {
            return ResponseEntity.ok(List.of());
        }
        String sym = symbol != null ? symbol : "SPY";
        return ResponseEntity.ok(List.of(new RepoEquityBetaResponse(
                sym, result.value(), Instant.now())));
    }

    // ── RRP Drain Velocity ──────────────────────────────────────────────────

    @GetMapping("/rrp-drain")
    public ResponseEntity<RrpDrainResponse> getRrpDrain() {
        var result = kpiProcessor.computeRrpDrainVelocity();
        String trend = switch (result.status()) {
            case "STRESSED" -> "ACCELERATING";
            case "ELEVATED" -> "DECELERATING";
            default -> "STABLE";
        };
        return ResponseEntity.ok(new RrpDrainResponse(
                Instant.now(),
                Double.isNaN(result.value()) ? 0.0 : result.value(),
                0.0,
                trend,
                List.of()));
    }

    // ── Volatility Regime ───────────────────────────────────────────────────

    @GetMapping("/volatility-regime")
    public ResponseEntity<VolatilityRegimeResponse> getVolatilityRegime() {
        var result = kpiProcessor.computeVolatilityRegime();
        String regime = switch (result.status()) {
            case "LOW_VOL" -> "LOW_VOL";
            case "HIGH_VOL" -> "HIGH_VOL";
            case "EXTREME" -> "HIGH_VOL";
            default -> "NORMAL";
        };
        double vol = Double.isNaN(result.value()) ? 0.0 : result.value();
        return ResponseEntity.ok(new VolatilityRegimeResponse(
                Instant.now(), regime, vol * 1.5, vol * 0.5, vol));
    }

    // ── Systemic Risk Heatmap ───────────────────────────────────────────────

    @GetMapping("/systemic-risk-heatmap")
    public ResponseEntity<SystemicRiskHeatmapResponse> getSystemicRiskHeatmap() {
        var result = kpiProcessor.computeSystemicRiskHeatmap();
        return ResponseEntity.ok(new SystemicRiskHeatmapResponse(
                Instant.now(), result.value(), result.value(), result.value(), result.value()));
    }

    // ── Correlation Matrix (not yet implemented at backend) ─────────────────

    @GetMapping("/correlation-matrix")
    public ResponseEntity<?> getCorrelationMatrix() {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
                .body(Map.of("title", "Not Implemented",
                        "detail", "Correlation matrix endpoint is not yet implemented. "
                                + "Use /api/v1/kpi/repo-equity-beta for pairwise beta.",
                        "status", 501));
    }

    // ── EVT Risk (not yet implemented) ──────────────────────────────────────

    @GetMapping("/evt-risk")
    public ResponseEntity<?> getEvtRisk() {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
                .body(Map.of("title", "Not Implemented",
                        "detail", "EVT risk endpoint is not yet implemented.",
                        "status", 501));
    }

    // ── Efficiency Gap (not yet implemented) ────────────────────────────────

    @GetMapping("/efficiency-gap")
    public ResponseEntity<?> getEfficiencyGap() {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
                .body(Map.of("title", "Not Implemented",
                        "detail", "Efficiency gap endpoint is not yet implemented.",
                        "status", 501));
    }

    // ── Monetary Policy (not yet implemented) ───────────────────────────────

    @GetMapping("/monetary-policy")
    public ResponseEntity<?> getMonetaryPolicy() {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
                .body(Map.of("title", "Not Implemented",
                        "detail", "Monetary policy endpoint is not yet implemented.",
                        "status", 501));
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private static double[] parseWeights(String activeWeights) {
        if (activeWeights == null || activeWeights.isBlank()) {
            return new double[0];
        }
        try {
            var cleaned = activeWeights.replaceAll("[\\[\\]]", "");
            var parts = cleaned.split(",");
            double[] result = new double[parts.length];
            for (int i = 0; i < parts.length; i++) {
                result[i] = Double.parseDouble(parts[i].trim());
            }
            return result;
        } catch (Exception e) {
            return new double[0];
        }
    }

    // ── Response DTOs ───────────────────────────────────────────────────────

    record IliValueResponse(Instant timestamp, double value, String status,
                            Map<String, Double> activeWeights,
                            List<String> excludedComponents) {}

    record IliHistoryPointResponse(Instant timestamp, double value,
                                   String status, Double anomalyScore) {}

    record LiquidityStressResponse(Instant timestamp, double value, String trend) {}

    record RepoEquityBetaResponse(String symbol, double beta, Instant lastUpdate) {}

    record RrpDrainResponse(Instant timestamp, double velocity,
                            double dayOverDayChange, String trend,
                            List<Map<String, Object>> history) {}

    record VolatilityRegimeResponse(Instant timestamp, String regime,
                                    double upperBand, double lowerBand,
                                    double currentPrice) {}

    record SystemicRiskHeatmapResponse(Instant timestamp,
                                       double triPartyGcfSpread,
                                       double sofrPctlRange,
                                       double tgcrBgcrSpread,
                                       double tgaBalanceChange) {}
}
