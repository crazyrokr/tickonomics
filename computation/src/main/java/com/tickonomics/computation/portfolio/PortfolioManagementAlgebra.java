package com.tickonomics.computation.portfolio;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class PortfolioManagementAlgebra {

    private static final Logger log = LoggerFactory.getLogger(PortfolioManagementAlgebra.class);

    public record CostModel(double t0Rate, double t1Rate, double spreadCost, double totalCost) {
    }

    public record MarginRequirement(double grossMargin, double netMargin,
                                    double maintenanceMargin, double equityRequired) {
    }

    public CostModel computeCosts(long size, double price, double t0Rate, double t1Rate, double spreadBps) {
        double notional = size * price;
        double t0Cost = notional * t0Rate;
        double t1Cost = notional * t1Rate;
        double spreadCost = notional * spreadBps / 10000.0;
        double totalCost = (t0Rate + t1Rate) * notional + spreadCost;

        log.debug("Costs: notional={:.2f}, t0={:.2f}, t1={:.2f}, spread={:.2f}, total={:.2f}",
                notional, t0Cost, t1Cost, spreadCost, totalCost);

        return new CostModel(t0Rate, t1Rate, spreadCost, totalCost);
    }

    public MarginRequirement computeMargin(long size, double price,
                                           double initialPct, double maintenancePct) {
        double notional = size * price;
        double grossMargin = notional * initialPct;
        double maintenanceMargin = notional * maintenancePct;
        double netMargin = grossMargin - maintenanceMargin;
        double equityRequired = grossMargin;

        log.debug("Margin: notional={:.2f}, gross={:.2f}, net={:.2f}, maintenance={:.2f}",
                notional, grossMargin, netMargin, maintenanceMargin);

        return new MarginRequirement(grossMargin, netMargin, maintenanceMargin, equityRequired);
    }
}
