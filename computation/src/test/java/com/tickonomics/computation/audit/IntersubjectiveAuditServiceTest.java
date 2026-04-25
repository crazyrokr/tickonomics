package com.tickonomics.computation.audit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class IntersubjectiveAuditServiceTest {

    private IntersubjectiveAuditService service;

    @BeforeEach
    void setUp() {
        service = new IntersubjectiveAuditService();
    }

    @Test
    @DisplayName("Given a data point with 3 coding rules, when reconstructPath, then entries appear in order with correct hashes")
    void reconstructPathReturnsOrderedEntries() {
        UUID dataPointId = UUID.randomUUID();

        service.logTransformation(dataPointId, CodingRule.RAW_FETCH, "{\"rate\":5.33}", 5.33);
        service.logTransformation(dataPointId, CodingRule.GAP_FILL_LOCF, "{\"rate\":5.33}", 5.33);
        service.logTransformation(dataPointId, CodingRule.NORMALIZATION, "{\"rate\":5.33}", 0.85);

        List<AuditEntry> path = service.reconstructPath(dataPointId);

        assertEquals(3, path.size());
        assertEquals(CodingRule.RAW_FETCH.ruleId(), path.get(0).codingRule());
        assertEquals(CodingRule.GAP_FILL_LOCF.ruleId(), path.get(1).codingRule());
        assertEquals(CodingRule.NORMALIZATION.ruleId(), path.get(2).codingRule());

        for (AuditEntry entry : path) {
            assertNotNull(entry.inputHash());
            assertEquals(64, entry.inputHash().length());
            assertEquals(dataPointId, entry.dataPointId());
        }
    }

    @Test
    @DisplayName("Given GAP_FILL_LOCF step, when computeCompositeIrScore, then irScore is 0.8")
    void compositeIrScoreReflectsMinimumAcrossSteps() {
        UUID dataPointId = UUID.randomUUID();

        service.logTransformation(dataPointId, CodingRule.RAW_FETCH, "raw", 5.33);
        service.logTransformation(dataPointId, CodingRule.GAP_FILL_LOCF, "gap", 5.33);
        service.logTransformation(dataPointId, CodingRule.NORMALIZATION, "norm", 0.85);

        double irScore = service.computeCompositeIrScore(dataPointId);

        assertEquals(0.8, irScore, 0.001);
    }

    @Test
    @DisplayName("Given irScore 0.8 (below 0.9 threshold), when isActionable, then returns false")
    void belowThresholdIsNotActionable() {
        UUID dataPointId = UUID.randomUUID();

        service.logTransformation(dataPointId, CodingRule.RAW_FETCH, "raw", 5.33);
        service.logTransformation(dataPointId, CodingRule.GAP_FILL_LOCF, "gap", 5.33);

        assertFalse(service.isActionable(dataPointId));
    }

    @Test
    @DisplayName("Given all RAW_FETCH steps, when isActionable, then returns true")
    void allRawFetchIsActionable() {
        UUID dataPointId = UUID.randomUUID();

        service.logTransformation(dataPointId, CodingRule.RAW_FETCH, "raw", 5.33);
        service.logTransformation(dataPointId, CodingRule.ADAPTER_MAP, "map", 5.33);
        service.logTransformation(dataPointId, CodingRule.NORMALIZATION, "norm", 0.85);

        assertTrue(service.isActionable(dataPointId));
    }

    @Test
    @DisplayName("Given unknown data point, when reconstructPath, then returns empty list")
    void unknownDataPointReturnsEmptyPath() {
        List<AuditEntry> path = service.reconstructPath(UUID.randomUUID());
        assertTrue(path.isEmpty());
    }

    @Test
    @DisplayName("Given unknown data point, when computeCompositeIrScore, then returns 0.0")
    void unknownDataPointHasZeroIrScore() {
        assertEquals(0.0, service.computeCompositeIrScore(UUID.randomUUID()), 0.001);
    }

    @Test
    @DisplayName("Given GAP_FILL_LINEAR (irScore=0.5), when isActionable, then returns false")
    void linearInterpolationIsNotActionable() {
        UUID dataPointId = UUID.randomUUID();

        service.logTransformation(dataPointId, CodingRule.RAW_FETCH, "raw", 5.33);
        service.logTransformation(dataPointId, CodingRule.GAP_FILL_LINEAR, "gap", 5.33);

        assertFalse(service.isActionable(dataPointId));
        assertEquals(0.5, service.computeCompositeIrScore(dataPointId), 0.001);
    }

    @Test
    @DisplayName("Given same input payload, when sha256, then produces consistent hash")
    void sha256ProducesConsistentHash() {
        String hash1 = IntersubjectiveAuditService.sha256("{\"sofr\":5.33}");
        String hash2 = IntersubjectiveAuditService.sha256("{\"sofr\":5.33}");

        assertEquals(hash1, hash2);
        assertEquals(64, hash1.length());
    }

    @Test
    @DisplayName("Given different input payloads, when sha256, then produces different hashes")
    void sha256ProducesDifferentHashesForDifferentInputs() {
        String hash1 = IntersubjectiveAuditService.sha256("{\"sofr\":5.33}");
        String hash2 = IntersubjectiveAuditService.sha256("{\"sofr\":5.34}");

        assertNotEquals(hash1, hash2);
    }
}
