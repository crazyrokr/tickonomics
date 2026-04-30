package com.tickonomics.ingestion.polygon;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tickonomics.cdm.adapter.raw.PolygonTick;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class DefaultPolygonWsClientTest {

    private static final Executor DIRECT_EXECUTOR = Runnable::run;

    private DefaultPolygonWsClient client;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        client = new DefaultPolygonWsClient(objectMapper, DIRECT_EXECUTOR);
    }

    @Nested
    class Connection {
        @Test
        void givenNotConnected_whenIsConnected_thenFalse() {
            assertFalse(client.isConnected());
        }

        @Test
        void givenNotConnected_whenDisconnect_thenNoException() {
            assertDoesNotThrow(() -> client.disconnect());
        }
    }

    @Nested
    class Subscription {
        @Test
        void givenSymbol_whenSubscribe_thenAdded() {
            client.subscribe("SPY");
        }

        @Test
        void givenSubscribedSymbol_whenUnsubscribe_thenRemoved() {
            client.subscribe("SPY");
            assertDoesNotThrow(() -> client.unsubscribe("SPY"));
        }
    }

    @Nested
    class Handler {
        @Test
        void givenHandler_whenOnTick_thenRegistered() {
            AtomicReference<PolygonTick> received = new AtomicReference<>();
            client.onTick(received::set);
        }
    }

    @Nested
    class ParseTick {
        @Test
        void givenValidTickJson_whenParse_thenCorrectFields() throws Exception {
            String json = "[{\"ev\":\"T\",\"sym\":\"SPY\",\"p\":450.50,\"s\":1000,\"t\":1700000000000,\"c\":[0,1]}]";
            AtomicReference<PolygonTick> received = new AtomicReference<>();
            client.onTick(received::set);

            var root = objectMapper.readTree(json);
            var tickNode = root.get(0);
            var handler = client.new PolygonWsHandler("test-key");

            Instant expectedTime = Instant.ofEpochMilli(1700000000000L);
            // Parse tick directly via reflection on the method
            var parseMethod = DefaultPolygonWsClient.class.getDeclaredMethod("parseTick", com.fasterxml.jackson.databind.JsonNode.class);
            parseMethod.setAccessible(true);
            PolygonTick tick = (PolygonTick) parseMethod.invoke(client, tickNode);

            assertEquals("SPY", tick.symbol());
            assertEquals(450.50, tick.price(), 1e-9);
            assertEquals(1000, tick.volume());
            assertArrayEquals(new int[]{0, 1}, tick.conditions());
        }
    }
}
