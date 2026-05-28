package com.tickonomics.computation.kpi;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class StrategicRunService {

    private static final Logger log = LoggerFactory.getLogger(StrategicRunService.class);
    private static final long GAP_THRESHOLD_SECONDS = 5;

    public record TradeRecord(Instant time, String side, double quantity, double price) {
    }

    public record StrategicRun(String direction, int childOrderCount, Instant startTime,
                               Instant endTime, double totalVolume, double impactBps) {
    }

    public record RunTransition(int passiveToAggressive, int aggressiveToPassive, int sameSide) {
    }

    public record RunAnalysis(List<StrategicRun> runs, RunTransition transitions) {
    }

    public RunAnalysis analyzeRuns(List<TradeRecord> trades) {
        if (trades == null || trades.isEmpty()) {
            log.debug("Empty trades list, returning empty analysis");
            return new RunAnalysis(List.of(), new RunTransition(0, 0, 0));
        }

        List<StrategicRun> runs = buildRuns(trades);
        RunTransition transitions = computeTransitions(runs);

        log.debug("Analyzed {} trades into {} runs", trades.size(), runs.size());
        return new RunAnalysis(runs, transitions);
    }

    List<StrategicRun> buildRuns(List<TradeRecord> trades) {
        List<StrategicRun> runs = new ArrayList<>();
        if (trades.isEmpty()) {
            return runs;
        }

        List<TradeRecord> currentBatch = new ArrayList<>();
        currentBatch.add(trades.get(0));

        for (int i = 1; i < trades.size(); i++) {
            TradeRecord prev = trades.get(i - 1);
            TradeRecord curr = trades.get(i);

            boolean sideChanged = !curr.side().equals(prev.side());
            boolean gapExceeded = curr.time().getEpochSecond() - prev.time().getEpochSecond() > GAP_THRESHOLD_SECONDS;

            if (sideChanged || gapExceeded) {
                runs.add(buildRun(currentBatch));
                currentBatch = new ArrayList<>();
            }
            currentBatch.add(curr);
        }

        if (!currentBatch.isEmpty()) {
            runs.add(buildRun(currentBatch));
        }

        return runs;
    }

    StrategicRun buildRun(List<TradeRecord> batch) {
        if (batch.isEmpty()) {
            return null;
        }

        String direction = batch.get(0).side();
        int childOrderCount = batch.size();
        Instant startTime = batch.get(0).time();
        Instant endTime = batch.get(batch.size() - 1).time();
        double totalVolume = batch.stream().mapToDouble(TradeRecord::quantity).sum();
        double impactBps = computeImpactBps(batch, direction);

        return new StrategicRun(direction, childOrderCount, startTime, endTime, totalVolume, impactBps);
    }

    double computeImpactBps(List<TradeRecord> batch, String direction) {
        if (batch.isEmpty()) {
            return 0.0;
        }

        double firstPrice = batch.get(0).price();
        double lastPrice = batch.get(batch.size() - 1).price();

        if (firstPrice == 0.0) {
            return 0.0;
        }

        if ("BUY".equals(direction)) {
            return (lastPrice - firstPrice) / firstPrice * 10000.0;
        } else {
            return (firstPrice - lastPrice) / firstPrice * 10000.0;
        }
    }

    RunTransition computeTransitions(List<StrategicRun> runs) {
        int passiveToAggressive = 0;
        int aggressiveToPassive = 0;
        int sameSide = 0;

        for (int i = 1; i < runs.size(); i++) {
            String prev = runs.get(i - 1).direction();
            String curr = runs.get(i).direction();

            if (prev.equals(curr)) {
                sameSide++;
            } else if ("BUY".equals(prev) && "SELL".equals(curr)) {
                passiveToAggressive++;
            } else {
                aggressiveToPassive++;
            }
        }

        return new RunTransition(passiveToAggressive, aggressiveToPassive, sameSide);
    }
}
