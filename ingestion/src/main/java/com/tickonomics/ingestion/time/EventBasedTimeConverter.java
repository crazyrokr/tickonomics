package com.tickonomics.ingestion.time;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Component
public class EventBasedTimeConverter {

    private static final Logger log = LoggerFactory.getLogger(EventBasedTimeConverter.class);
    private static final double DEFAULT_THETA = 0.005;

    List<TickEvent> convert(double[] prices, long[] timestamps) {
        return convert(prices, timestamps, DEFAULT_THETA);
    }

    List<TickEvent> convert(double[] prices, long[] timestamps, double theta) {
        if (prices == null || timestamps == null || prices.length == 0 || timestamps.length == 0) {
            return Collections.emptyList();
        }
        if (prices.length != timestamps.length) {
            log.warn("Price and timestamp arrays have different lengths: {} vs {}", prices.length, timestamps.length);
            return Collections.emptyList();
        }
        if (prices.length < 2) {
            return Collections.emptyList();
        }

        List<TickEvent> events = new ArrayList<>();
        var state = new DirectionalState(prices[0], prices[1] >= prices[0]);

        for (int i = 1; i < prices.length; i++) {
            double price = prices[i];
            double change = (price - state.lastExtremum) / state.lastExtremum;

            if (!state.inOvershoot) {
                if (state.expectingUp && change >= theta) {
                    state.recordDirectionalChange(events, timestamps[i], price, change, theta,
                            EventType.DIRECTIONAL_CHANGE_UP, true);
                } else if (!state.expectingUp && change <= -theta) {
                    state.recordDirectionalChange(events, timestamps[i], price, change, theta,
                            EventType.DIRECTIONAL_CHANGE_DOWN, false);
                } else if (state.expectingUp && change <= -theta) {
                    state.recordDirectionalChange(events, timestamps[i], price, change, theta,
                            EventType.DIRECTIONAL_CHANGE_DOWN, false);
                } else if (!state.expectingUp && change >= theta) {
                    state.recordDirectionalChange(events, timestamps[i], price, change, theta,
                            EventType.DIRECTIONAL_CHANGE_UP, true);
                }

                if (price > state.lastExtremum && state.expectingUp) {
                    state.lastExtremum = price;
                } else if (price < state.lastExtremum && !state.expectingUp) {
                    state.lastExtremum = price;
                }
            } else {
                double overshootChange = (price - state.overshootStart) / state.overshootStart;

                if (state.expectingUp && overshootChange > 0) {
                    events.add(new TickEvent(
                            timestamps[i], price,
                            EventType.OVERSHOOT,
                            overshootChange, theta));
                    state.overshootStart = price;
                } else if (!state.expectingUp && overshootChange < 0) {
                    events.add(new TickEvent(
                            timestamps[i], price,
                            EventType.OVERSHOOT,
                            Math.abs(overshootChange), theta));
                    state.overshootStart = price;
                }

                if (state.expectingUp && price > state.lastExtremum) {
                    state.lastExtremum = price;
                } else if (!state.expectingUp && price < state.lastExtremum) {
                    state.lastExtremum = price;
                }

                double reversalFromExtremum = (price - state.lastExtremum) / state.lastExtremum;
                if ((state.expectingUp && reversalFromExtremum <= -theta)
                        || (!state.expectingUp && reversalFromExtremum >= theta)) {
                    state.inOvershoot = false;
                    state.lastExtremum = price;
                    state.expectingUp = !state.expectingUp;
                }
            }
        }

        log.debug("Converted {} prices into {} events with theta={}", prices.length, events.size(), theta);
        return events;
    }

    private static class DirectionalState {
        double lastExtremum;
        boolean expectingUp;
        boolean inOvershoot;
        double overshootStart;

        DirectionalState(double lastExtremum, boolean expectingUp) {
            this.lastExtremum = lastExtremum;
            this.expectingUp = expectingUp;
            this.overshootStart = lastExtremum;
        }

        void recordDirectionalChange(
                List<TickEvent> events, long timestamp, double price,
                double change, double theta, EventType eventType, boolean newExpectingUp) {
            events.add(new TickEvent(timestamp, price, eventType, Math.abs(change), theta));
            this.lastExtremum = price;
            this.inOvershoot = true;
            this.overshootStart = price;
            this.expectingUp = newExpectingUp;
        }
    }

    public enum EventType {
        DIRECTIONAL_CHANGE_UP,
        DIRECTIONAL_CHANGE_DOWN,
        OVERSHOOT
    }

    public record TickEvent(
            long time,
            double price,
            EventType eventType,
            double magnitude,
            double threshold
    ) {
    }
}
