# Code Review: Web Layer, App Entry Point & API Contracts (Java)

**Date:** 2026-06-14  
**Component:** `web/`, `app/`, `api-contracts/`  
**Files reviewed:** 17  
**Reviewer:** Automated code review

---

## Executive Summary

The web layer is a Spring Boot 3.x application with REST controllers, WebSocket handlers, and API contract interfaces. While the code compiles and follows basic Spring conventions, it has **significant security gaps**, **poor data typing for financial values**, and **multiple stubbed endpoints that return empty responses**. The authentication system is disabled by default, and there is no global exception handling.

**Severity distribution:**
- 🔴 Critical: 5
- 🟠 High: 11
- 🟡 Medium: 14
- 🟢 Low: 4

---

## 1. Critical Findings

### 1.1 No Global Exception Handler

**File:** `web/src/main/java/com/tickonomics/web/` (absent class)  
**Severity:** 🔴 Critical

There is no `@ControllerAdvice` or `@RestControllerAdvice` class anywhere in the web module. Every controller method propagates exceptions directly to the client as HTML error pages or Spring Boot default JSON error responses.

**Impact:** Unhandled exceptions in `DemoController` or `QuantController` will expose stack traces to clients. For a financial application, this could leak internal system details (class names, SQL queries, internal IPs).

**Recommendation:** Add a class like:

```java
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handle(Exception e) {
        // Log full stack trace server-side
        // Return sanitized ProblemDetail to client
    }
}
```

### 1.2 Pervasive `Map<String, Object>` Return Types

**File:** `DemoController.java:62`, `DemoController.java:78`, `DemoController.java:98`, `DemoController.java:120`, `DemoController.java:127`, `DemoController.java:140`, `DemoController.java:153`, `DemoController.java:159`, `DemoController.java:164`, `DemoController.java:179`, `DemoController.java:185`, `DemoController.java:191`  
**Severity:** 🔴 Critical

Every controller endpoint returns `ResponseEntity<Map<String, Object>>`. This means:
- No compile-time type safety for response contracts
- No OpenAPI/Swagger schema generation for response bodies
- Clients must rely on documentation or trial-and-error to understand response shapes
- Adding/removing fields silently breaks API consumers

**Example (DemoController.java:62):**
```java
public ResponseEntity<Map<String, Object>> getPortfolioSummary() {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("balance", summary.currentBalance());  // what type?
    body.put("winRate", summary.winRate());          // what type?
    return ResponseEntity.ok(body);
}
```

`Referenced in 12 lines` — this is a systemic anti-pattern.

**Recommendation:** Create typed DTO records for every response shape:

```java
public record PortfolioSummaryResponse(
    BigDecimal balance,
    BigDecimal initialBalance,
    BigDecimal realizedPnl,
    BigDecimal unrealizedPnl,
    BigDecimal totalPnl,
    int openPositions,
    int totalTrades,
    BigDecimal winRate,
    boolean enabled
) {}
```

### 1.3 Authentication Disabled by Default

**File:** `SecurityConfig.java:20`  
**Severity:** 🔴 Critical

```java
@Value("${security.auth-disabled:false}")
private boolean authDisabled;
```

The default value in the annotation is `false`, meaning auth is enabled by default for the `SecurityFilterChain`. However, looking at `SecurityProperties.java:5-12`, the `authDisabled` boolean is also a property there. The `@Value` annotation on the field in `SecurityConfig` means the `SecurityProperties` record is **never used** by `SecurityConfig` — the two configurations are disconnected.

More critically, in `application.properties` there is likely `security.auth-disabled=true` for the demo profile (as indicated by `docker-compose.demo.yml`), meaning in practice **all endpoints are open with no authentication required**.

**Impact:** In the demo/deployed environment, any unauthenticated user can:
- View portfolio positions and trade history
- Activate/deactivate kill switches
- Close positions by posting to `/api/v1/demo/close-position/{id}`
- View all active signals and strategies

**Recommendation:**
1. Wire `SecurityProperties` into `SecurityConfig` via constructor injection instead of `@Value`
2. Never allow `auth-disabled: true` in any non-local environment
3. Add a startup warning when auth is disabled

### 1.4 `double` Used for All Monetary/Price Values

**File:** `PriceTick.java:11-12`, `SignalNotification.java:17-18`, `DemoController.java:129`, `DemoController.java:141`  
**Severity:** 🔴 Critical

Every financial value in the web layer uses primitive `double`:

```java
// PriceTick.java:11-12
public record PriceTick(String symbol, double price, double volume, ...)

// DemoController.java:129
@RequestParam double price

// DemoController.java:141
@RequestBody(required = false) Map<String, Double> prices
```

**Impact:** IEEE 754 floating-point cannot represent decimal monetary values exactly. For example, `0.1 + 0.2 != 0.3` in `double`. API responses will display values like `99.99000000000001` or `100.00999999999999` for what should be `100.00`. This is unacceptable for any financial system.

**Recommendation:**
1. Use `BigDecimal` for all monetary values in API contracts
2. Register a custom Jackson serializer/deserializer that handles `BigDecimal` as plain numbers (not `{"scale": 2, "value": 10000}`)
3. For `@RequestParam`, use `String` and parse with `new BigDecimal(value)` with validation

### 1.5 `DemoController` Has 9 Dependencies

**File:** `DemoController.java:28-58`  
**Severity:** 🔴 Critical

The `DemoController` has 9 constructor-injected dependencies:
1. `VirtualPortfolio`
2. `PaperTradingEngine` (unused field — injected but never called)
3. `SignalQualityAnalyzer`
4. `VirtualPortfolioTradeRepository`
5. `SignalLogRepository` (unused — injected but never called)
6. `KillSwitch`
7. `MarketPriceLookup`
8. `LeverageSignaler`
9. `SystemicResilienceMonitor`

This violates the Single Responsibility Principle. The controller handles portfolio management, kill switches, safe mode, leverage rotation, and signal quality — at least 5 distinct concerns.

**Recommendation:** Split into:
- `DemoPortfolioController` — portfolio, positions, trades
- `DemoRiskController` — kill switch, safe mode
- `DemoStrategyController` — signal quality, leverage rotation

---

## 2. High Severity Findings

### 2.1 Stub Endpoints Returning Empty Responses

**File:** `QuantController.java:29-52`  
**Severity:** 🟠 High

Three endpoints return hardcoded empty responses:
```java
@GetMapping("/signals/active")
public ResponseEntity<List<AlphaSignal>> getActiveSignals(...) {
    return ResponseEntity.ok(List.of());  // Always empty!
}

@GetMapping("/strategies/active")
public ResponseEntity<List<Map<String, Object>>> getActiveStrategies() {
    return ResponseEntity.ok(List.of());  // Always empty!
}

@GetMapping("/risk/evt-tail")
public ResponseEntity<Map<String, Object>> getEvtTail() {
    return ResponseEntity.ok(Map.of());   // Always empty!
}
```

If these endpoints are not yet implemented, they should return `501 Not Implemented` or be removed entirely. Returning `200 OK` with empty data misleads API consumers into thinking there are genuinely no active signals.

### 2.2 `SecurityProperties` Record Is Dead Code

**File:** `SecurityProperties.java:1-31`  
**Severity:** 🟠 High

The `SecurityProperties` record is declared with `@ConfigurationProperties(prefix = "security")` but is **never injected or used** by `SecurityConfig`. The `SecurityConfig` class uses `@Value` annotations directly instead. This means:
- The compact record constructor null-checks in `SecurityProperties` never execute
- Changing properties in YAML/properties files has no effect
- The `oauth2ClientSecret` default is never applied

**Recommendation:** Inject `SecurityProperties` via constructor in `SecurityConfig` and remove the `@Value` fields. Or delete `SecurityProperties.java`.

### 2.3 No Method-Level Authorization

**File:** `SecurityConfig.java:48-52`  
**Severity:** 🟠 High

```java
.authorizeHttpRequests(auth -> auth
    .requestMatchers("/health", "/actuator/**").permitAll()
    .requestMatchers("/oauth2/**", "/login/oauth2/code/**").permitAll()
    .requestMatchers("/api/**").authenticated()
    .anyRequest().permitAll())
```

All `/api/**` endpoints require only authentication — no role-based authorization. This means any authenticated user can:
- Activate a kill switch (`POST /api/v1/demo/kill-switch/activate`)
- Close positions (`POST /api/v1/demo/close-position/{id}`)
- View all portfolio data

For a financial system, write operations should require specific roles (e.g., `ROLE_TRADER`, `ROLE_ADMIN`).

### 2.4 `anyRequest().permitAll()` When Auth Is Enabled

**File:** `SecurityConfig.java:52`  
**Severity:** 🟠 High

When auth is enabled (authDisabled=false), the last matcher `.anyRequest().permitAll()` allows anonymous access to any endpoint that doesn't match `/api/**`. This includes the WebSocket endpoints at `/ws/**` and any future controller endpoints not yet under `/api/`.

**Recommendation:** Change to `.anyRequest().denyAll()` or `.anyRequest().authenticated()`.

### 2.5 Missing Security Headers in Auth-Disabled Path

**File:** `SecurityConfig.java:29-41`  
**Severity:** 🟠 High

When auth is disabled, the security headers are set. When auth is enabled, they're also set. So the headers themselves are fine. However, `X-Content-Type-Options: nosniff` and `X-XSS-Protection` headers are **missing** in both branches.

**Recommendation:** Add:
```java
.headers(headers -> headers
    .contentTypeOptions(cto -> cto.disable())  // implicitly sets nosniff
    ...)
```

### 2.6 No Rate Limiting

**File:** `web/src/main/java/` (absent)  
**Severity:** 🟠 High

There is no rate limiting on any endpoint. The `/api/v1/demo/kill-switch/activate` endpoint could be called repeatedly to trigger repeated portfolio liquidations. Even the `/health` endpoint has no rate limiting.

**Recommendation:** Add Spring Cloud Gateway or a `Filter`-based rate limiter, or use `Bucket4j` integration. At minimum, rate-limit the kill-switch and close-position endpoints.

### 2.7 WebSocket: All Origins Allowed

**File:** `RealtimeWebSocketConfig.java:27-28`  
**Severity:** 🟠 High

```java
registry.addHandler(priceHandler, "/ws/prices").setAllowedOriginPatterns("*");
registry.addHandler(signalHandler, "/ws/signals").setAllowedOriginPatterns("*");
```

This allows any website to open a WebSocket connection and receive real-time price and signal data (including potentially alpha signals before they're acted on). While the comment on line 8 says "production deployments should restrict the allowed origin patterns," there is no configuration property to control this, and no environment-dependent behavior.

**Recommendation:** Make origins configurable via `application.properties`:
```java
@Value("${ws.allowed-origins:http://localhost:3000}")
private String allowedOrigins;
```

### 2.8 WebSocket: No Heartbeat/Ping-Pong

**File:** `BroadcastWebSocketHandler.java:19-71`  
**Severity:** 🟠 High

The `BroadcastWebSocketHandler` has no heartbeat mechanism. If a client's network drops silently (e.g., laptop sleep), the session remains in the `sessions` map indefinitely. The `send()` method checks `session.isOpen()` before sending, but a dead TCP connection may still report as open until the OS-level TCP keepalive kicks in (typically 2+ hours).

**Recommendation:** Implement a periodic ping (every 15-30 seconds) and remove sessions that fail to respond with pong.

### 2.9 `PaperTradingEngine` Injected but Unused

**File:** `DemoController.java:31,42`  
**Severity:** 🟠 High

```java
private final PaperTradingEngine tradingEngine;  // line 31
// injected at line 42 but never referenced in any method
```

This is dead code that adds a useless dependency. If the trading engine was intended for manual trade execution endpoints, those endpoints don't exist.

### 2.10 `SignalLogRepository` Injected but Unused

**File:** `DemoController.java:34,44`  
**Severity:** 🟠 High

Same as above — injected but never called.

### 2.11 Zero Test Coverage for WebSocket Handlers

**File:** `web/src/test/java/` (absent)  
**Severity:** 🟠 High

There are no tests for:
- `BroadcastWebSocketHandler` — session management, broadcast logic, error handling
- `PriceWebSocketHandler` — message serialization
- `SignalWebSocketHandler` — message serialization
- `RealtimeWebSocketConfig` — handler registration

The existing tests (`SecurityConfigTest`, `DemoControllerTest`) cover only 2 of the 17 files.

---

## 3. Medium Severity Findings

### 3.1 `WebConfig` Is Empty

**File:** `WebConfig.java:7-8`  
**Severity:** 🟡 Medium

```java
@Configuration
public class WebConfig implements WebMvcConfigurer {
}
```

This class does nothing. Either add configuration (CORS, interceptors, formatters, message converters) or delete it.

### 3.2 `main()` Method Is Package-Private

**File:** `TickonomicsApplication.java:12`  
**Severity:** 🟡 Medium

```java
static void main(String[] args) {
```

The `main` method is package-private (no `public` modifier). While Spring Boot can still launch via its own launcher, this is non-standard and may confuse tools expecting `public static void main`. The `public` modifier was likely accidentally omitted.

### 3.3 Missing `@Valid` / `@Validated` on Request Parameters

**File:** `DemoController.java:99,127-129,141,191-192`  
**Severity:** 🟡 Medium

No validation annotations are present on any request parameters:
```java
@RequestParam double price       // no @Positive, no @DecimalMin
@RequestParam(defaultValue = "50") int limit  // no @Max(1000)
@PathVariable long positionId    // no @Positive
```

A negative price or a limit of 10,000 could cause unexpected behavior or performance issues.

### 3.4 RPC-Style URL Design

**File:** `DemoController.java:126,139,152,163,178,184,190`  
**Severity:** 🟡 Medium

URLs use RPC-style design:
- `POST /api/v1/demo/close-position/{positionId}` — action in URL
- `POST /api/v1/demo/kill-switch/activate` — action in URL
- `POST /api/v1/demo/leverage-rotation/evaluate` — action in URL

RESTful convention would be:
- `POST /api/v1/demo/positions/{positionId}/close`
- `POST /api/v1/demo/kill-switch` with body `{"active": true}`
- Or use the controller's `@RequestMapping` base path with resource-oriented sub-paths

### 3.5 No Pagination Metadata in Trade Response

**File:** `DemoController.java:97-117`  
**Severity:** 🟡 Medium

```java
public ResponseEntity<List<Map<String, Object>>> getTrades(
    @RequestParam(defaultValue = "50") int limit,
    @RequestParam(defaultValue = "0") int offset)
```

The response is a bare list with no metadata about total count, next page, or previous page. API consumers cannot know if there are more results without querying again.

**Recommendation:** Return a paginated wrapper:
```java
public record PaginatedResponse<T>(
    List<T> data,
    int limit,
    int offset,
    long total
) {}
```

### 3.6 `AnalyticsWorkerClient` Lacks Error Handling Contract

**File:** `AnalyticsWorkerClient.java:1-12`  
**Severity:** 🟡 Medium

The interface declares:
```java
Map<String, Object> sendAnalysisRequest(String function, Map<String, Object> payload);
boolean isHealthy();
```

There is no contract for what happens on failure. Does `sendAnalysisRequest` throw? Return null? Return a map with an "error" key? The `Map<String, Object>` return type means error information would be indistinguishable from successful responses by type alone.

**Recommendation:** Either declare checked exceptions or return a `Result<T, E>` type. Document the failure contract in Javadoc.

### 3.7 `EquityWsClient` Interface Has Temporal Coupling

**File:** `EquityWsClient.java:1-24`  
**Severity:** 🟡 Medium

The interface requires callers to follow a specific sequence: `connect()` → `subscribe()` → `onTrade()` → `unsubscribe()` → `disconnect()`. Calling `subscribe()` before `connect()` or `disconnect()` without `unsubscribe()` has undefined behavior. The contract should either:
- Use a builder pattern that enforces the sequence at compile time
- Document the state machine explicitly
- Make `connect()` return an `AutoCloseable` session

### 3.8 `QuantController.butterflySignal` Uses `@RequestParam` for Symbol

**File:** `QuantController.java:39-40`  
**Severity:** 🟡 Medium

```java
@PostMapping("/strategies/options/butterfly")
public ResponseEntity<AlphaSignal> computeButterflySignal(
    @RequestParam String underlying) {
```

A `POST` endpoint that takes a query parameter is unusual. POST requests should use `@RequestBody` for the payload. Query parameters on POST are not forbidden by HTTP spec but violate common REST conventions.

### 3.9 `BroadcastWebSocketHandler.send()` Synchronization on Session

**File:** `BroadcastWebSocketHandler.java:62`  
**Severity:** 🟡 Medium

```java
synchronized (session) {
    if (session.isOpen()) {
        session.sendMessage(message);
    }
}
```

Synchronizing on a `WebSocketSession` object is fragile — Spring's WebSocket implementation may internally synchronize on the same object, potentially causing deadlocks. Use `ConcurrentHashMap` iteration with snapshot semantics or a `CopyOnWriteArrayList` of sessions instead.

### 3.10 `PriceTick` and `SignalNotification` Have Inconsistent Factory Methods

**File:** `PriceTick.java:18-20`, `SignalNotification.java:19-21`  
**Severity:** 🟡 Medium

```java
// PriceTick.of() defaults volume to 0.0 — but 0 volume is not a valid trade
public static PriceTick of(String symbol, double price, Instant timestamp) {
    return new PriceTick(symbol, price, 0.0, timestamp, List.of(), "tickonomics");
}

// SignalNotification.of() passes null for strategyId
public static SignalNotification of(UUID id, String symbol, String direction) {
    return new SignalNotification(id, Instant.now(), symbol, direction, null, 0.0, 0.0);
}
```

These factory methods create objects with semantically invalid default values. A 0.0 volume trade is impossible. A `null` strategyId means the signal's origin is untraceable. Use `Optional` for truly optional fields, or provide overloaded factory methods that require all meaningful data.

### 3.11 OAuth2 JWT Configuration Is Empty

**File:** `SecurityConfig.java:53-54`  
**Severity:** 🟡 Medium

```java
.oauth2ResourceServer(oauth2 -> oauth2
    .jwt(jwt -> {}))  // Empty! No issuer URI, no claim validation
.oauth2Client(oauth2 -> {})  // Empty! No provider configuration
```

When auth is enabled, the JWT resource server accepts any JWT without validating the issuer, audience, or claims. This is effectively no authentication at all.

### 3.12 Serialization Error Handling: Exception Wrapped Incorrectly

**File:** `BroadcastWebSocketHandler.java:55-56`  
**Severity:** 🟡 Medium

```java
} catch (JsonProcessingException e) {
    throw new IllegalStateException("Failed to serialize broadcast payload", e);
}
```

A `JsonProcessingException` (serialization failure) is a data problem, not an illegal state. Wrapping it as `IllegalStateException` obscures the root cause. Use a custom `BroadcastException` or `RuntimeException` instead.

### 3.13 `CrossModuleResilienceHealthProbe` Catches `RuntimeException` Too Broadly

**File:** `CrossModuleResilienceHealthProbe.java:63,72,78`  
**Severity:** 🟡 Medium

```java
} catch (RuntimeException e) {
    return ResilienceHealthSnapshot.UNKNOWN_UTILIZATION;  // or false
}
```

Catching `RuntimeException` swallows `NullPointerException`, `IndexOutOfBoundsException`, and other programming errors that should be surfaced as bugs rather than silently converted to "unknown" health status. Catch specific exception types instead.

### 3.14 Division by Zero Risk in `overflowUtilization()`

**File:** `CrossModuleResilienceHealthProbe.java:62`  
**Severity:** 🟡 Medium

```java
return 100.0 * buffer.size() / bufferCapacity;
```

While there is a guard `bufferCapacity <= 0` on line 59, `buffer.size()` and `bufferCapacity` are both `int`, so `buffer.size() / bufferCapacity` is integer division. Then `100.0 *` is applied to the already-truncated integer result. For example, if `size=500` and `capacity=10000`, the result is `100.0 * (500/10000) = 100.0 * 0 = 0.0` instead of `5.0`.

**Fix:**
```java
return 100.0 * buffer.size() / (double) bufferCapacity;
```

---

## 4. Low Severity Findings

### 4.1 Unused Imports

The `QuantController` imports `Map` and `List` from `java.util` — both are used. No unused imports detected in this module.

### 4.2 `LinkedHashMap` Used Without Specific Ordering Requirements

**File:** `DemoController.java:64`  
**Severity:** 🟢 Low

`LinkedHashMap` preserves insertion order, but the code inserts fields in a specific order because it expects the JSON serializer to respect it. Most JSON serializers (including Jackson) do NOT guarantee field order by default. If field order matters for the API contract, use `@JsonPropertyOrder` on a DTO class instead.

### 4.3 No Correlation ID in Responses

**File:** All controllers  
**Severity:** 🟢 Low

API responses don't include a correlation ID or request ID header. For a financial system, every request should be traceable through logs. Add a `Filter` that generates/propagates `X-Request-ID`.

### 4.4 `ApplicationStartupIT` Test Location

**File:** `integration-tests/.../ApplicationStartupIT.java`  
**Severity:** 🟢 Low

The integration test verifies the Spring context loads but is in a separate module. Consider whether a simple smoke test in the `app` module would catch context-loading failures earlier in the build pipeline.

---

## Summary Table

| # | Finding | File | Severity |
|---|---------|------|----------|
| 1 | No global exception handler | web/ (absent) | 🔴 Critical |
| 2 | `Map<String, Object>` instead of typed DTOs | DemoController.java | 🔴 Critical |
| 3 | Auth disabled by default path produces open endpoints | SecurityConfig.java:28-41 | 🔴 Critical |
| 4 | `double` for monetary/price values | Multiple files | 🔴 Critical |
| 5 | 9 dependencies in DemoController | DemoController.java:28-58 | 🔴 Critical |
| 6 | Stub endpoints return hardcoded empty responses | QuantController.java:29-52 | 🟠 High |
| 7 | Dead `SecurityProperties` record | SecurityProperties.java | 🟠 High |
| 8 | No role-based authorization | SecurityConfig.java:48-52 | 🟠 High |
| 9 | `.anyRequest().permitAll()` security hole | SecurityConfig.java:52 | 🟠 High |
| 10 | Missing `X-Content-Type-Options` header | SecurityConfig.java:35-41,56-61 | 🟠 High |
| 11 | No rate limiting | web/ (absent) | 🟠 High |
| 12 | WebSocket allows all origins | RealtimeWebSocketConfig.java:27-28 | 🟠 High |
| 13 | WebSocket no heartbeat mechanism | BroadcastWebSocketHandler.java | 🟠 High |
| 14 | PaperTradingEngine injected but unused | DemoController.java:31 | 🟠 High |
| 15 | SignalLogRepository injected but unused | DemoController.java:34 | 🟠 High |
| 16 | Zero test coverage for WebSocket handlers | web/src/test/ (absent) | 🟠 High |
| 17 | Empty WebConfig class | WebConfig.java:7-8 | 🟡 Medium |
| 18 | Package-private main() method | TickonomicsApplication.java:12 | 🟡 Medium |
| 19 | Missing validation on request params | DemoController.java:99,127-129 | 🟡 Medium |
| 20 | RPC-style URL design | DemoController.java multiple | 🟡 Medium |
| 21 | No pagination metadata | DemoController.java:97-117 | 🟡 Medium |
| 22 | AnalyticsWorkerClient lacks error contract | AnalyticsWorkerClient.java | 🟡 Medium |
| 23 | EquityWsClient temporal coupling | EquityWsClient.java | 🟡 Medium |
| 24 | POST with @RequestParam | QuantController.java:39-40 | 🟡 Medium |
| 25 | synchronized on WebSocketSession | BroadcastWebSocketHandler.java:62 | 🟡 Medium |
| 26 | Invalid defaults in factory methods | PriceTick.java, SignalNotification.java | 🟡 Medium |
| 27 | Empty JWT/OAuth2 config | SecurityConfig.java:53-55 | 🟡 Medium |
| 28 | JsonProcessingException wrapped as IllegalState | BroadcastWebSocketHandler.java:55-56 | 🟡 Medium |
| 29 | Overly broad RuntimeException catch | CrossModuleResilienceHealthProbe.java:63,72,78 | 🟡 Medium |
| 30 | Integer division in utilization calc | CrossModuleResilienceHealthProbe.java:62 | 🟡 Medium |
| 31 | LinkedHashMap JSON field order not guaranteed | DemoController.java:64 | 🟢 Low |
| 32 | No correlation ID in responses | All controllers | 🟢 Low |
| 33 | Integration test in separate module | integration-tests/ | 🟢 Low |
| 34 | Missing Javadoc on public API methods | Multiple | 🟢 Low |

---

## Key Recommendations (Priority Order)

1. **Add global exception handling** before deploying anywhere
2. **Replace `Map<String, Object>`** with typed DTO records throughout
3. **Switch `double` to `BigDecimal`** for all monetary values in API contracts
4. **Remove or implement stub endpoints** in QuantController
5. **Wire `SecurityProperties`** into `SecurityConfig` properly
6. **Add role-based authorization** for write operations
7. **Configure WebSocket allowed origins** from properties, not hardcoded `*`
8. **Add heartbeat** to WebSocket handler
9. **Split `DemoController`** into focused controllers
10. **Fix integer division** in `CrossModuleResilienceHealthProbe.overflowUtilization()`
