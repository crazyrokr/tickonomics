package com.tickonomics.computation.options;

import com.tickonomics.cdm.enums.DayCountConvention;
import com.tickonomics.cdm.enums.OptionType;
import com.tickonomics.cdm.model.CdmOptionSnapshot;
import com.tickonomics.computation.strategy.AlphaSignal;
import com.tickonomics.computation.strategy.StrategyContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class LegMatchServiceTest {

    private static final Instant NOW = Instant.now();
    private static final LocalDate EXPIRY = LocalDate.of(2026, 6, 20);

    private CdmOptionSnapshot option(double strike, OptionType type) {
        return new CdmOptionSnapshot(
            UUID.randomUUID(), "SPY", BigDecimal.valueOf(strike), EXPIRY, type,
            DayCountConvention.ACT_365_FIXED, 0.5, 0.1, -0.02, 0.15, 0.01,
            0.25, 0.5, 1.0, 2.0, 100, NOW
        );
    }

    @Test
    @DisplayName("Given strikes [95, 100, 110], when findButterflySpreads, then result is EMPTY (asymmetry)")
    void butterflyFailsOnAsymmetricStrikes() {
        List<CdmOptionSnapshot> chain = List.of(
            option(95.0, OptionType.CALL),
            option(100.0, OptionType.CALL),
            option(110.0, OptionType.CALL)
        );

        LegMatchService service = new LegMatchService();
        List<LegGroup> result = service.findButterflySpreads("SPY", chain);

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Given strikes [95, 100, 105], when findButterflySpreads, then finds one butterfly")
    void butterflyFindsSymmetricStrikes() {
        List<CdmOptionSnapshot> chain = List.of(
            option(95.0, OptionType.CALL),
            option(100.0, OptionType.CALL),
            option(105.0, OptionType.CALL)
        );

        LegMatchService service = new LegMatchService();
        List<LegGroup> result = service.findButterflySpreads("SPY", chain);

        assertEquals(1, result.size());
        assertEquals(3, result.getFirst().legCount());
        assertEquals(StrategyType.CALL_BUTTERFLY, result.getFirst().strategyType());
    }

    @Test
    @DisplayName("Given 2 options, when findVerticalSpreads, then finds one spread")
    void verticalSpreadFindsAdjacentStrikes() {
        List<CdmOptionSnapshot> chain = List.of(
            option(100.0, OptionType.CALL),
            option(105.0, OptionType.CALL)
        );

        LegMatchService service = new LegMatchService();
        List<LegGroup> result = service.findVerticalSpreads("SPY", chain);

        assertEquals(1, result.size());
        assertEquals(2, result.getFirst().legCount());
    }

    @Test
    @DisplayName("Given 4 symmetric strikes, when findCondors, then finds one condor")
    void condorFindsFourStrikes() {
        List<CdmOptionSnapshot> chain = List.of(
            option(95.0, OptionType.CALL),
            option(100.0, OptionType.CALL),
            option(105.0, OptionType.CALL),
            option(110.0, OptionType.CALL)
        );

        LegMatchService service = new LegMatchService();
        List<LegGroup> result = service.findCondors("SPY", chain);

        assertFalse(result.isEmpty());
        assertEquals(4, result.getFirst().legCount());
    }

    @Test
    @DisplayName("Given options on different expiries, when findButterflySpreads, then groups by expiry")
    void butterflyGroupsByExpiry() {
        LocalDate expiry1 = LocalDate.of(2026, 6, 20);
        LocalDate expiry2 = LocalDate.of(2026, 7, 20);

        List<CdmOptionSnapshot> chain = List.of(
            new CdmOptionSnapshot(UUID.randomUUID(), "SPY", BigDecimal.valueOf(95), expiry1, OptionType.CALL,
                DayCountConvention.ACT_365_FIXED, 0.5, 0.1, -0.02, 0.15, 0.01, 0.25, 0.5, 1.0, 2.0, 100, NOW),
            new CdmOptionSnapshot(UUID.randomUUID(), "SPY", BigDecimal.valueOf(100), expiry1, OptionType.CALL,
                DayCountConvention.ACT_365_FIXED, 0.5, 0.1, -0.02, 0.15, 0.01, 0.25, 0.5, 1.0, 2.0, 100, NOW),
            new CdmOptionSnapshot(UUID.randomUUID(), "SPY", BigDecimal.valueOf(105), expiry1, OptionType.CALL,
                DayCountConvention.ACT_365_FIXED, 0.5, 0.1, -0.02, 0.15, 0.01, 0.25, 0.5, 1.0, 2.0, 100, NOW),
            new CdmOptionSnapshot(UUID.randomUUID(), "SPY", BigDecimal.valueOf(95), expiry2, OptionType.CALL,
                DayCountConvention.ACT_365_FIXED, 0.5, 0.1, -0.02, 0.15, 0.01, 0.25, 0.5, 1.0, 2.0, 100, NOW),
            new CdmOptionSnapshot(UUID.randomUUID(), "SPY", BigDecimal.valueOf(100), expiry2, OptionType.CALL,
                DayCountConvention.ACT_365_FIXED, 0.5, 0.1, -0.02, 0.15, 0.01, 0.25, 0.5, 1.0, 2.0, 100, NOW),
            new CdmOptionSnapshot(UUID.randomUUID(), "SPY", BigDecimal.valueOf(105), expiry2, OptionType.CALL,
                DayCountConvention.ACT_365_FIXED, 0.5, 0.1, -0.02, 0.15, 0.01, 0.25, 0.5, 1.0, 2.0, 100, NOW)
        );

        LegMatchService service = new LegMatchService();
        List<LegGroup> result = service.findButterflySpreads("SPY", chain);

        assertEquals(2, result.size());
    }

    @Test
    @DisplayName("Given StrategyContext with irScore 0.8, when compute, then returns NEUTRAL")
    void irScoreBelowThresholdReturnsNeutral() {
        var strategy = new BaseOptionStrategy(StrategyType.BULL_CALL_SPREAD) {
            @Override
            protected double calculateFormula(LegGroup legs) {
                return 0.5;
            }
        };

        LegGroup legs = new LegGroup(List.of(
            option(100.0, OptionType.CALL),
            option(105.0, OptionType.CALL)
        ), StrategyType.BULL_CALL_SPREAD);

        StrategyContext lowIrCtx = new StrategyContext(0.8, 0.9);
        AlphaSignal signal = strategy.compute(legs, lowIrCtx);

        assertEquals("NEUTRAL", signal.direction());
        assertEquals(0.0, signal.strength(), 0.001);
    }

    @Test
    @DisplayName("Given StrategyContext with irScore 0.95, when compute, then returns actual signal")
    void irScoreAboveThresholdReturnsSignal() {
        var strategy = new BaseOptionStrategy(StrategyType.BULL_CALL_SPREAD) {
            @Override
            protected double calculateFormula(LegGroup legs) {
                return 0.5;
            }
        };

        LegGroup legs = new LegGroup(List.of(
            option(100.0, OptionType.CALL),
            option(105.0, OptionType.CALL)
        ), StrategyType.BULL_CALL_SPREAD);

        StrategyContext goodCtx = new StrategyContext(0.95, 0.9);
        AlphaSignal signal = strategy.compute(legs, goodCtx);

        assertEquals("LONG", signal.direction());
        assertTrue(signal.strength() > 0);
        assertTrue(signal.metrics().containsKey("net_delta"));
        assertTrue(signal.metrics().containsKey("net_theta"));
        assertTrue(signal.metrics().containsKey("spread_efficiency"));
    }
}
