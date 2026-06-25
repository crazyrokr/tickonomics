"""Deterministic perspective-mismatch detection (ADR-037, Track A).

This module is the *hard validation* core that the paper (PolyGnosis 2.0) says agents cannot be
allowed to talk their way around. It is deliberately LLM-free and fully reproducible: given recent
prediction-market quotes and OSINT news events, it computes a numeric divergence between the
market-implied probability and a news-tone-derived probability and emits a mismatch only when the
divergence exceeds a threshold for an equity-relevant market with adequate liquidity and supporting news.

Ticker resolution uses cashtag extraction (``$AAPL``); markets without a cashtag are treated as
non-equity and skipped (ADR-022 T4). This is a known heuristic — see ADR-037 open follow-ups.
"""

import math
import re

_CASHTAG = re.compile(r"\$([A-Z]{1,6})")
_WORD = re.compile(r"[a-z]{3,}")
_TONE_SCALE = 3.0


def extract_tickers(question: str) -> list[str]:
    if not question:
        return []
    seen: dict[str, None] = {}
    for match in _CASHTAG.findall(question):
        seen.setdefault(match, None)
    return list(seen.keys())


def _tokens(text: str) -> set[str]:
    if not text:
        return set()
    return set(_WORD.findall(text.lower()))


def tone_to_probability(tone: float) -> float:
    """Maps a GDELT average tone (roughly [-15, +15]) to a [0, 1] probability of the yes outcome."""
    return 1.0 / (1.0 + math.exp(-tone / _TONE_SCALE))


def _relevance_weighted_tone(question: str, events: list[dict]) -> tuple[float, list[str]]:
    """Returns (weighted-mean tone, supporting event ids) for events topically related to the question."""
    question_tokens = _tokens(question)
    if not question_tokens:
        return 0.0, []

    weighted_sum = 0.0
    weight_total = 0.0
    supporting: list[str] = []
    for event in events:
        overlap = len(question_tokens & _tokens(event.get("headline", "")))
        if overlap == 0:
            continue
        weight = float(overlap)
        weighted_sum += float(event.get("avg_tone", 0.0)) * weight
        weight_total += weight
        supporting.append(event.get("event_id", ""))

    if weight_total == 0.0:
        return 0.0, []
    return weighted_sum / weight_total, supporting


def evaluate_market(quote: dict, events: list[dict], threshold: float) -> dict:
    """Evaluates one quote. Returns a verdict dict with ``emit`` (True only for a real mismatch) and a
    ``reason`` (``no_ticker`` | ``no_news`` | ``aligned`` | ``mismatch``) plus mismatch fields when emitted."""
    question = quote.get("question", "")
    tickers = extract_tickers(question)
    if not tickers:
        return {"emit": False, "reason": "no_ticker"}

    tone, supporting = _relevance_weighted_tone(question, events)
    if not supporting:
        return {"emit": False, "reason": "no_news"}

    market_prob = float(quote.get("outcome_yes_price", 0.5))
    news_prob = tone_to_probability(tone)
    divergence = abs(market_prob - news_prob)
    if divergence < threshold:
        return {"emit": False, "reason": "aligned", "divergence": divergence}

    return {
        "emit": True,
        "reason": "mismatch",
        "market_id": quote.get("market_id", ""),
        "question": question,
        "tickers": tickers,
        "market_probability": market_prob,
        "news_probability": news_prob,
        "divergence": divergence,
        "direction": "bullish" if news_prob > market_prob else "bearish",
        "confidence": min(1.0, divergence / 0.5),
        "supporting_event_ids": supporting,
    }


def analyze(
    quotes: list[dict],
    events: list[dict],
    threshold: float = 0.25,
    min_liquidity: float = 1000.0,
    min_volume: float = 500.0,
) -> dict:
    """Runs the deterministic mismatch evaluation across all quotes. Counts every skip reason so the
    caller can observe false-positive guards firing (thin markets, non-equity, no supporting news, aligned)."""
    mismatches: list[dict] = []
    skipped_thin = 0
    counts: dict[str, int] = {"no_ticker": 0, "no_news": 0, "aligned": 0}

    deduped_events = _dedupe_events(events)

    for quote in quotes:
        liquidity = float(quote.get("liquidity", 0.0))
        volume = float(quote.get("volume", 0.0))
        if liquidity < min_liquidity or volume < min_volume:
            skipped_thin += 1
            continue

        verdict = evaluate_market(quote, deduped_events, threshold)
        if verdict.get("emit"):
            mismatches.append(verdict)
        else:
            counts[verdict.get("reason", "aligned")] = counts.get(verdict.get("reason", "aligned"), 0) + 1

    return {
        "mismatches": mismatches,
        "evaluated_markets": len(quotes),
        "skipped_thin": skipped_thin,
        "skipped_no_ticker": counts["no_ticker"],
        "skipped_no_news": counts["no_news"],
        "skipped_aligned": counts["aligned"],
    }


def _dedupe_events(events: list[dict]) -> list[dict]:
    by_id: dict[str, dict] = {}
    for event in events:
        event_id = event.get("event_id", "")
        if event_id and event_id not in by_id:
            by_id[event_id] = event
    return list(by_id.values()) or list(events)
