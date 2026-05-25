from app.services.sentiment.sentiment_service import analyze_lexicon, analyze_sentiment


def test_sentiment_positive_text():
    """Given bullish text, when analyzing sentiment, then score is positive."""
    result = analyze_sentiment(["Markets rally on strong growth data"], include_certainty=True)

    assert "error" not in result
    assert len(result["results"]) == 1
    r = result["results"][0]
    assert r["score"] > 0
    assert r["label"] == "positive"
    assert "certainty" in r
    assert r["certainty"] > 0


def test_sentiment_negative_text():
    """Given bearish text, when analyzing sentiment, then score is negative."""
    result = analyze_sentiment(["Stocks plunge amid recession fears and sell-off"])

    assert "error" not in result
    r = result["results"][0]
    assert r["score"] < 0
    assert r["label"] == "negative"


def test_sentiment_neutral_text():
    """Given neutral text, when analyzing sentiment, then label is neutral."""
    result = analyze_sentiment(["The meeting is scheduled for Tuesday"])

    assert "error" not in result
    r = result["results"][0]
    assert r["score"] == 0.0
    assert r["label"] == "neutral"


def test_sentiment_negation():
    """Given text with negation, when analyzing sentiment, then positive word becomes negative."""
    result = analyze_sentiment(["Not strong"])

    assert "error" not in result
    r = result["results"][0]
    assert r["score"] < 0


def test_sentiment_empty_input():
    """Given empty texts, when analyzing sentiment, then error returned."""
    assert "error" in analyze_sentiment([])


def test_lexicon_basic():
    """Given financial texts, when lexicon analysis, then polarity and subjectivity are valid."""
    result = analyze_lexicon(
        ["Markets rally on strong employment data"],
        sources=["title"],
    )

    assert "error" not in result
    r = result["results"][0]
    assert -1.0 <= r["polarity"] <= 1.0
    assert 0.0 <= r["subjectivity"] <= 1.0
    assert r["source"] == "title"


def test_lexicon_mixed_sentiment():
    """Given text with both positive and negative words, when lexicon analysis, then polarity is near zero."""
    result = analyze_lexicon(["Strong rally but fear of recession and crisis"])

    assert "error" not in result
    r = result["results"][0]
    assert -0.5 <= r["polarity"] <= 0.5


def test_sentiment_consistency():
    """Given same text analyzed twice, when comparing, then identical results."""
    # Given
    text = ["Markets rally on strong growth data"]

    # When
    r1 = analyze_sentiment(text)
    r2 = analyze_sentiment(text)

    # Then
    assert r1["results"][0]["score"] == r2["results"][0]["score"]
    assert r1["results"][0]["label"] == r2["results"][0]["label"]


def test_lexicon_sources_match():
    """Given texts with two sources, when lexicon analysis, then output has two entries."""
    # Given
    texts = ["Markets rally", "Fear of recession"]
    sources = ["headline", "body"]

    # When
    result = analyze_lexicon(texts, sources=sources)

    # Then
    assert "error" not in result
    assert len(result["results"]) == 2
    assert result["results"][0]["source"] == "headline"
    assert result["results"][1]["source"] == "body"
