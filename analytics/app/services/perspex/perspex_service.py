"""Perspex service: orchestrates perspective-mismatch detection (ADR-037).

In Track A the deterministic check (``_deterministic``) is both necessary and authoritative. The LLM
agent corroborator (Track B) will be wired here later: agents run only on deterministic-flagged
candidates and can never author a signal on their own. Validation here is cheap and dependency-free.
"""

from app.services.perspex._deterministic import analyze


def analyze_perspex(
    quotes: list[dict],
    events: list[dict],
    divergence_threshold: float = 0.25,
    min_liquidity: float = 1000.0,
    min_volume: float = 500.0,
) -> dict:
    if not isinstance(quotes, list) or not isinstance(events, list):
        return {"error": "quotes and events must be lists"}
    return analyze(quotes, events, divergence_threshold, min_liquidity, min_volume)
