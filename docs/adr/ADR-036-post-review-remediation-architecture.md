# ADR-036: Post-review Remediation Architecture

## Status
Accepted

## Date
2026-06-24

## Context

The `docs/reviews/` code review tracked a tail of cross-cutting findings that did not fit neatly into the
P0/P1/P2 buckets already closed by ADR-035: an eager heavy dependency in the analytics ML paths, an
untrained model producing authoritative-looking output, internet-exposed SSH across all three clouds, a
monolithic Azure environment, a monitoring gap in GCP, a mis-coupled CI credential, and the still-open
"remediation architecture" record (originally booked as ADR-021, whose number is taken by the
commons-lang3/SpotBugs pin). The "Other Findings Still Open" table in `docs/reviews/00-progress.md`
listed these as the last unresolved items. This ADR records the load-bearing decisions made to close
them, so later changes respect the same boundaries.

## Decisions

### D1 — torch is a lazy, call-site dependency (A-M1)

`regime_service` and `anomaly_service` imported `torch` at module scope, so importing either service
(and therefore FastAPI app startup) loaded the heavy torch dependency even when the torch paths were
unused. torch-backed code now lives in private submodules (`app/services/regime/_cnn_lstm.py`,
`app/services/anomaly/_autoencoder.py`); the public services import those submodules **inside** the
function bodies, after the cheap numpy-only input validation. Importing a service no longer loads torch;
torch loads only when a regime/anomaly endpoint is actually invoked. Chosen over a `try/except
ImportError` optional-dependency guard because the goal is deferred loading, not making torch optional —
the worker requires torch at runtime either way. This mirrors the pre-existing in-function import already
used for `garch_forecast`.

### D2 — the CNN-LSTM regime classifier runs a trained checkpoint, never random weights (A-M2)

`cnn_lstm_regime` constructed `_RegimeCNNLSTM` fresh on every call and ran a forward pass on
randomly-initialized weights, then returned a `regime`, `confidence`, and `transition_probability` as if
they were meaningful — the silent-noise pattern the project's trust bar exists to prevent. (The
autoencoder was **not** affected: `detect_anomalies` already loads a trained `state_dict`.) The model now
loads a committed checkpoint
(`analytics/app/services/regime/regime_cnn_lstm_state.json`, a plain-JSON `state_dict` deserialized with
the same `torch.tensor` pattern as the autoencoder) via `load_state_dict` and runs in `eval()` mode. If
the artifact is missing, corrupt, or shape-mismatched, the call returns `{"error": ...}` (the file's
existing convention) — it never falls back to random weights.

The checkpoint is produced by `analytics/scripts/train_regime_cnn_lstm.py`, a deterministic, reproducible
trainer (fixed seeds; a synthetic multi-regime returns series; labels derived from rolling-volatility
percentiles — the same boundary convention as `garch_regime`). **This is a reproducibility scaffold, not
a claimed alpha**: the point is that the model's output is now a function of fixed, inspectable weights
rather than RNG state. Re-running the script after any architecture change keeps the artifact in sync.

### D3 — no internet-exposed SSH; access via SSM / IAP / Bastion (Orig-3)

All three clouds allowed inbound SSH from `0.0.0.0/0` (AWS spot SG default, GCP firewall, Azure NSG `*`).
Removed in favour of each cloud's tunneled-access mechanism:

* **AWS** — the spot security group no longer has a port-22 ingress and the unused `aws_key_pair` is
  gone. SSM Session Manager was already wired (`AmazonSSMManagedInstanceCore` + instance profile in
  `modules/compute-spot`), so no new access infra was needed. The `hosting` SG was already HTTPS-only.
* **GCP** — the SSH firewall now allows only the IAP TCP-forwarding range `35.235.240.0/20`
  (`gcloud compute ssh ... --tunnel-through-iap`); a gated `roles/iap.tunnelResourceAccessor` binding is
  added when `iap_authorized_member` is set.
* **Azure** — the spot NSG (in the new networking module) has no inbound SSH rule; access is via Azure
  Bastion (deployed separately into an `AzureBastionSubnet`). The VM retains its `admin_ssh_key` because
  Bastion still SSHes to the guest.

Chosen over "restrict to a configured CIDR" because a sandbox should not ship an SSH surface at all;
tunneled access is the cloud-native default and removes the class of error entirely.

### D4 — Azure is modular; GCP remains a documented gap (Orig-4)

`environments/azure/main.tf` was a 315-line monolith (0 modules, 20 inline resources) versus AWS's
11-module structure. It is now a resource-group root plus seven `module` calls into new
`modules/azure-{networking,storage,database,container-registry,compute-spot,orchestrator,monitoring}`
packages, mirroring the AWS forecast subset. Resource **bodies were relocated, not redesigned**, so
behaviour is preserved; this is a structural change for consistency and maintainability.

**State caveat:** moving resources into modules changes Terraform state addresses. The Azure environment
has never been applied (no production state), so no `terraform state mv` is required. If it *had* been
applied, every relocated resource would need an explicit `state mv` / `import` before the next `apply`,
or Terraform would attempt to recreate it — operators must not blind-apply over an existing Azure state.

GCP is also monolithic but is **out of scope** for Orig-4 (an Azure-only finding) and is left inline; it
is recorded here as a known follow-up. The GCP Orig-3 and Orig-5 changes were made to its existing inline
file regardless.

### D5 — GCP monitoring reaches parity (Orig-5)

GCP had a single unattached `google_logging_metric`; it now has an email `google_monitoring_notification_channel`
(gated on `alert_email`, mirroring AWS's SNS subscription) and two `google_monitoring_alert_policy`
resources (spot preemption on the logging metric, high CPU on the forecast VM), both wired to the channel.
This matches AWS (`modules/monitoring`: SNS + 2 alarms) and Azure (2 alerts). Chosen over a full
Cloud Monitoring dashboard to match the existing alerting parity rather than introduce a new surface.

### D6 — ACR login credentials are decoupled (I-L2)

`forecast-deploy.yml` used `secrets.ACR_NAME` (the registry hostname) as the `username` for
`docker/login-action`. The username now reads a dedicated `secrets.ACR_USERNAME`, with `ACR_NAME`
retained for the hostname and `ACR_PASSWORD` for the password. The ACR has `admin_enabled = true`, so the
admin username happens to equal the registry name today, but the decoupling keeps a future move to a
service principal (username = clientId) from silently breaking. Credential types are documented in
`docs/runbook-forecast-deployment.md`.

### D7 — cross-cutting static-analysis posture (the original "remediation ADR" remit)

This ADR also inherits the open "post-review remediation architecture" record, scoped to the
cross-cutting quality workstreams that span many files:

* **Record hygiene / `EI_EXPOSE_REP`** — the SpotBugs advisory surfaces mutable-field exposures; the
  project's record-first DTO/entity convention (ADR-021's neighbourhood) is the mitigation. Full
  enforcement (advisory → build-failing) remains a deliberate, separate decision, not taken here, because
  the codebase still has legitimate array-backed records (e.g. `CdmTick`) where `EI_EXPOSE_REP` is a
  false positive until the accessor returns a defensive copy — a sweep, not a toggle.
* **`BigDecimal` for money** is ADR-033's scope; this ADR does not duplicate it.
* **Static analysis stays advisory** (`ignoreFailures`/`continue-on-error`) until the EI_EXPOSE_REP and
  Checkstyle sweeps land; flipping the gate prematurely would fail the build on known, triaged findings.

## Consequences

* Importing `regime_service`/`anomaly_service` is now cheap and torch-free; the torch load cost moves to
  first invocation of a regime/anomaly endpoint.
* `cnn_lstm_regime` output is deterministic and checkpoint-derived; a missing/corrupt artifact is a
  visible error, not silent noise. Re-running the trainer regenerates the committed artifact.
* No cloud ships an internet-open SSH port; operators must use SSM/IAP/Bastion (and set
  `iap_authorized_member` for GCP IAP).
* Azure is structurally consistent with AWS; an existing Azure state would need `state mv` (none exists
  today). GCP monolith is a known follow-up.
* GCP now alerts on preemption and high CPU like the other clouds.
* ACR CI login requires `ACR_USERNAME` (and `ACR_NAME`, `ACR_PASSWORD`) as distinct repository secrets.
* Static analysis remains advisory; the enforcement gate is deferred to a dedicated sweep.

## Related

* [ADR-021](ADR-021-commons-lang3-spotbugs-bcel.md) — SpotBugs/BCEL pin (the taken 021 slot).
* [ADR-033](ADR-033-money-as-bigdecimal.md) — money-as-`BigDecimal` (money precision scope, not duplicated here).
* [ADR-035](ADR-035-p2-remediation-decisions.md) — P2 remediation decisions (D6 unimplemented→501, D7 shared stats, etc.).
