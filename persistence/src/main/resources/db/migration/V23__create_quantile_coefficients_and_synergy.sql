CREATE TABLE quantile_coefficients (
    time            TIMESTAMPTZ NOT NULL,
    kpi_name        TEXT NOT NULL,
    quantile        DOUBLE PRECISION NOT NULL,
    coefficients    DOUBLE PRECISION[],
    pseudo_r2       DOUBLE PRECISION,
    PRIMARY KEY (time, kpi_name, quantile)
);
SELECT create_hypertable('quantile_coefficients', 'time', chunk_time_interval => INTERVAL '30 days');

CREATE TABLE synergy_entropy_matrix (
    time            TIMESTAMPTZ NOT NULL,
    source_a        TEXT NOT NULL,
    source_b        TEXT NOT NULL,
    entropy_bits    DOUBLE PRECISION NOT NULL,
    p_value         DOUBLE PRECISION NOT NULL,
    PRIMARY KEY (time, source_a, source_b)
);
SELECT create_hypertable('synergy_entropy_matrix', 'time', chunk_time_interval => INTERVAL '30 days');
