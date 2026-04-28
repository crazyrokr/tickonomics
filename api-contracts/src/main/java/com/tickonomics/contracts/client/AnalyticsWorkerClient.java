package com.tickonomics.contracts.client;

import java.util.Map;

public interface AnalyticsWorkerClient {

    Map<String, Object> sendAnalysisRequest(String function, Map<String, Object> payload);

    byte[] sendArrowRequest(byte[] arrowPayload);

    boolean isHealthy();
}
