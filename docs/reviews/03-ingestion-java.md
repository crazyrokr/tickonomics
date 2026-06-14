# Code Review: Ingestion Layer (Java)

**Date:** 2026-06-14
**Component:** `ingestion/` — Data Ingestion Layer
**Files reviewed:** 46 source + 4 test
**Reviewer:** Automated code review

---

## Executive Summary

The ingestion layer handles real-time and batch financial data ingestion from multiple sources (FRED, NY Fed, Finnhub, Yahoo Finance, Alpha Vantage, DataHub, Ken French Data Library). It uses Spring Boot 3 + Resilience4j 2.3.0 + Micrometer tracing. The architecture with buffers, quality guards, and bulkheads is well-conceived.

However, **5 critical data-loss bugs** were found, including a typo in an API key property name, a WebSocket that never authenticates, and a Shiller backfill path that silently discards all fetched data.

**Severity distribution:**
- 🔴 Critical: 5
- 🟠 High: 4
- 🟡 Medium: 7
- 🟢 Low: 5

---

## 1. Critical Findings

### 1.1 Data Silently Lost in `DataHubBackfillClient.backfillShiller()`

**File:** `ingestion/src/main/java/com/tickonomics/ingestion/datahub/DataHubBackfillClient.java:72-77`
**Severity:** 🔴 Critical

```java
List<ShillerSp500Row> rows = parseShillerCsv(csv);
for (ShillerSp500Row row : rows) {
    shillerAdapter.toCdm(row);   // <-- return value ignored!
}
```

The Shiller S&P 500 backfill path parses CSV rows and calls `shillerAdapter.toCdm(row)` but **never writes the result** to the database. Every other backfill path (VIX, oil WTI, oil Brent, gold) correctly flows through `writer.writeRate()`.

**Impact:** The entire Shiller S&P 500 historical dataset (CAPE ratio, long-term valuation) is fetched, parsed, and silently discarded. All downstream analytics receive no Shiller data.

**Fix:** Pipe through `writer.writeRate()` identically to the other backfill methods.

### 1.2 Finnhub WebSocket Never Authenticates — API Key Not Sent

**File:** `ingestion/src/main/java/com/tickonomics/ingestion/ws/FinnhubWsClient.java:80-94`
**Severity:** 🔴 Critical

```java
var handler = new FinnhubWsHandler(apiKey);
wsClient.execute(handler, wsUrl)  // wsUrl missing ?token= parameter
```

The `doConnect()` method connects to `wss://ws.finnhub.io` without appending the Finnhub API token as a query parameter. The Finnhub WebSocket API **requires** `wss://ws.finnhub.io?token=API_KEY`.

**Impact:** WebSocket connection rejected by server with authentication error. Real-time trade feed completely inoperative. All subscribers receive nothing.

**Fix:** Append `?token=` + URL-encoded apiKey to `wsUrl` before connecting.

### 1.3 Typo in `FinnhubEquityClient` API Key Property Name

**File:** `ingestion/src/main/java/com/tickonomics/ingestion/equity/FinnhubEquityClient.java:34`
**Severity:** 🔴 Critical

```java
@Value("${monitor.finnub.api-key:}")   // missing 'h' — reads 'finnub' not 'finnhub'
private String apiKey;
```

The `application.yml` declares `monitor.finnhub.api-key`. The `@Value` annotation reads `monitor.finnub.api-key` (missing 'h'). The property resolves to empty string.

**Impact:** All Finnhub REST calls receive no API key and get HTTP 401/403. Finnhub-specific paths (equity prices, news) are broken. The same typo exists in `FinnhubNewsClient.java:33`.

**Fix:** Change both to `@Value("${monitor.finnhub.api-key:}")`.

### 1.4 `FredClient` and `NyFedClient` Use Wrong Property Namespaces

**File:** `ingestion/src/main/java/com/tickonomics/ingestion/fred/FredClient.java:36-40`
**File:** `ingestion/src/main/java/com/tickonomics/ingestion/nyfed/NyFedClient.java:34-36`
**Severity:** 🔴 Critical

Both clients use `@Value` annotations referencing property paths not present in `application.yml`:
- `FredClient`: `${fred.api-key:}`, `${fred.base-url:...}`
- `NyFedClient`: `${nyfed.base-url:...}`, `${nyfed.poll-interval-ms:...}`

The config has **no** `fred:` or `nyfed:` top-level sections. FRED requires an API key and returns 400 errors without one.

**Impact:** FRED data ingestion (EFFR, RRPONTSYD, treasury yields) completely broken. NY Fed data may work if default URL is correct but is not externally configurable.

**Fix:** Either add `fred:` and `nyfed:` sections to `application.yml`, or align annotations to read from `monitor.*` namespace.

### 1.5 In-Memory Buffer Unbounded Growth → OOM Data Loss

**File:** `ingestion/src/main/java/com/tickonomics/ingestion/buffer/InMemoryIngestionBuffer.java:19-22`
**Severity:** 🔴 Critical

```java
@Override
public void add(T item) {
    queue.add(item);  // No capacity enforcement!
}
```

The `add()` method unconditionally calls `ConcurrentLinkedQueue.add()` with no size check. `capacity` is only checked by `isOverflowing()`, which is a passive indicator. If a DB outage causes sustained flush failures, heap grows until OOM kills the JVM.

**Impact:** All buffered (unflushed) items lost on OOM. IdempotencyGuard keys for lost items persist, preventing re-generation on restart.

**Fix:** Enforce capacity at the `InMemoryIngestionBuffer` level, or make it package-private.

---

## 2. High Severity Findings

### 2.1 AlgorithmicSanityGuard — Unsynchronized ArrayList

**File:** `ingestion/src/main/java/com/tickonomics/ingestion/guard/AlgorithmicSanityGuard.java:32`
**Severity:** 🟠 High

```java
private final List<SanityBreach> breaches = new ArrayList<>();  // NOT thread-safe
private final Map<String, PriceTracker> priceTrackers = new ConcurrentHashMap<>();  // OK
private final Map<String, RateTracker> rateTrackers = new ConcurrentHashMap<>();    // OK
```

`breaches` is accessed from multiple scheduler/handler threads but is a plain `ArrayList`. Will produce `ConcurrentModificationException` under load.

**Fix:** Use `CopyOnWriteArrayList` or synchronize access.

### 2.2 FinnhubWsClient — Duplicate Reconnect Storms

**File:** `ingestion/src/main/java/com/tickonomics/ingestion/ws/FinnhubWsClient.java:202-208`
**Severity:** 🟠 High

`scheduleReconnect()` can be called from two paths simultaneously (failure callback + connection close), scheduling duplicate reconnect tasks. `reconnectDelayMs` is `volatile` but read-and-update is not atomic.

**Impact:** Duplicate WebSocket connections, race conditions in session state, multiply-bloated scheduler.

**Fix:** Use `AtomicInteger` for `reconnectDelayMs` and synchronize `scheduleReconnect()`.

### 2.3 HttpClientConfig — No Timeouts on Default HttpClient

**File:** `ingestion/src/main/java/com/tickonomics/ingestion/config/HttpClientConfig.java:18-21`
**Severity:** 🟠 High

```java
@Bean
public HttpClient httpClient() {
    return HttpClient.newHttpClient();  // No connect timeout, no read timeout
}
```

A hanging connection blocks a thread indefinitely. Some clients set timeouts per-request, but the default is dangerous for any future usage.

**Fix:** Configure `HttpClient` with explicit `connectTimeout()` and `readTimeout()`.

### 2.4 FileOverflowBuffer — IOExceptions Silently Swallowed

**File:** `ingestion/src/main/java/com/tickonomics/ingestion/buffer/FileOverflowBuffer.java:29-41`
**Severity:** 🟠 High

```java
} catch (IOException e) {
    log.error("Failed to write overflow item...");  // Exception swallowed
}
```

On disk full or permission error, overflow items are silently lost. The caller has no way to know the write failed.

**Fix:** Propagate the exception, or add a counter/health check for write failures.

---

## 3. Medium Severity Findings

### 3.1 YahooOptionsClient — Expiry Date Timezone Bug

**File:** `ingestion/src/main/java/com/tickonomics/ingestion/options/YahooOptionsClient.java:104`
**Severity:** 🟡 Medium

```java
LocalDate expiry = LocalDate.ofEpochDay(expiryEpoch / 86400);
```

Treats the timestamp as UTC midnight. Options expire at 4:00 PM ET. Near the international date line, produces off-by-one-day errors.

**Fix:** Use `Instant.ofEpochSecond(expiryEpoch).atZone(ZoneId.of("America/New_York")).toLocalDate()`.

### 3.2 EconomicCalendarClient — Event Time Always "Now"

**File:** `ingestion/src/main/java/com/tickonomics/ingestion/external/EconomicCalendarClient.java:65`
**Severity:** 🟡 Medium

```java
LocalDate.now().atStartOfDay().toInstant(ZoneOffset.UTC)  // Always current date!
```

All economic calendar events get the current timestamp instead of their actual release date. `FredRelease` record lacks a date field.

### 3.3 FredClient — Unguarded Double.parseDouble()

**File:** `ingestion/src/main/java/com/tickonomics/ingestion/fred/FredClient.java:94`
**Severity:** 🟡 Medium

A single non-numeric observation causes the entire `fetchSeries()` to fail with `NumberFormatException`, losing all good data points for that series.

**Fix:** Wrap in try-catch and skip unparseable values with a warning.

### 3.4 IdempotencyGuard — Unbounded Memory Growth

**File:** `ingestion/src/main/java/com/tickonomics/ingestion/writer/IdempotencyGuard.java:20,39-54`
**Severity:** 🟡 Medium

`ConcurrentHashMap` of seen keys has no cap. During high throughput (100K+ trades at market open), all keys accumulate until next eviction cycle (1 hour).

**Fix:** Use a bounded cache (e.g., Caffeine with `maximumSize()`).

### 3.5 DisasterAlertClient — Non-Thread-Safe Circuit Breaker

**File:** `ingestion/src/main/java/com/tickonomics/ingestion/external/DisasterAlertClient.java:33-34`
**Severity:** 🟡 Medium

```java
private int consecutiveFailures = 0;       // Not atomic
private Instant circuitOpenUntil = Instant.MIN;  // Not volatile
```

**Fix:** Use `AtomicInteger` and `volatile`/`AtomicReference`.

### 3.6 FrenchFactorClient — ZIP Entry Assumes Single Entry

**File:** `ingestion/src/main/java/com/tickonomics/ingestion/factor/FrenchFactorClient.java:82`
**Severity:** 🟡 Medium

Calls `getNextEntry()` once without verifying entry name/extension. If ZIP structure changes, silently reads nothing or garbage.

### 3.7 DataHubBackfillClient — restClient Null

**File:** `ingestion/src/main/java/com/tickonomics/ingestion/datahub/DataHubBackfillClient.java:55-57`
**Severity:** 🟡 Medium

Constructor permits `restClient` to be null; `fetchCsv()` calls `restClient.get()` without null check.

---

## 4. Low Severity Findings

### 4.1 KernelAggregator Autocovariance Mean Computation
**File:** `kernel/KernelAggregator.java:39-51`
Mean computed over full `n` elements but covariance uses `n-lag` pairs. Use `n-lag` for mean too.

### 4.2 TieredIngestionBuffer isOverflowing() Semantics
Returns true only when BOTH memory buffer is overflowing AND file has data. Misleading after truncate.

### 4.3 No HealthIndicator Beans for Data Sources
No Spring Boot `HealthIndicator` beans for FRED, NY Fed, Finnhub WebSocket, Yahoo Finance.

### 4.4 AlphaVantageScheduler Poll Frequency
Polls every 24h with bulkhead. If initial full load exceeds 24h, subsequent runs stack up.

### 4.5 No Propagation Delay Between Fed Poll Frequencies
Both FredClient and NyFedClient poll every 5 min. For EFFR/SOFR (update once per business day), this is excessive.

---

## Summary: Data Integrity Guarantees

| Scenario | Protected? | Notes |
|---|---|---|
| Duplicate API response | ✅ Yes | IdempotencyGuard + deterministic UUID + DB ON CONFLICT |
| DB transient failure | ❌ No | Re-queued items pass IdempotencyGuard and are dropped |
| JVM crash during flush | ⚠️ Partial | In-memory buffer items lost; polling re-fetches most sources |
| JVM crash during overflow drain | ❌ No | Narrow window between replayAll() and truncate() |
| Disk full (overflow buffer) | ❌ No | IOException swallowed, data silently lost |
| Flash crash / erroneous data | ✅ Yes | AlgorithmicSanityGuard + DataQualityChecker + ProxyDivergenceGuard |
| Cross-source price divergence | ✅ Yes | ProxyDivergenceGuard monitors correlation |
| IdempotencyGuard memory exhaustion | ❌ No | Unbounded growth between eviction cycles |
| Unhealthy data feed (silent) | ❌ No | No HealthIndicator; requires log-scraping |

---

## Recommended Fix Priority

1. Fix FinnhubEquityClient/FinnhubNewsClient API key typo (CRITICAL)
2. Fix Finnhub WebSocket authentication (CRITICAL)
3. Fix Shiller backfill data discard (CRITICAL)
4. Fix FredClient/NyFedClient property namespaces (CRITICAL)
5. Add capacity enforcement to InMemoryIngestionBuffer (CRITICAL)
6. Add synchronization to AlgorithmicSanityGuard.breaches (HIGH)
7. Fix FinnhubWsClient reconnect storms (HIGH)
8. Add timeouts to HttpClientConfig (HIGH)
9. Fix FileOverflowBuffer exception swallowing (HIGH)
10. Address all MEDIUM issues in priority order
