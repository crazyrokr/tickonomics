package com.tickonomics.computation.demo;

import com.tickonomics.persistence.entity.VirtualPortfolioPosition;
import com.tickonomics.persistence.entity.VirtualPortfolioTrade;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.stereotype.Service;

/**
 * Big Red Button: when active, {@link PaperTradingEngine} blocks every new trade and all open
 * positions are liquidated at the supplied market prices until explicitly re-armed via
 * {@link #deactivate()}.
 *
 * <p>State is in-memory and volatile: a process restart returns the switch to the inactive
 * default. Demo trading is opt-in ({@code monitor.demo.enabled=false}), so this is an acceptable
 * operational trade-off — documented in ADR-017.
 */
@Service
public class KillSwitch {

  private final AtomicBoolean active = new AtomicBoolean(false);

  public boolean isActive() {
    return active.get();
  }

  public void activate() {
    active.set(true);
  }

  public void deactivate() {
    active.set(false);
  }

  /**
   * Close every open position at its current market price. Idempotent: positions without a live
   * price are skipped rather than liquidated at a stale value.
   */
  public List<VirtualPortfolioTrade> liquidateAll(VirtualPortfolio portfolio,
      Map<String, Double> currentPrices) {
    if (portfolio == null || currentPrices == null) {
      return List.of();
    }
    List<VirtualPortfolioPosition> openPositions = portfolio.findOpenPositions();
    return openPositions.stream()
        .filter(pos -> currentPrices.containsKey(pos.symbol()))
        .map(pos -> portfolio.closePosition(pos.id(), currentPrices.get(pos.symbol())))
        .toList();
  }
}
