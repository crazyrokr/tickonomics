package com.tickonomics.computation.kpi;

import com.tickonomics.persistence.entity.RateSnapshot;
import com.tickonomics.persistence.entity.TickData;
import com.tickonomics.persistence.repository.RateSnapshotRepository;
import com.tickonomics.persistence.repository.TickDataRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IntradayProxyServiceTest {

    @Mock
    private RateSnapshotRepository rateRepository;
    @Mock
    private TickDataRepository tickRepository;

    private IntradayProxyService service;

    @BeforeEach
    void setUp() {
        service = new IntradayProxyService(rateRepository, tickRepository);
    }

    private List<TickData> generateTicks(String symbol, double basePrice, int count) {
        var now = Instant.now();
        var ticks = new ArrayList<TickData>(count);
        for (int i = 0; i < count; i++) {
            ticks.add(new TickData(
                    now.minusSeconds((long) (count - i) * 60),
                    symbol,
                    basePrice + i * 0.001,
                    100,
                    new int[]{}
            ));
        }
        return ticks;
    }

    private List<RateSnapshot> generateRates(String rateType, double baseValue, int count) {
        var now = Instant.now();
        var rates = new ArrayList<RateSnapshot>(count);
        for (int i = 0; i < count; i++) {
            rates.add(new RateSnapshot(
                    now.minusSeconds((long) (count - i) * 86400),
                    rateType,
                    baseValue + i * 0.001,
                    "TEST",
                    null,
                    null
            ));
        }
        return rates;
    }

    @Nested
    class CheckProxyQuality {
        @Test
        void givenAlignedData_whenCheck_thenNormalStatus() {
            when(tickRepository.findBySymbolAndTimeBetween(eq("SOFR_TICK"), any(), any()))
                    .thenReturn(generateTicks("SOFR_TICK", 4.30, 50));
            when(rateRepository.findByRateTypeAndTimeBetween(eq("SOFR"), any(), any()))
                    .thenReturn(generateRates("SOFR", 4.30, 20));

            var result = service.checkProxyQuality("SOFR_TICK", "SOFR", 5);
            assertEquals(IntradayProxyService.ProxyStatus.NORMAL, result.status());
            assertTrue(result.divergenceBps() < 50);
            assertEquals(50, result.tickCount());
        }

        @Test
        void givenDivergentData_whenCheck_thenElevatedStatus() {
            when(tickRepository.findBySymbolAndTimeBetween(eq("TBILL"), any(), any()))
                    .thenReturn(generateTicks("TBILL", 4.325, 50));
            when(rateRepository.findByRateTypeAndTimeBetween(eq("SOFR"), any(), any()))
                    .thenReturn(generateRates("SOFR", 4.30, 20));

            var result = service.checkProxyQuality("TBILL", "SOFR", 5);
            assertEquals(IntradayProxyService.ProxyStatus.ELEVATED, result.status());
            assertTrue(result.divergenceBps() > 50);
            assertTrue(result.divergenceBps() <= 100);
        }

        @Test
        void givenHighlyDivergentData_whenCheck_thenDislocatedStatus() {
            when(tickRepository.findBySymbolAndTimeBetween(eq("TBILL"), any(), any()))
                    .thenReturn(generateTicks("TBILL", 5.30, 50));
            when(rateRepository.findByRateTypeAndTimeBetween(eq("SOFR"), any(), any()))
                    .thenReturn(generateRates("SOFR", 4.30, 20));

            var result = service.checkProxyQuality("TBILL", "SOFR", 5);
            assertEquals(IntradayProxyService.ProxyStatus.DISLOCATED, result.status());
            assertTrue(result.divergenceBps() > 100);
        }
    }

    @Nested
    class EdgeCases {
        @Test
        void givenNoTickData_whenCheck_thenInsufficientData() {
            when(tickRepository.findBySymbolAndTimeBetween(anyString(), any(), any()))
                    .thenReturn(List.of());
            when(rateRepository.findByRateTypeAndTimeBetween(anyString(), any(), any()))
                    .thenReturn(generateRates("SOFR", 4.30, 5));

            var result = service.checkProxyQuality("MISSING", "SOFR", 5);
            assertEquals(IntradayProxyService.ProxyStatus.INSUFFICIENT_DATA, result.status());
        }

        @Test
        void givenNoRateData_whenCheck_thenInsufficientData() {
            when(tickRepository.findBySymbolAndTimeBetween(anyString(), any(), any()))
                    .thenReturn(generateTicks("SOFR_TICK", 4.30, 10));
            when(rateRepository.findByRateTypeAndTimeBetween(anyString(), any(), any()))
                    .thenReturn(List.of());

            var result = service.checkProxyQuality("SOFR_TICK", "MISSING", 5);
            assertEquals(IntradayProxyService.ProxyStatus.INSUFFICIENT_DATA, result.status());
        }
    }
}
