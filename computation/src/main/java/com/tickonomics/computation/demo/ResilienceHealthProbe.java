package com.tickonomics.computation.demo;

/**
 * Supplies the live cross-module health reading that {@link SystemicResilienceMonitor} evaluates.
 *
 * <p>Implemented in the {@code app} module ({@code CrossModuleResilienceHealthProbe}) so it can
 * read ingestion-buffer depth, analytics-worker health, and proxy-divergence state together;
 * the decision logic itself stays in {@code computation} where the consumers live.
 */
public interface ResilienceHealthProbe {

  ResilienceHealthSnapshot snapshot();
}
