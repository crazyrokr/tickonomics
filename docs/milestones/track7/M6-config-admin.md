# Milestone 6: Configuration & Admin

**Status:** DONE
**Depends on:** M1 (Scaffolding, Layout, Auth, API Client)
**Estimated scope:** ~3 files

## Objective

Implement the admin configuration editor that allows live YAML/JSON editing of hot-reloadable system config with validation, diff view, and submission history.

## Components

### 6.1 Configuration Editor

**File:** `components/config/ConfigEditor.tsx`

- Live YAML/JSON editor with syntax highlighting
- Client-side validation against known config schema
- Diff view: before/after comparison showing exactly what will change
- Submit via `PUT /api/v1/config`
- Validation errors displayed inline with line numbers

### 6.2 Configuration History

**File:** `components/config/ConfigHistory.tsx`

- Chronological list of all configuration changes
- Each entry shows: timestamp, author, diff summary, and mandatory `audit_reason`
- Click to expand full diff view
- Filterable by date range and author

### 6.3 Admin Access Control

- Config editor and history only accessible to users with `ADMIN` role
- Non-admin users see a "read-only" view of current config
- Role checked from JWT claims

## Data Sources

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `GET /api/v1/config` | GET | Current system configuration |
| `PUT /api/v1/config` | PUT | Submit configuration changes |
| `GET /api/v1/config/history` | GET | Configuration change history with audit_reason |

## Acceptance Criteria

- [ ] Config editor renders current config as formatted YAML/JSON
- [ ] Syntax highlighting works for YAML and JSON modes
- [ ] Client-side validation catches malformed config before submit
- [ ] Diff view highlights additions (green) and removals (red)
- [ ] Submit sends validated config to `PUT /api/v1/config`
- [ ] Config history shows chronological list with audit_reason
- [ ] Each history entry is expandable to show full diff
- [ ] Admin-only access enforced; non-admin sees read-only view
- [ ] Unit tests for validation logic, diff computation, and role checking

## Technical Notes

- Use a code editor library (e.g., CodeMirror 6 or Monaco Editor) for the config editor
- Diff computation can use `diff` or `jsdiff` library
- The `audit_reason` field is mandatory per v5 requirements — validate it client-side before submit
- Config history may grow large — implement pagination or virtualized scrolling
