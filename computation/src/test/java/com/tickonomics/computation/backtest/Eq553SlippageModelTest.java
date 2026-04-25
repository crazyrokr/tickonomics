package com.tickonomics.computation.backtest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class Eq553SlippageModelTest {

    private final Eq553SlippageModel model = new Eq553SlippageModel();

    @Test
    @DisplayName("Given high volume and low vol, when calculateSlippageBps, then slippage is small")
    void lowSlippageForHighVolumeLowVol() {
        double slippage = model.calculateSlippageBps(0.15, 10_000_000, 1000);
        assertTrue(slippage < 1.0, "Expected low slippage, got: " + slippage);
    }

    @Test
    @DisplayName("Given low volume and high vol, when calculateSlippageBps, then slippage is large")
    void highSlippageForLowVolumeHighVol() {
        double slippage = model.calculateSlippageBps(0.50, 100_000, 50000);
        assertTrue(slippage > 0.0, "Expected positive slippage, got: " + slippage);
    }

    @Test
    @DisplayName("Given zero ADDV, when calculateSlippageBps, then returns infinity")
    void zeroAddvReturnsInfinity() {
        double slippage = model.calculateSlippageBps(0.20, 0, 100);
        assertEquals(Double.MAX_VALUE, slippage);
    }

    @Test
    @DisplayName("Given positive ideal return exceeding slippage, when adjustReturn, then adjusted is positive")
    void adjustReturnReducesBySlippage() {
        double adjusted = model.adjustReturn(0.05, 0.15, 5_000_000, 1000);
        assertTrue(adjusted <= 0.05);
    }

    @Test
    @DisplayName("Given ideal return that collapses under slippage, when isLiquidityFragile, then returns true")
    void detectsLiquidityFragile() {
        assertTrue(model.isLiquidityFragile(0.001, -0.002));
    }

    @Test
    @DisplayName("Given robust return, when isLiquidityFragile, then returns false")
    void robustReturnNotFragile() {
        assertFalse(model.isLiquidityFragile(0.05, 0.04));
    }
}
