-- V28: Create evt_risk_metrics view over risk_evt_parameters.
-- The plan specified an evt_risk_metrics hypertable; V22 created risk_evt_parameters
-- with the same schema plus an extra tail_var_999 column. This view aligns the name
-- with the plan so that RiskPremiumResidualMonitor and EVT-based services can query
-- evt_risk_metrics directly.

CREATE VIEW evt_risk_metrics AS
SELECT time,
       symbol,
       shape_xi,
       scale_beta,
       threshold_u,
       tail_var_99
FROM risk_evt_parameters;

-- Grant the same permissions as the underlying table
GRANT SELECT ON evt_risk_metrics TO CURRENT_USER;
