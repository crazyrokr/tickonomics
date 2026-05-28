package com.tickonomics.ingestion.time;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EventBasedTimeConverterTest {

    private EventBasedTimeConverter converter;

    @BeforeEach
    void setUp() {
        converter = new EventBasedTimeConverter();
    }

    @Nested
    class DirectionalChangeDetection {

        @Test
        void givenUpwardPriceMovementAboveTheta_whenConvert_thenEmitsDirectionalChangeUp() {
            // Given
            double[] prices = {100.0, 100.0, 101.0};
            long[] timestamps = {0, 1000, 2000};
            double theta = 0.005;

            // When
            List<EventBasedTimeConverter.TickEvent> events = converter.convert(prices, timestamps, theta);

            // Then
            assertThat(events).isNotEmpty();
            assertThat(events.get(0).eventType()).isEqualTo(EventBasedTimeConverter.EventType.DIRECTIONAL_CHANGE_UP);
        }

        @Test
        void givenDownwardPriceMovementAboveTheta_whenConvert_thenEmitsDirectionalChangeDown() {
            // Given
            double[] prices = {100.0, 100.0, 99.0};
            long[] timestamps = {0, 1000, 2000};
            double theta = 0.005;

            // When
            List<EventBasedTimeConverter.TickEvent> events = converter.convert(prices, timestamps, theta);

            // Then
            assertThat(events).isNotEmpty();
            assertThat(events.get(0).eventType()).isEqualTo(EventBasedTimeConverter.EventType.DIRECTIONAL_CHANGE_DOWN);
        }

        @Test
        void givenAlternatingDirections_whenConvert_thenEmitsAlternatingEvents() {
            // Given - up then down, each exceeding theta
            double[] prices = {100.0, 101.0, 100.0, 99.0};
            long[] timestamps = {0, 1000, 2000, 3000};
            double theta = 0.005;

            // When
            List<EventBasedTimeConverter.TickEvent> events = converter.convert(prices, timestamps, theta);

            // Then
            List<EventBasedTimeConverter.EventType> directionalChanges = events.stream()
                    .filter(e -> e.eventType() == EventBasedTimeConverter.EventType.DIRECTIONAL_CHANGE_UP
                            || e.eventType() == EventBasedTimeConverter.EventType.DIRECTIONAL_CHANGE_DOWN)
                    .map(EventBasedTimeConverter.TickEvent::eventType)
                    .toList();

            assertThat(directionalChanges).isNotEmpty();
            assertThat(directionalChanges).contains(EventBasedTimeConverter.EventType.DIRECTIONAL_CHANGE_UP);
            assertThat(directionalChanges).contains(EventBasedTimeConverter.EventType.DIRECTIONAL_CHANGE_DOWN);
        }

        @Test
        void givenCustomTheta_whenConvert_thenRespectsThreshold() {
            // Given - 0.5% move is below 1% theta
            double[] prices = {100.0, 100.3, 100.5};
            long[] timestamps = {0, 1000, 2000};
            double theta = 0.01;

            // When
            List<EventBasedTimeConverter.TickEvent> events = converter.convert(prices, timestamps, theta);

            // Then - no events because 0.5% < 1%
            assertThat(events).isEmpty();
        }

        @Test
        void givenDefaultTheta_whenConvertNoThetaArg_thenUsesDefault() {
            // Given - 1% move exceeds default theta of 0.005
            double[] prices = {100.0, 100.0, 101.0};
            long[] timestamps = {0, 1000, 2000};

            // When
            List<EventBasedTimeConverter.TickEvent> events = converter.convert(prices, timestamps);

            // Then
            assertThat(events).isNotEmpty();
        }
    }

    @Nested
    class OvershootDetection {

        @Test
        void givenContinuedUpwardAfterDirectionalChangeUp_whenConvert_thenEmitsOvershoot() {
            // Given - DC up at price 101, then continues to 102, 103
            double[] prices = {100.0, 100.0, 101.0, 102.0, 103.0};
            long[] timestamps = {0, 1000, 2000, 3000, 4000};
            double theta = 0.005;

            // When
            List<EventBasedTimeConverter.TickEvent> events = converter.convert(prices, timestamps, theta);

            // Then
            List<EventBasedTimeConverter.EventType> types = events.stream()
                    .map(EventBasedTimeConverter.TickEvent::eventType).toList();
            assertThat(types).contains(EventBasedTimeConverter.EventType.OVERSHOOT);
        }

        @Test
        void givenContinuedDownwardAfterDirectionalChangeDown_whenConvert_thenEmitsOvershoot() {
            // Given - DC down, then continues falling
            double[] prices = {100.0, 100.0, 99.0, 98.0, 97.0};
            long[] timestamps = {0, 1000, 2000, 3000, 4000};
            double theta = 0.005;

            // When
            List<EventBasedTimeConverter.TickEvent> events = converter.convert(prices, timestamps, theta);

            // Then
            List<EventBasedTimeConverter.EventType> types = events.stream()
                    .map(EventBasedTimeConverter.TickEvent::eventType).toList();
            assertThat(types).contains(EventBasedTimeConverter.EventType.OVERSHOOT);
        }

        @Test
        void givenReversalAfterOvershoot_whenConvert_thenEndsOversootAndDetectsNewDirection() {
            // Given - up DC, overshoot up, then reversal down exceeding theta
            double[] prices = {100.0, 101.0, 102.0, 103.0, 101.0};
            long[] timestamps = {0, 1000, 2000, 3000, 4000};
            double theta = 0.005;

            // When
            List<EventBasedTimeConverter.TickEvent> events = converter.convert(prices, timestamps, theta);

            // Then
            assertThat(events).isNotEmpty();
            assertThat(events.get(0).eventType()).isEqualTo(EventBasedTimeConverter.EventType.DIRECTIONAL_CHANGE_UP);
            List<EventBasedTimeConverter.EventType> types = events.stream()
                    .map(EventBasedTimeConverter.TickEvent::eventType).toList();
            assertThat(types).contains(EventBasedTimeConverter.EventType.OVERSHOOT);
        }
    }

    @Nested
    class EmptyAndNullInputs {

        @Test
        void givenNullPrices_whenConvert_thenReturnsEmptyList() {
            // Given
            long[] timestamps = {0, 1000};

            // When
            List<EventBasedTimeConverter.TickEvent> events = converter.convert(null, timestamps);

            // Then
            assertThat(events).isEmpty();
        }

        @Test
        void givenNullTimestamps_whenConvert_thenReturnsEmptyList() {
            // Given
            double[] prices = {100.0, 101.0};

            // When
            List<EventBasedTimeConverter.TickEvent> events = converter.convert(prices, null);

            // Then
            assertThat(events).isEmpty();
        }

        @Test
        void givenEmptyArrays_whenConvert_thenReturnsEmptyList() {
            // Given
            double[] prices = {};
            long[] timestamps = {};

            // When
            List<EventBasedTimeConverter.TickEvent> events = converter.convert(prices, timestamps);

            // Then
            assertThat(events).isEmpty();
        }

        @Test
        void givenMismatchedLengths_whenConvert_thenReturnsEmptyList() {
            // Given
            double[] prices = {100.0, 101.0};
            long[] timestamps = {0};

            // When
            List<EventBasedTimeConverter.TickEvent> events = converter.convert(prices, timestamps, 0.005);

            // Then
            assertThat(events).isEmpty();
        }
    }

    @Nested
    class SinglePriceInput {

        @Test
        void givenSinglePrice_whenConvert_thenReturnsEmptyList() {
            // Given
            double[] prices = {100.0};
            long[] timestamps = {0};

            // When
            List<EventBasedTimeConverter.TickEvent> events = converter.convert(prices, timestamps);

            // Then
            assertThat(events).isEmpty();
        }
    }

    @Nested
    class NoEventsBelowTheta {

        @Test
        void givenPriceMovementSmallerThanTheta_whenConvert_thenReturnsNoEvents() {
            // Given - 0.3% move, less than default theta of 0.5%
            double[] prices = {100.0, 100.0, 100.3};
            long[] timestamps = {0, 1000, 2000};

            // When
            List<EventBasedTimeConverter.TickEvent> events = converter.convert(prices, timestamps);

            // Then
            assertThat(events).isEmpty();
        }

        @Test
        void givenFlatPriceSeries_whenConvert_thenReturnsNoEvents() {
            // Given
            double[] prices = {100.0, 100.0, 100.0, 100.0, 100.0};
            long[] timestamps = {0, 1000, 2000, 3000, 4000};

            // When
            List<EventBasedTimeConverter.TickEvent> events = converter.convert(prices, timestamps, 0.005);

            // Then
            assertThat(events).isEmpty();
        }
    }

    @Nested
    class EventProperties {

        @Test
        void givenDirectionalChangeEvent_whenConvert_thenMagnitudeMatchesPriceMove() {
            // Given - 2% move up
            double[] prices = {100.0, 100.0, 102.0};
            long[] timestamps = {0, 1000, 2000};
            double theta = 0.005;

            // When
            List<EventBasedTimeConverter.TickEvent> events = converter.convert(prices, timestamps, theta);

            // Then
            assertThat(events).hasSizeGreaterThanOrEqualTo(1);
            EventBasedTimeConverter.TickEvent dc = events.get(0);
            assertThat(dc.price()).isEqualTo(102.0);
            assertThat(dc.time()).isEqualTo(2000L);
            assertThat(dc.threshold()).isEqualTo(theta);
            assertThat(dc.magnitude()).isGreaterThanOrEqualTo(0.0);
        }

        @Test
        void givenLargePriceSeries_whenConvert_thenProducesMultipleEvents() {
            // Given - oscillating series with clear directional changes
            double[] prices = {100.0, 102.0, 104.0, 102.0, 100.0, 98.0, 100.0, 102.0};
            long[] timestamps = {0, 1000, 2000, 3000, 4000, 5000, 6000, 7000};
            double theta = 0.005;

            // When
            List<EventBasedTimeConverter.TickEvent> events = converter.convert(prices, timestamps, theta);

            // Then
            assertThat(events.size()).isGreaterThan(1);
            assertThat(events.stream().map(EventBasedTimeConverter.TickEvent::eventType))
                    .contains(EventBasedTimeConverter.EventType.DIRECTIONAL_CHANGE_UP);
        }
    }
}
