package com.tickonomics.ingestion.polygon;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tickonomics.contracts.client.PolygonWsClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@ConditionalOnProperty(name = "polygon.ws.enabled", havingValue = "true", matchIfMissing = false)
public class PolygonWsConfig {

    @Bean
    public PolygonWsClient polygonWsClient(ObjectMapper objectMapper) {
        Executor asyncExecutor = new SimpleAsyncTaskExecutor("polygon-ws-");
        return new DefaultPolygonWsClient(objectMapper, asyncExecutor);
    }
}
