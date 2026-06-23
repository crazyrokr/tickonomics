package com.tickonomics.computation.liquidity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class PhantomLiquidityService {

    private static final Logger log = LoggerFactory.getLogger(PhantomLiquidityService.class);
    private static final double PLI_DISCOUNT_FACTOR = 0.5;

    public double computePli(double canceledVolume, double totalVolumeAtBest) {
        if (totalVolumeAtBest <= 0.0) {
            log.warn("PLI undefined for non-positive totalVolumeAtBest={}; returning NaN sentinel",
                    totalVolumeAtBest);
            return Double.NaN;
        }
        double pli = canceledVolume / totalVolumeAtBest;
        log.debug("PLI computed: canceledVolume={}, totalVolumeAtBest={}, pli={}",
                canceledVolume, totalVolumeAtBest, pli);
        return pli;
    }

    public double computeReliabilityScore(double pli) {
        return 1.0 - pli;
    }

    public double discountIli(double ili, double pli) {
        double discounted = ili * (1.0 - pli * PLI_DISCOUNT_FACTOR);
        log.debug("ILI discount: original={}, pli={}, discounted={}", ili, pli, discounted);
        return discounted;
    }

    public PhantomLiquidityResult compute(double canceledVolume, double totalVolumeAtBest, double ili) {
        double pli = computePli(canceledVolume, totalVolumeAtBest);
        double reliabilityScore = computeReliabilityScore(pli);
        double discountedIli = discountIli(ili, pli);

        return new PhantomLiquidityResult(pli, reliabilityScore, discountedIli);
    }

    public record PhantomLiquidityResult(double pli, double reliabilityScore, double discountedIli) {
    }
}
