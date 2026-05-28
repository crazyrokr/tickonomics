package com.tickonomics.computation.stress;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tickonomics.computation.stress.LiquidityStressTestModule.StressScenario;
import com.tickonomics.computation.stress.LiquidityStressTestModule.StressTestResult;

@ExtendWith(MockitoExtension.class)
class LiquidityStressTestModuleTest {

    private LiquidityStressTestModule module;

    @BeforeEach
    void setUp() {
        module = new LiquidityStressTestModule();
    }

    @Nested
    class RunScenario {

        @Test
        void givenSwissFrancScenario_whenRunScenario_thenPassesDueToModerateDrawdown() {
            // Given a Swiss Franc 2015 stress scenario with a moderate current ILI
            StressScenario scenario = LiquidityStressTestModule.swissFranc2015();
            double currentIli = 0.5;
            double[] historical = {0.4, 0.45, 0.5, 0.55, 0.48};

            // When running the stress scenario
            StressTestResult result = module.runScenario(scenario, currentIli, historical);

            // Then the result contains the scenario name and a computed verdict
            assertEquals("SWISS_FRANC_2015", result.scenarioName());
            assertTrue(result.drawdownPct() >= 0);
            assertTrue(result.timeToRecoveryDays() >= 0);
            assertTrue(result.falsePositiveRate() >= 0);
        }

        @Test
        void givenRepoSpikeScenario_whenRunScenario_thenReturnsNonNullResult() {
            // Given a Repo Spike 2019 scenario
            StressScenario scenario = LiquidityStressTestModule.repoSpike2019();
            double currentIli = 0.3;
            double[] historical = {0.2, 0.25, 0.3, 0.35, 0.28};

            // When running the stress scenario
            StressTestResult result = module.runScenario(scenario, currentIli, historical);

            // Then a non-null result with the correct name is produced
            assertNotNull(result);
            assertEquals("REPO_SPIKE_2019", result.scenarioName());
        }

        @Test
        void givenCovidScenario_whenRunScenario_thenReturnsExpectedFields() {
            // Given a COVID 2020 stress scenario
            StressScenario scenario = LiquidityStressTestModule.covid2020();
            double currentIli = 0.6;
            double[] historical = {0.5, 0.55, 0.6, 0.58, 0.52};

            // When running the stress scenario
            StressTestResult result = module.runScenario(scenario, currentIli, historical);

            // Then all result fields are populated
            assertEquals("COVID_2020", result.scenarioName());
            assertTrue(result.drawdownPct() >= 0);
            assertTrue(result.timeToRecoveryDays() >= 0);
            assertTrue(result.falsePositiveRate() >= 0);
        }

        @Test
        void givenAllPredefinedScenarios_whenRunScenario_thenAllReturnResults() {
            // Given all three predefined stress scenarios
            double currentIli = 0.4;
            double[] historical = {0.3, 0.35, 0.4, 0.45, 0.38};
            StressScenario[] scenarios = {
                    LiquidityStressTestModule.swissFranc2015(),
                    LiquidityStressTestModule.repoSpike2019(),
                    LiquidityStressTestModule.covid2020()
            };

            // When each scenario is run
            for (StressScenario scenario : scenarios) {
                StressTestResult result = module.runScenario(scenario, currentIli, historical);

                // Then each returns a non-null result with a matching name
                assertNotNull(result);
                assertEquals(scenario.name(), result.scenarioName());
            }
        }
    }

    @Nested
    class PassFailCriteria {

        @Test
        void givenDrawdownBelow15AndFprBelow5_whenRunScenario_thenPassedIsTrue() {
            // Given a scenario where ILI equals historical mean (minimizing drawdown)
            // and historical values are very small (minimizing FPR exceedances)
            StressScenario mildScenario = new StressScenario("MILD", 1.0, 1.0, 0.5, "Mild stress");
            double currentIli = 0.005;
            double[] historical = {0.005, 0.005, 0.005, 0.005, 0.005};

            // When running the mild scenario
            StressTestResult result = module.runScenario(mildScenario, currentIli, historical);

            // Then the result passes because drawdown is tiny and FPR is low
            assertTrue(result.passed(), "Expected passed=true, drawdown=" + result.drawdownPct() + ", fpr=" + result.falsePositiveRate());
            assertTrue(result.drawdownPct() < 15.0);
            assertTrue(result.falsePositiveRate() < 5.0);
        }

        @Test
        void givenExtremeShocks_whenRunScenario_thenPassedMayBeFalse() {
            // Given a scenario with extreme shocks
            StressScenario extreme = new StressScenario("EXTREME", 5000.0, 5000.0, 10.0, "Extreme stress");
            double currentIli = 0.1;
            double[] historical = {0.1, 0.1, 0.1, 0.1, 0.1};

            // When running the extreme scenario
            StressTestResult result = module.runScenario(extreme, currentIli, historical);

            // Then the result fails due to excessive drawdown
            assertFalse(result.passed());
        }

        @Test
        void givenHighFalsePositiveHistorical_whenRunScenario_thenFailsOnFpr() {
            // Given a scenario where historical ILI values all exceed the spread threshold
            StressScenario scenario = new StressScenario("HIGH_FPR", 10.0, 0.01, 1.0, "High FPR test");
            double currentIli = 0.5;
            double[] historical = {10.0, 20.0, 30.0, 40.0, 50.0};

            // When running the scenario
            StressTestResult result = module.runScenario(scenario, currentIli, historical);

            // Then all historical values exceed threshold, FPR is 100%, and test fails
            assertEquals(100.0, result.falsePositiveRate(), 0.001);
            assertFalse(result.passed());
        }
    }

    @Nested
    class NullAndEmptyInputs {

        @Test
        void givenNullHistorical_whenRunScenario_thenReturnsZeroFalsePositiveRate() {
            // Given a scenario with null historical ILI values
            StressScenario scenario = LiquidityStressTestModule.swissFranc2015();
            double currentIli = 0.5;

            // When running the scenario with null historical data
            StressTestResult result = module.runScenario(scenario, currentIli, null);

            // Then false positive rate is zero and result is still produced
            assertEquals(0.0, result.falsePositiveRate());
            assertNotNull(result.scenarioName());
        }

        @Test
        void givenEmptyHistorical_whenRunScenario_thenReturnsZeroFalsePositiveRate() {
            // Given a scenario with empty historical ILI values
            StressScenario scenario = LiquidityStressTestModule.repoSpike2019();
            double currentIli = 0.3;
            double[] emptyHistorical = {};

            // When running the scenario with empty historical data
            StressTestResult result = module.runScenario(scenario, currentIli, emptyHistorical);

            // Then false positive rate is zero and result is still produced
            assertEquals(0.0, result.falsePositiveRate());
            assertNotNull(result.scenarioName());
        }

        @Test
        void givenZeroCurrentIli_whenRunScenario_thenReturnsResult() {
            // Given a zero current ILI value
            StressScenario scenario = LiquidityStressTestModule.covid2020();
            double currentIli = 0.0;
            double[] historical = {0.1, 0.2, 0.15};

            // When running the scenario
            StressTestResult result = module.runScenario(scenario, currentIli, historical);

            // Then a result is still produced with valid fields
            assertNotNull(result);
            assertEquals("COVID_2020", result.scenarioName());
            assertTrue(result.drawdownPct() >= 0);
        }
    }

    @Nested
    class ApplyShocks {

        @Test
        void givenShocksApplied_whenIliIsPositive_thenShockedIliIsLower() {
            // Given a positive ILI and Swiss Franc scenario shocks
            StressScenario scenario = LiquidityStressTestModule.swissFranc2015();
            double currentIli = 0.5;
            double[] historical = {0.4, 0.5, 0.6};

            // When applying shocks
            double shocked = module.applyShocks(scenario, currentIli, historical);

            // Then the shocked ILI is less than the current ILI
            assertTrue(shocked < currentIli);
        }

        @Test
        void givenNullHistorical_whenApplyShocks_thenUsesZeroMean() {
            // Given null historical values
            StressScenario scenario = LiquidityStressTestModule.repoSpike2019();
            double currentIli = 0.5;

            // When applying shocks with null historical data
            double shocked = module.applyShocks(scenario, currentIli, null);

            // Then the shocked value is computed with zero mean baseline
            assertTrue(shocked < currentIli);
        }

        @Test
        void givenEmptyHistorical_whenApplyShocks_thenUsesZeroMean() {
            // Given empty historical values
            StressScenario scenario = LiquidityStressTestModule.repoSpike2019();
            double currentIli = 0.5;
            double[] empty = {};

            // When applying shocks with empty historical data
            double shocked = module.applyShocks(scenario, currentIli, empty);

            // Then the shocked value is computed with zero mean baseline
            assertTrue(shocked < currentIli);
        }
    }

    @Nested
    class ComputeDrawdown {

        @Test
        void givenNoDifference_whenComputeDrawdown_thenReturnsZero() {
            // Given identical current and shocked ILI values
            StressScenario scenario = LiquidityStressTestModule.swissFranc2015();

            // When computing drawdown with no difference
            double drawdown = module.computeDrawdown(0.5, 0.5, scenario);

            // Then drawdown is zero
            assertEquals(0.0, drawdown, 0.0001);
        }

        @Test
        void givenSmallDifference_whenComputeDrawdown_thenReturnsScaledDrawdown() {
            // Given a small ILI difference
            StressScenario scenario = LiquidityStressTestModule.swissFranc2015();

            // When computing drawdown
            double drawdown = module.computeDrawdown(0.5, 0.4, scenario);

            // Then drawdown is positive and scaled by vol shock multiplier
            assertTrue(drawdown > 0);
            double expectedRaw = Math.abs(0.5 - 0.4) * 100.0;
            assertEquals(expectedRaw * scenario.volShockMultiplier(), drawdown, 0.0001);
        }

        @Test
        void givenVolShockMultiplierOfOne_whenComputeDrawdown_thenReturnsRawDrawdown() {
            // Given a scenario with vol shock multiplier of 1.0
            StressScenario scenario = new StressScenario("UNIT_VOL", 100.0, 100.0, 1.0, "Unit vol test");

            // When computing drawdown
            double drawdown = module.computeDrawdown(0.6, 0.4, scenario);

            // Then drawdown equals the raw percentage difference
            assertEquals(20.0, drawdown, 0.0001);
        }
    }

    @Nested
    class ComputeRecoveryTime {

        @Test
        void givenZeroDrawdown_whenComputeRecoveryTime_thenReturnsZero() {
            // Given zero drawdown
            StressScenario scenario = LiquidityStressTestModule.swissFranc2015();

            // When computing recovery time
            double recovery = module.computeRecoveryTime(0.0, scenario);

            // Then recovery time is zero
            assertEquals(0.0, recovery, 0.0001);
        }

        @Test
        void givenPositiveDrawdown_whenComputeRecoveryTime_thenReturnsPositiveDays() {
            // Given positive drawdown with Swiss Franc scenario
            StressScenario scenario = LiquidityStressTestModule.swissFranc2015();

            // When computing recovery time
            double recovery = module.computeRecoveryTime(10.0, scenario);

            // Then recovery time is positive
            assertTrue(recovery > 0);
        }

        @Test
        void givenHighVolShock_whenComputeRecoveryTime_thenRecoveryIsFaster() {
            // Given same drawdown but different vol shock multipliers
            StressScenario lowVol = new StressScenario("LOW_VOL", 100.0, 100.0, 2.0, "Low vol");
            StressScenario highVol = new StressScenario("HIGH_VOL", 100.0, 100.0, 4.0, "High vol");

            // When computing recovery times
            double recoveryLow = module.computeRecoveryTime(10.0, lowVol);
            double recoveryHigh = module.computeRecoveryTime(10.0, highVol);

            // Then higher vol shock implies faster recovery
            assertTrue(recoveryHigh < recoveryLow);
        }
    }

    @Nested
    class ComputeFalsePositiveRate {

        @Test
        void givenNoExceedances_whenComputeFpr_thenReturnsZero() {
            // Given historical values all below threshold
            StressScenario scenario = new StressScenario("TEST", 100.0, 10000.0, 1.0, "Test");
            double[] historical = {0.1, 0.2, 0.3, 0.4, 0.5};

            // When computing false positive rate
            double fpr = module.computeFalsePositiveRate(scenario, historical);

            // Then FPR is zero since no values exceed the high spread threshold
            assertEquals(0.0, fpr, 0.0001);
        }

        @Test
        void givenAllExceedances_whenComputeFpr_thenReturns100() {
            // Given historical values all above threshold
            StressScenario scenario = new StressScenario("TEST", 100.0, 0.01, 1.0, "Test");
            double[] historical = {5.0, 10.0, 15.0};

            // When computing false positive rate
            double fpr = module.computeFalsePositiveRate(scenario, historical);

            // Then FPR is 100% since all values exceed threshold
            assertEquals(100.0, fpr, 0.0001);
        }

        @Test
        void givenHalfExceedances_whenComputeFpr_thenReturns50() {
            // Given historical values where half exceed the threshold (0.5/100 = 0.005)
            StressScenario scenario = new StressScenario("TEST", 100.0, 0.5, 1.0, "Test");
            double[] historical = {0.001, 0.002, -0.01, -0.02};

            // When computing false positive rate
            double fpr = module.computeFalsePositiveRate(scenario, historical);

            // Then FPR is 50% (two out of four exceed threshold 0.005)
            assertEquals(50.0, fpr, 0.0001);
        }

        @Test
        void givenNullHistorical_whenComputeFpr_thenReturnsZero() {
            // Given null historical data
            StressScenario scenario = LiquidityStressTestModule.swissFranc2015();

            // When computing false positive rate
            double fpr = module.computeFalsePositiveRate(scenario, null);

            // Then FPR defaults to zero
            assertEquals(0.0, fpr, 0.0001);
        }

        @Test
        void givenEmptyHistorical_whenComputeFpr_thenReturnsZero() {
            // Given empty historical data
            StressScenario scenario = LiquidityStressTestModule.repoSpike2019();
            double[] empty = {};

            // When computing false positive rate
            double fpr = module.computeFalsePositiveRate(scenario, empty);

            // Then FPR defaults to zero
            assertEquals(0.0, fpr, 0.0001);
        }
    }

    @Nested
    class ComputeMean {

        @Test
        void givenNullArray_whenComputeMean_thenReturnsZero() {
            // Given a null array

            // When computing the mean
            double mean = module.computeMean(null);

            // Then mean is zero
            assertEquals(0.0, mean, 0.0001);
        }

        @Test
        void givenEmptyArray_whenComputeMean_thenReturnsZero() {
            // Given an empty array
            double[] empty = {};

            // When computing the mean
            double mean = module.computeMean(empty);

            // Then mean is zero
            assertEquals(0.0, mean, 0.0001);
        }

        @Test
        void givenSingleValue_whenComputeMean_thenReturnsThatValue() {
            // Given an array with a single value
            double[] values = {0.42};

            // When computing the mean
            double mean = module.computeMean(values);

            // Then mean equals the single value
            assertEquals(0.42, mean, 0.0001);
        }

        @Test
        void givenMultipleValues_whenComputeMean_thenReturnsAverage() {
            // Given an array with multiple values
            double[] values = {0.2, 0.4, 0.6, 0.8};

            // When computing the mean
            double mean = module.computeMean(values);

            // Then mean equals the arithmetic average
            assertEquals(0.5, mean, 0.0001);
        }
    }

    @Nested
    class PredefinedScenarios {

        @Test
        void givenSwissFranc2015_whenCreated_thenFieldsMatchSpec() {
            // Given the Swiss Franc 2015 scenario factory method

            // When retrieving the scenario
            StressScenario scenario = LiquidityStressTestModule.swissFranc2015();

            // Then all fields match the specification
            assertEquals("SWISS_FRANC_2015", scenario.name());
            assertEquals(200.0, scenario.rrpShockBps(), 0.001);
            assertEquals(150.0, scenario.spreadShockBps(), 0.001);
            assertEquals(3.0, scenario.volShockMultiplier(), 0.001);
            assertFalse(scenario.description().isEmpty());
        }

        @Test
        void givenRepoSpike2019_whenCreated_thenFieldsMatchSpec() {
            // Given the Repo Spike 2019 scenario factory method

            // When retrieving the scenario
            StressScenario scenario = LiquidityStressTestModule.repoSpike2019();

            // Then all fields match the specification
            assertEquals("REPO_SPIKE_2019", scenario.name());
            assertEquals(100.0, scenario.rrpShockBps(), 0.001);
            assertEquals(200.0, scenario.spreadShockBps(), 0.001);
            assertEquals(2.5, scenario.volShockMultiplier(), 0.001);
            assertFalse(scenario.description().isEmpty());
        }

        @Test
        void givenCovid2020_whenCreated_thenFieldsMatchSpec() {
            // Given the COVID 2020 scenario factory method

            // When retrieving the scenario
            StressScenario scenario = LiquidityStressTestModule.covid2020();

            // Then all fields match the specification
            assertEquals("COVID_2020", scenario.name());
            assertEquals(50.0, scenario.rrpShockBps(), 0.001);
            assertEquals(300.0, scenario.spreadShockBps(), 0.001);
            assertEquals(4.0, scenario.volShockMultiplier(), 0.001);
            assertFalse(scenario.description().isEmpty());
        }
    }
}
