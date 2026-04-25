package com.tickonomics.computation.fixedincome;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FixedIncomePortfolioBuilderTest {

    private FixedIncomePortfolioBuilder builder;
    private List<BondPosition> bonds;

    @BeforeEach
    void setUp() {
        builder = new FixedIncomePortfolioBuilder();
        bonds = List.of(
            new BondPosition("2Y", 1.9, 4.5, 0.0),
            new BondPosition("5Y", 4.7, 4.3, 0.0),
            new BondPosition("10Y", 8.9, 4.5, 0.0),
            new BondPosition("30Y", 19.0, 4.8, 0.0)
        );
    }

    @Test
    @DisplayName("Given target duration 5, when buildBullet, then selects closest bond")
    void bulletSelectsClosestDuration() {
        FixedIncomePortfolio portfolio = builder.buildBullet(5.0, bonds);

        assertEquals(FixedIncomeStrategyType.BULLET_PORTFOLIO, portfolio.type());
        assertEquals(1, portfolio.positions().size());
        assertEquals(4.7, portfolio.positions().getFirst().duration(), 0.1);
    }

    @Test
    @DisplayName("Given short and long tenors, when buildBarbell, then duration-weighted allocation")
    void barbellAllocatesByDuration() {
        FixedIncomePortfolio portfolio = builder.buildBarbell(2.0, 20.0, bonds);

        assertEquals(FixedIncomeStrategyType.BARBELL_PORTFOLIO, portfolio.type());
        assertEquals(2, portfolio.positions().size());
        assertTrue(portfolio.convexity() > 0);
    }

    @Test
    @DisplayName("Given three tenors, when buildDurationNeutral, then net duration is near zero")
    void durationNeutralHasNearZeroDuration() {
        FixedIncomePortfolio portfolio = builder.buildDurationNeutral(2.0, 5.0, 20.0, bonds);

        assertEquals(FixedIncomeStrategyType.DURATION_NEUTRAL, portfolio.type());
        assertEquals(3, portfolio.positions().size());
        assertTrue(Math.abs(portfolio.portfolioDuration()) < 3.0);
    }

    @Test
    @DisplayName("Given empty bond list, when buildBullet, then throws IllegalArgumentException")
    void bulletRejectsEmptyBondList() {
        assertThrows(IllegalArgumentException.class, () -> builder.buildBullet(5.0, List.of()));
    }
}
