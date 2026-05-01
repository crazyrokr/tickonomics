-- Weight optimization runs, active weights snapshots, and liquidity stress test results

CREATE TABLE optimization_runs
(
    id            BIGSERIAL PRIMARY KEY,
    started_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at  TIMESTAMPTZ,
    method        TEXT        NOT NULL,
    status        TEXT        NOT NULL DEFAULT 'RUNNING',
    input_params  JSONB       NOT NULL,
    output_params JSONB,
    fitness_score DOUBLE PRECISION,
    iterations    INTEGER,
    metadata      JSONB
);

CREATE TABLE active_weights
(
    id                BIGSERIAL PRIMARY KEY,
    applied_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    weights           JSONB       NOT NULL,
    source            TEXT        NOT NULL,
    backtest_sharpe   DOUBLE PRECISION,
    validation_passed BOOLEAN              DEFAULT FALSE
);

CREATE TABLE liquidity_stress_results
(
    id                BIGSERIAL PRIMARY KEY,
    run_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    scenario          TEXT        NOT NULL,
    impact_metrics    JSONB       NOT NULL,
    strategy_survived BOOLEAN
);
