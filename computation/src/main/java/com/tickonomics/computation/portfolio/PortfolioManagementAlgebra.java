package com.tickonomics.computation.portfolio;

import java.math.BigDecimal;
import java.math.MathContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class PortfolioManagementAlgebra {

    private static final Logger log = LoggerFactory.getLogger(PortfolioManagementAlgebra.class);
    private static final BigDecimal BPS_DIVISOR = new BigDecimal("10000");

    public record CostModel(BigDecimal t0Rate, BigDecimal t1Rate,
                            BigDecimal spreadCost, BigDecimal totalCost) {
    }

    public record MarginRequirement(BigDecimal grossMargin, BigDecimal netMargin,
                                    BigDecimal maintenanceMargin, BigDecimal equityRequired) {
    }

    public CostModel computeCosts(long size, BigDecimal price, BigDecimal t0Rate,
                                   BigDecimal t1Rate, BigDecimal spreadBps) {
        BigDecimal notional = BigDecimal.valueOf(size).multiply(price);
        BigDecimal t0Cost = notional.multiply(t0Rate);
        BigDecimal t1Cost = notional.multiply(t1Rate);
        BigDecimal spreadCost = notional.multiply(spreadBps).divide(BPS_DIVISOR, MathContext.DECIMAL64);
        BigDecimal totalCost = t0Cost.add(t1Cost).add(spreadCost);

        log.debug("Costs: notional={}, t0={}, t1={}, spread={}, total={}",
                notional, t0Cost, t1Cost, spreadCost, totalCost);

        return new CostModel(t0Rate, t1Rate, spreadCost, totalCost);
    }

    public MarginRequirement computeMargin(long size, BigDecimal price,
                                           double initialPct, double maintenancePct) {
        BigDecimal notional = BigDecimal.valueOf(size).multiply(price);
        BigDecimal initialFactor = BigDecimal.valueOf(initialPct);
        BigDecimal maintenanceFactor = BigDecimal.valueOf(maintenancePct);
        BigDecimal grossMargin = notional.multiply(initialFactor);
        BigDecimal maintenanceMargin = notional.multiply(maintenanceFactor);
        BigDecimal netMargin = grossMargin.subtract(maintenanceMargin);
        BigDecimal equityRequired = grossMargin;

        log.debug("Margin: notional={}, gross={}, net={}, maintenance={}",
                notional, grossMargin, netMargin, maintenanceMargin);

        return new MarginRequirement(grossMargin, netMargin, maintenanceMargin, equityRequired);
    }
}