from fastapi.testclient import TestClient

from app.main import app

client = TestClient(app)


def test_perspex_analyze_emits_mismatch():
    """Given divergent market and news via the API, when posted, then a mismatch is returned."""
    # Given
    payload = {
        "quotes": [{
            "market_id": "m1",
            "question": "Will $AAPL beat earnings?",
            "outcome_yes_price": 0.85,
            "volume": 10000.0,
            "liquidity": 50000.0,
            "time": "2026-06-24T12:00:00Z",
        }],
        "events": [{
            "event_id": "e1",
            "headline": "Apple earnings outlook deteriorates sharply",
            "avg_tone": -9.0,
            "time": "2026-06-24T12:00:00Z",
        }],
        "divergence_threshold": 0.25,
    }

    # When
    response = client.post("/api/v1/perspex/analyze", json=payload)

    # Then
    assert response.status_code == 200
    body = response.json()
    assert len(body["mismatches"]) == 1
    assert body["mismatches"][0]["tickers"] == ["AAPL"]
    assert body["mismatches"][0]["direction"] == "bearish"


def test_perspex_analyze_aligned_returns_no_mismatches():
    """Given aligned market and news via the API, when posted, then no mismatch is returned."""
    # Given
    payload = {
        "quotes": [{
            "market_id": "m1",
            "question": "Will $AAPL beat earnings?",
            "outcome_yes_price": 0.5,
            "volume": 10000.0,
            "liquidity": 50000.0,
            "time": "2026-06-24T12:00:00Z",
        }],
        "events": [{
            "event_id": "e1",
            "headline": "Apple earnings report in line",
            "avg_tone": 0.0,
            "time": "2026-06-24T12:00:00Z",
        }],
    }

    # When
    response = client.post("/api/v1/perspex/analyze", json=payload)

    # Then
    assert response.status_code == 200
    assert response.json()["mismatches"] == []
