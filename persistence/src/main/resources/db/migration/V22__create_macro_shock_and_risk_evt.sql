CREATE TABLE macro_shock_irfs
(
    time            TIMESTAMPTZ      NOT NULL,
    shock_source    TEXT             NOT NULL,
    target_kpi      TEXT             NOT NULL,
    horizon_days    INT              NOT NULL,
    response_path   DOUBLE PRECISION[],
    confidence_high DOUBLE PRECISION[],
    confidence_low  DOUBLE PRECISION[],
    magnitude_std   DOUBLE PRECISION NOT NULL,
    PRIMARY KEY (time, shock_source, target_kpi)
);
SELECT create_hypertable('macro_shock_irfs', 'time', chunk_time_interval => INTERVAL '30 days');

CREATE TABLE risk_evt_parameters
(
    time         TIMESTAMPTZ      NOT NULL,
    symbol       TEXT             NOT NULL,
    shape_xi     DOUBLE PRECISION NOT NULL,
    scale_beta   DOUBLE PRECISION NOT NULL,
    threshold_u  DOUBLE PRECISION NOT NULL,
    tail_var_99  DOUBLE PRECISION NOT NULL,
    tail_var_999 DOUBLE PRECISION NOT NULL,
    PRIMARY KEY (time, symbol)
);
SELECT create_hypertable('risk_evt_parameters', 'time', chunk_time_interval => INTERVAL '30 days');
