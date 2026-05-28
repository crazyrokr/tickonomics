package com.tickonomics.computation.sensitivity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class MarketSensitivityLibrary {

    private static final Logger log = LoggerFactory.getLogger(MarketSensitivityLibrary.class);

    public record SensitivityGreeks(
            double repoDelta,
            double rateDelta,
            double rateGamma,
            double spreadDelta,
            double volga,
            double dv01,
            double convexity) {}

    public MarketSensitivityLibrary() {}

    public SensitivityGreeks fromKpis(
            double rrpZscore,
            double spreadZscore,
            double volZscore,
            double correlation) {

        double repoDelta = rrpZscore;
        double rateDelta = rrpZscore * correlation;
        double rateGamma = rrpZscore * rrpZscore * 0.5;
        double spreadDelta = spreadZscore;
        double volga = volZscore * volZscore * correlation;
        double dv01 = repoDelta * 0.0001;
        double convexity = rateGamma * 0.5;

        SensitivityGreeks greeks = new SensitivityGreeks(
                repoDelta, rateDelta, rateGamma, spreadDelta, volga, dv01, convexity);

        log.info("Sensitivity Greeks computed: repoDelta={:.4f}, rateDelta={:.4f}, "
                        + "rateGamma={:.4f}, spreadDelta={:.4f}, volga={:.4f}",
                repoDelta, rateDelta, rateGamma, spreadDelta, volga);

        return greeks;
    }

    public String describe(SensitivityGreeks greeks) {
        if (greeks == null) {
            return "No Greeks available";
        }

        return String.format(
                "Market Sensitivity Greeks:%n"
                        + "  Repo Delta (dV/dRRP):     %+.6f%n"
                        + "  Rate Delta (dV/dRate):    %+.6f%n"
                        + "  Rate Gamma (d2V/dRate2):  %+.6f%n"
                        + "  Spread Delta (dV/dSpread): %+.6f%n"
                        + "  Volga (d2V/dVol2):        %+.6f%n"
                        + "  DV01:                     %+.8f%n"
                        + "  Convexity:                %+.6f",
                greeks.repoDelta(),
                greeks.rateDelta(),
                greeks.rateGamma(),
                greeks.spreadDelta(),
                greeks.volga(),
                greeks.dv01(),
                greeks.convexity());
    }
}
