package com.tickonomics.computation.regime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class SessionRangeServiceTest {

    private SessionRangeService service;

    @BeforeEach
    void setUp() {
        service = new SessionRangeService();
    }

    private Instant atHour(int hour) {
        return LocalDateTime.of(2026, 6, 3, hour, 0, 0)
                .toInstant(ZoneOffset.UTC);
    }

    private Instant atHourMinute(int hour, int minute) {
        return LocalDateTime.of(2026, 6, 3, hour, minute, 0)
                .toInstant(ZoneOffset.UTC);
    }

    @Nested
    class Detect {

        @Nested
        class NullInput {

            @Test
            void givenNullTimestamp_whenDetect_thenThrowsIllegalArgumentException() {
                // Given: a null timestamp
                // When: detect is called
                // Then: IllegalArgumentException is thrown
                assertThrows(IllegalArgumentException.class, () -> service.detect(null));
            }
        }

        @Nested
        class AsianSession {

            @Test
            void givenHour0_whenDetect_thenReturnsAsianSession() {
                // Given: a timestamp at 00:00 UTC (Asian session start)
                // When: detect is called
                // Then: session type is ASIAN with correct boundaries and multiplier
                var result = service.detect(atHour(0));

                assertEquals("ASIAN", result.sessionType());
                assertEquals(0, result.startHour());
                assertEquals(9, result.endHour());
                assertEquals(0.8, result.volatilityMultiplier());
            }

            @Test
            void givenHour4_whenDetect_thenReturnsAsianSession() {
                // Given: a timestamp at 04:00 UTC (deep within Asian session)
                // When: detect is called
                // Then: session type is ASIAN
                var result = service.detect(atHour(4));

                assertEquals("ASIAN", result.sessionType());
                assertEquals(0.8, result.volatilityMultiplier());
            }

            @Test
            void givenHour6_whenDetect_thenReturnsAsianSessionOverlapNotTriggered() {
                // Given: a timestamp at 06:00 UTC (Asian session before European overlap)
                // When: detect is called
                // Then: session type is ASIAN (European starts at 07:00)
                var result = service.detect(atHour(6));

                assertEquals("ASIAN", result.sessionType());
            }
        }

        @Nested
        class EuropeanSession {

            @Test
            void givenHour10_whenDetect_thenReturnsEuropeanSession() {
                // Given: a timestamp at 10:00 UTC (European-only session)
                // When: detect is called
                // Then: session type is EUROPEAN with correct boundaries and multiplier
                var result = service.detect(atHour(10));

                assertEquals("EUROPEAN", result.sessionType());
                assertEquals(7, result.startHour());
                assertEquals(16, result.endHour());
                assertEquals(1.0, result.volatilityMultiplier());
            }

            @Test
            void givenHour12_whenDetect_thenReturnsEuropeanSession() {
                // Given: a timestamp at 12:00 UTC (European session, US not yet started)
                // When: detect is called
                // Then: session type is EUROPEAN
                var result = service.detect(atHour(12));

                assertEquals("EUROPEAN", result.sessionType());
            }
        }

        @Nested
        class UsSession {

            @Test
            void givenHour17_whenDetect_thenReturnsUsSession() {
                // Given: a timestamp at 17:00 UTC (US-only session after European close)
                // When: detect is called
                // Then: session type is US with correct boundaries and multiplier
                var result = service.detect(atHour(17));

                assertEquals("US", result.sessionType());
                assertEquals(13, result.startHour());
                assertEquals(22, result.endHour());
                assertEquals(1.2, result.volatilityMultiplier());
            }

            @Test
            void givenHour20_whenDetect_thenReturnsUsSession() {
                // Given: a timestamp at 20:00 UTC (deep within US session)
                // When: detect is called
                // Then: session type is US
                var result = service.detect(atHour(20));

                assertEquals("US", result.sessionType());
            }

            @Test
            void givenHour21_whenDetect_thenReturnsUsSession() {
                // Given: a timestamp at 21:00 UTC (last full hour of US session)
                // When: detect is called
                // Then: session type is US
                var result = service.detect(atHour(21));

                assertEquals("US", result.sessionType());
            }
        }

        @Nested
        class OverlapEuUs {

            @Test
            void givenHour13_whenDetect_thenReturnsOverlapEuUs() {
                // Given: a timestamp at 13:00 UTC (EU/US overlap start)
                // When: detect is called
                // Then: session type is OVERLAP_EU_US with highest volatility multiplier
                var result = service.detect(atHour(13));

                assertEquals("OVERLAP_EU_US", result.sessionType());
                assertEquals(13, result.startHour());
                assertEquals(16, result.endHour());
                assertEquals(1.5, result.volatilityMultiplier());
            }

            @Test
            void givenHour15_whenDetect_thenReturnsOverlapEuUs() {
                // Given: a timestamp at 15:00 UTC (deep within EU/US overlap)
                // When: detect is called
                // Then: session type is OVERLAP_EU_US
                var result = service.detect(atHour(15));

                assertEquals("OVERLAP_EU_US", result.sessionType());
            }
        }

        @Nested
        class OverlapAsEu {

            @Test
            void givenHour7_whenDetect_thenReturnsOverlapAsEu() {
                // Given: a timestamp at 07:00 UTC (Asian/European overlap start)
                // When: detect is called
                // Then: session type is OVERLAP_AS_EU with correct multiplier
                var result = service.detect(atHour(7));

                assertEquals("OVERLAP_AS_EU", result.sessionType());
                assertEquals(7, result.startHour());
                assertEquals(9, result.endHour());
                assertEquals(1.3, result.volatilityMultiplier());
            }

            @Test
            void givenHour8_whenDetect_thenReturnsOverlapAsEu() {
                // Given: a timestamp at 08:00 UTC (within Asian/European overlap)
                // When: detect is called
                // Then: session type is OVERLAP_AS_EU
                var result = service.detect(atHour(8));

                assertEquals("OVERLAP_AS_EU", result.sessionType());
            }
        }

        @Nested
        class OvernightSession {

            @Test
            void givenHour22_whenDetect_thenReturnsOvernight() {
                // Given: a timestamp at 22:00 UTC (overnight period start)
                // When: detect is called
                // Then: session type is OVERNIGHT with lowest multiplier
                var result = service.detect(atHour(22));

                assertEquals("OVERNIGHT", result.sessionType());
                assertEquals(22, result.startHour());
                assertEquals(0, result.endHour());
                assertEquals(0.6, result.volatilityMultiplier());
            }

            @Test
            void givenHour23_whenDetect_thenReturnsOvernight() {
                // Given: a timestamp at 23:00 UTC (deep overnight)
                // When: detect is called
                // Then: session type is OVERNIGHT
                var result = service.detect(atHour(23));

                assertEquals("OVERNIGHT", result.sessionType());
            }
        }

        @Nested
        class BoundaryValues {

            @Test
            void givenHour1_whenDetect_thenReturnsAsianSession() {
                // Given: a timestamp at 01:00 UTC (one hour into Asian session)
                // When: detect is called
                // Then: session type is ASIAN
                var result = service.detect(atHour(1));

                assertEquals("ASIAN", result.sessionType());
            }

            @Test
            void givenHour9_whenDetect_thenReturnsEuropeanSession() {
                // Given: a timestamp at 09:00 UTC (Asian ends, European active, US not started)
                // When: detect is called
                // Then: session type is EUROPEAN (Asian is hour < 9, so 9 is not Asian)
                var result = service.detect(atHour(9));

                assertEquals("EUROPEAN", result.sessionType());
            }

            @Test
            void givenHour16_whenDetect_thenReturnsUsSession() {
                // Given: a timestamp at 16:00 UTC (European ends, US still active)
                // When: detect is called
                // Then: session type is US (European is hour < 16, so 16 is not European)
                var result = service.detect(atHour(16));

                assertEquals("US", result.sessionType());
            }

            @Test
            void givenEpochInstant_whenDetect_thenReturnsAsianSession() {
                // Given: the epoch instant (1970-01-01T00:00:00Z)
                // When: detect is called
                // Then: session type is ASIAN (hour 0)
                var result = service.detect(Instant.EPOCH);

                assertEquals("ASIAN", result.sessionType());
            }

            @Test
            void givenMidnightMinute_whenDetect_thenReturnsAsianSession() {
                // Given: a timestamp at 00:30 UTC
                // When: detect is called
                // Then: session type is ASIAN
                var result = service.detect(atHourMinute(0, 30));

                assertEquals("ASIAN", result.sessionType());
            }
        }

        @Nested
        class AllHoursCoverage {

            @Test
            void givenAll24Hours_whenDetect_thenEachHourReturnsExpectedSessionType() {
                // Given: all 24 hours of the day in UTC
                // When: detect is called for each hour
                // Then: every hour maps to exactly one expected session type
                String[] expected = {
                        "ASIAN",         // 00
                        "ASIAN",         // 01
                        "ASIAN",         // 02
                        "ASIAN",         // 03
                        "ASIAN",         // 04
                        "ASIAN",         // 05
                        "ASIAN",         // 06
                        "OVERLAP_AS_EU", // 07
                        "OVERLAP_AS_EU", // 08
                        "EUROPEAN",      // 09
                        "EUROPEAN",      // 10
                        "EUROPEAN",      // 11
                        "EUROPEAN",      // 12
                        "OVERLAP_EU_US", // 13
                        "OVERLAP_EU_US", // 14
                        "OVERLAP_EU_US", // 15
                        "US",            // 16
                        "US",            // 17
                        "US",            // 18
                        "US",            // 19
                        "US",            // 20
                        "US",            // 21
                        "OVERNIGHT",     // 22
                        "OVERNIGHT",     // 23
                };

                assertEquals(24, expected.length);

                for (int hour = 0; hour < 24; hour++) {
                    var result = service.detect(atHour(hour));
                    assertEquals(expected[hour], result.sessionType(),
                            "Mismatch at hour " + hour);
                }
            }
        }

        @Nested
        class VolatilityMultiplierOrdering {

            @Test
            void givenEachSessionType_whenDetect_thenMultipliersAreCorrect() {
                // Given: timestamps representing each distinct session type
                // When: detect is called
                // Then: each session returns the correct volatility multiplier
                assertEquals(0.6, service.detect(atHour(22)).volatilityMultiplier()); // OVERNIGHT
                assertEquals(0.8, service.detect(atHour(0)).volatilityMultiplier());  // ASIAN
                assertEquals(1.0, service.detect(atHour(10)).volatilityMultiplier()); // EUROPEAN
                assertEquals(1.2, service.detect(atHour(17)).volatilityMultiplier()); // US
                assertEquals(1.5, service.detect(atHour(13)).volatilityMultiplier()); // OVERLAP_EU_US
                assertEquals(1.3, service.detect(atHour(7)).volatilityMultiplier());  // OVERLAP_AS_EU
            }
        }
    }
}
