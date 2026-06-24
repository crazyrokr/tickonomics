from app.services.perspex._deterministic import (
    analyze,
    evaluate_market,
    extract_tickers,
    tone_to_probability,
)


def _quote(price=0.85, question="Will $AAPL beat earnings?", liquidity=50000.0, volume=10000.0):
    return {
        "market_id": "m1",
        "question": question,
        "outcome_yes_price": price,
        "volume": volume,
        "liquidity": liquidity,
        "time": "2026-06-24T12:00:00Z",
    }


def _event(event_id="e1", headline="Apple earnings outlook deteriorates sharply", tone=-9.0):
    return {"event_id": event_id, "headline": headline, "avg_tone": tone, "time": "2026-06-24T12:00:00Z"}


def test_tone_to_probability_is_monotonic_and_centered():
    """Given GDELT tones, when mapped, then neutral tone is 0.5 and the map is monotonic increasing."""
    # Given / When / Then
    assert tone_to_probability(0.0) == 0.5
    assert tone_to_probability(9.0) > tone_to_probability(0.0) > tone_to_probability(-9.0)
    assert 0.0 <= tone_to_probability(-15.0) < tone_to_probability(15.0) <= 1.0


def test_extract_tickers_finds_cashtags_only():
    """Given a market question, when extracting tickers, then only cashtags are returned (equity filter)."""
    # Given / When / Then
    assert extract_tickers("Will $AAPL beat $MSFT earnings?") == ["AAPL", "MSFT"]
    assert extract_tickers("Will the Fed cut rates?") == []
    assert extract_tickers("") == []


def test_analyze_emits_mismatch_when_market_and_news_diverge():
    """Given a market priced 0.85 yes but strongly negative news, when analyzed, then a bearish mismatch is emitted."""
    # Given
    quotes = [_quote(price=0.85)]
    events = [_event(tone=-9.0)]

    # When
    result = analyze(quotes, events, threshold=0.25)

    # Then
    assert len(result["mismatches"]) == 1
    mismatch = result["mismatches"][0]
    assert mismatch["tickers"] == ["AAPL"]
    assert mismatch["direction"] == "bearish"
    assert mismatch["divergence"] > 0.25
    assert mismatch["news_probability"] < mismatch["market_probability"]
    assert mismatch["supporting_event_ids"] == ["e1"]


def test_analyze_emits_nothing_when_market_and_news_aligned():
    """Given a market and news that agree, when analyzed, then no mismatch and the market is counted aligned."""
    # Given
    quotes = [_quote(price=0.5)]
    events = [_event(tone=0.0)]

    # When
    result = analyze(quotes, events, threshold=0.25)

    # Then
    assert result["mismatches"] == []
    assert result["skipped_aligned"] == 1


def test_analyze_skips_thin_markets():
    """Given a market below the liquidity/volume floor, when analyzed, then it is skipped as thin."""
    # Given
    quotes = [_quote(liquidity=100.0, volume=10.0)]
    events = [_event()]

    # When
    result = analyze(quotes, events, threshold=0.25)

    # Then
    assert result["mismatches"] == []
    assert result["skipped_thin"] == 1


def test_analyze_skips_non_equity_markets():
    """Given a market with no cashtag, when analyzed, then it is skipped as non-equity (T4)."""
    # Given
    quotes = [_quote(question="Will the Fed cut rates in July?", price=0.9)]
    events = [_event(headline="Rates decision looming", tone=-9.0)]

    # When
    result = analyze(quotes, events, threshold=0.25)

    # Then
    assert result["mismatches"] == []
    assert result["skipped_no_ticker"] == 1


def test_analyze_skips_when_no_supporting_news():
    """Given an equity market but no topically related news, when analyzed, then skipped for no news."""
    # Given
    quotes = [_quote(price=0.9)]
    events = [_event(headline="Oil prices surge on supply cuts", tone=-9.0)]

    # When
    result = analyze(quotes, events, threshold=0.25)

    # Then
    assert result["mismatches"] == []
    assert result["skipped_no_news"] == 1


def test_analyze_dedupes_repeated_events():
    """Given the same event repeated, when analyzed, then it is counted once (no double weighting)."""
    # Given
    quotes = [_quote(price=0.85)]
    events = [_event(event_id="e1", tone=-9.0), _event(event_id="e1", tone=9.0)]

    # When
    result = analyze(quotes, events, threshold=0.25)

    # Then: only the first e1 survives dedup, tone -9.0 → bearish mismatch
    assert len(result["mismatches"]) == 1
    assert result["mismatches"][0]["direction"] == "bearish"


def test_evaluate_market_returns_reason_for_aligned():
    """Given an aligned market, when evaluated, then emit is False and reason is aligned."""
    # Given / When
    verdict = evaluate_market(_quote(price=0.5), [_event(tone=0.0)], 0.25)

    # Then
    assert verdict["emit"] is False
    assert verdict["reason"] == "aligned"
