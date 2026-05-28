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
        double lastExtremum = prices[0];
        boolean expectingUp = prices[1] >= prices[0];
        boolean inOvershoot = false;
        double overshootStart = prices[0];

        for (int i = 1; i < prices.length; i++) {
            double price = prices[i];
            double change = (price - lastExtremum) / lastExtremum;

            if (!inOvershoot) {
                if (expectingUp && change >= theta) {
                    events.add(new TickEvent(
                            timestamps[i], price,
                            EventType.DIRECTIONAL_CHANGE_UP,
                            Math.abs(change), theta));
                    lastExtremum = price;
                    inOvershoot = true;
                    overshootStart = price;
                    expectingUp = true;
                } else if (!expectingUp && change <= -theta) {
                    events.add(new TickEvent(
                            timestamps[i], price,
                            EventType.DIRECTIONAL_CHANGE_DOWN,
                            Math.abs(change), theta));
                    lastExtremum = price;
                    inOvershoot = true;
                    overshootStart = price;
                    expectingUp = false;
                } else if (expectingUp && change <= -theta) {
                    events.add(new TickEvent(
                            timestamps[i], price,
                            EventType.DIRECTIONAL_CHANGE_DOWN,
                            Math.abs(change), theta));
                    lastExtremum = price;
                    inOvershoot = true;
                    overshootStart = price;
                    expectingUp = false;
                } else if (!expectingUp && change >= theta) {
                    events.add(new TickEvent(
                            timestamps[i], price,
                            EventType.DIRECTIONAL_CHANGE_UP,
                            Math.abs(change), theta));
                    lastExtremum = price;
                    inOvershoot = true;
                    overshootStart = price;
                    expectingUp = true;
                }

                if (price > lastExtremum && expectingUp) {
                    lastExtremum = price;
                } else if (price < lastExtremum && !expectingUp) {
                    lastExtremum = price;
                }
            } else {
                double overshootChange = (price - overshootStart) / overshootStart;

                if (expectingUp && overshootChange > 0) {
                    events.add(new TickEvent(
                            timestamps[i], price,
                            EventType.OVERSHOOT,
                            overshootChange, theta));
                    overshootStart = price;
                } else if (!expectingUp && overshootChange < 0) {
                    events.add(new TickEvent(
                            timestamps[i], price,
                            EventType.OVERSHOOT,
                            Math.abs(overshootChange), theta));
                    overshootStart = price;
                }

                if (expectingUp && price > lastExtremum) {
                    lastExtremum = price;
                } else if (!expectingUp && price < lastExtremum) {
                    lastExtremum = price;
                }

                double reversalFromExtremum = (price - lastExtremum) / lastExtremum;
                if ((expectingUp && reversalFromExtremum <= -theta)
                        || (!expectingUp && reversalFromExtremum >= theta)) {
                    inOvershoot = false;
                    lastExtremum = price;
                    expectingUp = !expectingUp;
                }
            }
        }

        log.debug("Converted {} prices into {} events with theta={}", prices.length, events.size(), theta);
        return events;
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
