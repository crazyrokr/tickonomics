package com.tickonomics.computation.demo;

import com.tickonomics.computation.kpi.SignalResult;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;

/**
 * Passive execution: place a limit order at the ILI fair price to capture spread. In the simulation
 * this fills at the fair price with zero adverse slippage, representing the best-case cost outcome
 * for the dual-portfolio comparison.
 */
@Component
public class PassiveExecutionHandler {

  public FillEstimate fill(SignalResult signal, double fairPrice) {
    return new FillEstimate("PASSIVE", BigDecimal.valueOf(fairPrice), 0.0, true);
  }
}
