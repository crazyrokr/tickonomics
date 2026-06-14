# Code Review: CDM (Common Data Model) Module

**Date:** 2026-06-14
**Component:** `cdm/` — Common Data Model
**Files reviewed:** 39 Java sources + 1 build.gradle
**Java Version:** 25, Spring Boot 3.5.0

---

## Executive Summary

The CDM module is a lightweight common-data-model layer that uses Java records for immutable domain objects and functional-interface adapters for data-source abstraction. Input validation in record constructors and defensive copying in `CdmTick` demonstrate good practices.

However, **3 critical issues** exist: broken `equals()` on `CdmTick`, silent PUT option misclassification, and complete data loss for factor reversal signals. Several high-severity issues involve day-count convention mismatches and financial data integrity.

**Severity distribution:**
- 🔴 Critical: 3
- 🟠 High: 4
- 🟡 Medium: 7
- 🟢 Low: 11

---

## 1. Critical Findings

### 1.1 `CdmTick.equals()` Is Broken — Reference Equality for `int[] conditions`

**File:** `cdm/src/main/java/com/tickonomics/cdm/model/CdmTick.java`
**Severity:** 🔴 Critical
**Category:** Java Best Practices / Equals Contract

Java records auto-generate `equals()` and `hashCode()` from all components. For the `int[] conditions` field, `Object.equals()` is used, which is **reference equality** — two `CdmTick` instances with identical logical conditions (`new int[]{1, 2}`) will **not** be equal. The defensive copying in the constructor and accessor actually worsens this by ensuring arrays are never the same reference.

**Impact:** Broken equals contract. Ticks in `HashSet`, `HashMap`, stream deduplication, and equality assertions produce incorrect results.

**Fix:** Override `equals`/`hashCode` using `Arrays.equals()`/`Arrays.hashCode()`, or store conditions as `List<Integer>`.

### 1.2 `YahooOptionsCdmAdapter` Silently Maps Non-"CALL" Types to PUT

**File:** `cdm/src/main/java/com/tickonomics/cdm/adapter/YahooOptionsCdmAdapter.java:20`
**Severity:** 🔴 Critical
**Category:** Financial Correctness

```java
OptionType type = "CALL".equals(raw.optionType()) ? OptionType.CALL : OptionType.PUT;
```

Any string not exactly `"CALL"` — including `"call"` (lowercase), `"Call"`, or a malformed value — is silently treated as PUT. If Yahoo's API changes its enum format, every non-CALL option is misclassified as PUT, **inverting all downstream option pricing, Greeks, and risk calculations**.

**Fix:** Use `OptionType.valueOf(raw.optionType().toUpperCase())` which throws on unknown values, or use a `switch` with explicit handling.

### 1.3 `FrenchFactorCdmAdapter` Always Sets stRev/ltRev to NaN

**File:** `cdm/src/main/java/com/tickonomics/cdm/adapter/FrenchFactorCdmAdapter.java:18`
**Severity:** 🔴 Critical
**Category:** Financial Correctness / Data Loss

The adapter unconditionally sets `stRev` and `ltRev` to `Double.NaN`. The raw `FrenchFactorRow` has no `stRev` or `ltRev` fields. This means `ST_REVERSAL` and `LT_REVERSAL` factor sets **can never carry actual data**.

**Fix:** Add `stRev` and `ltRev` fields to `FrenchFactorRow` and populate them.

---

## 2. High Severity Findings

### 2.1 TTM Uses 365.25 but Enum Says ACT/365 FIXED

**File:** `cdm/src/main/java/com/tickonomics/cdm/adapter/YahooOptionsCdmAdapter.java:49-52`
**Severity:** 🟠 High

```java
return Math.max(0, days / 365.25);  // Uses 365.25 (ACT/365L)
// But DayCountConvention.ACT_365_FIXED is passed to CdmOptionSnapshot
```

`ACT_365_FIXED` uses exactly 365 days. The computation uses 365.25. For a 1-year TTM, this produces a ~0.07% error that propagates into all option Greeks.

**Fix:** Change divisor to `365.0` or define a new `ACT_365_25` convention.

### 2.2 Inverted Bid/Ask Silently Corrected Without Logging

**File:** `cdm/src/main/java/com/tickonomics/cdm/adapter/YahooOptionsCdmAdapter.java:22-26`
**Severity:** 🟠 High

```java
if (ask < bid) {
    ask = bid;  // Silently fixes inverted spread
}
```

Inverted spreads are a strong signal of stale or corrupted market data. Silently "fixing" masks the data quality issue.

**Fix:** Log a warning. Consider rejecting the record or marking as suspicious.

### 2.3 `CdmBondSnapshot` — No Validation for Duration ≥ 0 or Convexity ≥ 0

**File:** `cdm/src/main/java/com/tickonomics/cdm/model/CdmBondSnapshot.java` (compact ctor)
**Severity:** 🟠 High

The constructor validates `yieldValue`, `dv01`, `convexity`, and `duration` for finiteness but does not check that duration is non-negative or convexity is non-negative. For standard fixed-income: modified duration > 0, convexity > 0.

**Fix:** Add `if (duration < 0)` and `if (convexity < 0)` validation.

### 2.4 Checkstyle `ignoreFailures = true` Disables Quality Enforcement

**File:** root `build.gradle:58`
**Severity:** 🟠 High

```groovy
checkstyle {
    ignoreFailures = true
}
```

The build succeeds regardless of checkstyle violations. Coding standards are advisory only.

---

## 3. Medium Severity Findings

### 3.1 No `@Nullable`/`@NonNull` Annotations Anywhere
**Category:** Java Best Practices / Null Safety
Zero nullability annotations. With Java 25 and Spring Boot 3.5, `jakarta.annotation.Nullable`/`Nonnull` are available.

### 3.2 `RateType.toInstrumentType()` Collapses All Bill Tenors to `BILL_3M`
**File:** `cdm/src/main/java/com/tickonomics/cdm/enums/RateType.java:37`
All Treasury bill tenors (1 month through 30 years) map to `InstrumentType.BILL_3M`, losing term-structure dimension. A 10-year yield is fundamentally different from a 3-month bill.

### 3.3 `InstrumentType` Enum Mixes Rate Benchmarks, Asset Classes, and Commodities
**File:** `cdm/src/main/java/com/tickonomics/cdm/enums/InstrumentType.java`
Conflates rates (SOFR, EFFR), money market (BILL_3M), equity (EQUITY), and commodities (COMMODITY_OIL) in one flat namespace.

### 3.4 VixCdmAdapter Maps VIX to `InstrumentType.EQUITY`
**File:** `cdm/src/main/java/com/tickonomics/cdm/adapter/VixCdmAdapter.java:13`
VIX is a volatility index, not an equity index. Classifying as EQUITY causes downstream analytics to apply equity-specific models to volatility data.

### 3.5 Identity Adapters Violate the Pattern's Purpose
**Files:** `NewsArticleCdmAdapter.java`, `ShillerSp500CdmAdapter.java`
Both implement `CdmAdapter<T, T>` as identity functions. If there's no transformation, there's no need for an adapter.

### 3.6 `CdmInstrumentMapper` Maps RRP/TGA/WALCL to `InstrumentType.REPO`
**File:** `cdm/src/main/java/com/tickonomics/cdm/mapper/CdmInstrumentMapper.java:26-28`
RRPONTSYD (Fed Reverse Repo Facility), WTREGEN (Treasury General Account), and WALCL (Fed Total Assets) are Fed balance-sheet metrics, not repo instruments.

### 3.7 Inconsistent Case Sensitivity in `fromFredSeries()` vs `fromNyFedRate()`
**File:** `cdm/src/main/java/com/tickonomics/cdm/mapper/CdmInstrumentMapper.java`
`fromNyFedRate()` normalizes with `toLowerCase()` but `fromFredSeries()` does not. A lowercase "effr" hits the default case and throws.

---

## 4. Low Severity Findings

1. **`computeTtmYears` uses `LocalDate.now()` instead of `Clock`** — untestable
2. **`CdmOptionSnapshot` has 16 constructor parameters** — no builder; transposed args won't be caught
3. **`CdmBondSnapshot` validates 4 fields but not `rateDelta`/`rateGamma`** — inconsistent
4. **`FredObservation`/`NyFedRateResponse` don't validate double value for NaN/infinity**
5. **`NewsArticle` doesn't null-check `summary`, `url`, or `category`**
6. **`OilPriceCdmAdapter` has instance state; other adapters are stateless** — inconsistent
7. **`AlphaVantageCdmAdapter` discards `dividendAmount` and `splitCoefficient`**
8. **All equity tick adapters pass `null` for trade conditions** — field is dead
9. **`CdmBondSnapshot.rateDelta`/`rateGamma` non-standard terminology** — should be "duration"/"convexity"
10. **`FactorReturn` stores `rmRf` and `rf`** — potential double-counting; document relationship
11. **`cdm/build.gradle` has no explicit dependencies block**

---

## 5. Positive Observations

1. **Consistent record usage** — all data holders are Java records providing immutability
2. **Defensive copying in `CdmTick`** — canonical constructor clones `int[] conditions`
3. **`@FunctionalInterface` adapter pattern** — `CdmAdapter<T,R>` is clean, minimal, composable
4. **Input validation in record constructors** — fails fast on null/range violations
5. **NaN sentinel handling** — `FactorReturn.isMissing()` handles Ken French sentinels (-99.99, -999)
6. **`CdmInstrumentRef` separation** — avoids metadata duplication in snapshots

---

## Summary Table

| # | Severity | File | Issue |
|---|----------|------|-------|
| 1.1 | 🔴 Critical | `model/CdmTick.java` | `equals()` uses reference equality for `int[]` |
| 1.2 | 🔴 Critical | `adapter/YahooOptionsCdmAdapter.java:20` | Non-"CALL" silently mapped to PUT |
| 1.3 | 🔴 Critical | `adapter/FrenchFactorCdmAdapter.java:18` | stRev/ltRev always NaN |
| 2.1 | 🟠 High | `adapter/YahooOptionsCdmAdapter.java:49-52` | TTM uses 365.25 but claims ACT/365 FIXED |
| 2.2 | 🟠 High | `adapter/YahooOptionsCdmAdapter.java:22-26` | Inverted bid/ask silently corrected |
| 2.3 | 🟠 High | `model/CdmBondSnapshot.java` | No duration/convexity ≥ 0 check |
| 2.4 | 🟠 High | root `build.gradle:58` | `checkstyle.ignoreFailures = true` |
| 3.1 | 🟡 Medium | All files | No `@Nullable`/`@NonNull` annotations |
| 3.2 | 🟡 Medium | `enums/RateType.java:37` | All bill tenors → BILL_3M |
| 3.3 | 🟡 Medium | `enums/InstrumentType.java` | Mixed semantics in flat enum |
| 3.4 | 🟡 Medium | `adapter/VixCdmAdapter.java:13` | VIX as EQUITY |
| 3.5 | 🟡 Medium | NewsArticle/ShillerSp500 adapters | Identity adapters |
| 3.6 | 🟡 Medium | `mapper/CdmInstrumentMapper.java:26-28` | RRP/TGA/WALCL as REPO |
| 3.7 | 🟡 Medium | `mapper/CdmInstrumentMapper.java` | Inconsistent case sensitivity |

---

## Recommended Fix Priority

1. Fix `CdmTick.equals()` — affects every collection/comparison on tick data
2. Fix `YahooOptionsCdmAdapter` option type mapping — can silently flip CALL to PUT
3. Fix `FrenchFactorCdmAdapter` — reversal factor data completely lost
4. Align TTM computation with declared day-count convention
5. Add logging for inverted bid/ask spreads
6. Add validation to `CdmBondSnapshot`
7. Enable null-safety annotations
8. Resolve `InstrumentType`/`RateType` hierarchy
9. Remove or refactor identity adapters
10. Address all LOW items
