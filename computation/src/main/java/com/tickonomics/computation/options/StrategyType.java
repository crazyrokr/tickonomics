package com.tickonomics.computation.options;

public enum StrategyType {
    BULL_CALL_SPREAD("OS.01", "Eq. 17-28"),
    BEAR_PUT_SPREAD("OS.02", "Eq. 29-38"),
    LONG_STRADDLE("OS.03", "Eq. 239-246"),
    CALL_BUTTERFLY("OS.04", "Eq. 79-100"),
    PUT_BUTTERFLY("OS.05", "Eq. 101-130"),
    IRON_CONDOR("OS.06", "Eq. 179-208"),
    IRON_BUTTERFLY("OS.07", "Eq. 131-158"),
    LONG_STRANGLE("OS.08", "Eq. 253-260"),
    RATIO_SPREAD("OS.09", "Eq. 209-220"),
    CALENDAR_SPREAD("OS.10", "Eq. 221-238"),
    BOX_SPREAD("OS.11", "Eq. 247-252"),
    DIAGONAL_SPREAD("OS.12", "Sec 2.9"),
    BACKSPREAD("OS.13", "Sec 2.11"),
    VERTICAL_PUT_SPREAD("OS.14", "Eq. 49"),
    DIAGONAL_CALL_SPREAD("OS.15", "Sec 2.x"),
    COVERED_CALL("OS.16", "Sec 2.1"),
    PROTECTIVE_PUT("OS.17", "Sec 2.2"),
    COLLAR_SPREAD("OS.18", "Sec 2.3"),
    MARRIED_PUT("OS.19", "Sec 2.4"),
    SYNTHETIC_LONG("OS.20", "Sec 2.5"),
    BULL_PUT_SPREAD("OS.21", "Eq. 49-58"),
    BEAR_CALL_SPREAD("OS.22", "Eq. 39-48"),
    CALL_CONDOR("OS.23", "Eq. 159-178"),
    PUT_CONDOR("OS.24", "Sec 2.8"),
    RISK_REVERSAL("OS.25", "Sec 2.12"),
    BUTTERFLY_BACKSPREAD("OS.26", "Sec 2.13"),
    CHRISTMAS_TREE("OS.27", "Sec 2.14"),
    SEAGULL_SPREAD("OS.28", "Sec 2.15"),
    STRADDLE_SWAP("OS.29", "Sec 2.16"),
    CALENDAR_STRADDLE("OS.30", "Sec 2.17");

    private final String strategyId;
    private final String formulaRef;

    StrategyType(String strategyId, String formulaRef) {
        this.strategyId = strategyId;
        this.formulaRef = formulaRef;
    }

    public String strategyId() { return strategyId; }
    public String formulaRef() { return formulaRef; }
}
