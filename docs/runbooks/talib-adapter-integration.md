# TA-Lib Adapter Integration

## Overview

The computation engine uses TA-Lib for technical indicator calculations (RSI, MACD, Bollinger Bands, Aroon, etc.). TA-Lib is compiled as a native shared library and accessed via a JNA wrapper. The adapter class is `com.tickonomics.computation.talib.TalibAdapter`.

## Build the Native Library

The native `.so` is built at build time using the Gradle task:

```bash
./gradlew :computation:buildTalibNative
```

This produces the shared library under `native-libs/ta-lib-jna/build/`.

## Verify the Library is Loadable

The `TalibNativeLoader` class handles discovery and loading. Verify it works:

```bash
./gradlew :computation:test --tests "com.tickonomics.computation.talib.TalibNativeLoaderTest"
```

Alternatively, run a health check from the application:

```java
TalibAdapter adapter = new TalibAdapter();
boolean healthy = adapter.healthCheck();
// healthy == true means all native functions are callable
```

## Environment Configuration

The TA-Lib `.so` must be discoverable at runtime. Two options:

1. **java.library.path** -- add the directory containing `libta_lib.so` to the JVM argument:
   ```
   -Djava.library.path=/opt/tickonomics/native-libs
   ```

2. **JNA auto-loading** -- the `TalibNativeLoader` searches common paths. Ensure the `.so` is in one of:
   - `/usr/lib/`
   - `/usr/local/lib/`
   - The directory specified by `jna.library.path`

## Supported Functions

| Function    | Description            |
|-------------|------------------------|
| RSI         | Relative Strength Index |
| MACD        | Moving Average Convergence Divergence |
| BBANDS      | Bollinger Bands        |
| AROON       | Aroon Up/Down          |
| SMA         | Simple Moving Average  |
| EMA         | Exponential Moving Average |
| ATR         | Average True Range     |
| STOCH       | Stochastic Oscillator  |

## Troubleshooting

### "Unable to load TA-Lib native library"

1. Confirm the `.so` exists:
   ```bash
   find / -name "libta_lib.so" 2>/dev/null
   ```
2. If missing, rebuild:
   ```bash
   ./gradlew :computation:buildTalibNative
   ```
3. Ensure the path is in `java.library.path` or `jna.library.path`.

### UnsatisfiedLinkError at Runtime

The `.so` is present but the architecture is wrong. Verify:
```bash
file /path/to/libta_lib.so
# Should match your JVM architecture (e.g., ELF 64-bit x86-64)
```

### Segfault During Calculation

Input array length may be too short for the indicator's lookback period. Check that the input data has at least `lookbackPeriod + 1` elements before calling the adapter.

## Running Integration Tests

```bash
./gradlew :computation:test --tests "com.tickonomics.computation.talib.*"
```
