"""Sentiment analysis: lexicon-based scoring for financial text."""

_FINANCIAL_POSITIVE = {
    "rally", "surge", "gain", "bullish", "beat", "exceed", "strong", "growth",
    "profit", "upgrade", "outperform", "rise", "climb", "soar", "optimism",
    "recovery", "expansion", "robust", "solid", "improve", "positive",
}

_FINANCIAL_NEGATIVE = {
    "crash", "plunge", "loss", "bearish", "miss", "decline", "weak", "recession",
    "downgrade", "underperform", "fall", "drop", "slump", "pessimism", "crisis",
    "contraction", "fragile", "deteriorate", "negative", "hawkish", "tightening",
    "risk", "default", "volatility", "fear", "sell-off", "correction",
}

_INTENSIFIERS = {"very", "extremely", "highly", "significantly", "strongly"}
_NEGATORS = {"not", "no", "never", "neither", "nor"}


def analyze_sentiment(texts: list[str], include_certainty: bool = False) -> dict:
    if not texts:
        return {"error": "No texts provided"}

    results = []
    for text in texts:
        words = text.lower().split()
        if not words:
            results.append({"text": text, "score": 0.0, "label": "neutral"})
            continue

        polarity = 0.0
        negate = False
        intensity = 1.0
        n_scored = 0

        for word in words:
            clean = word.strip(".,!?;:")
            if clean in _NEGATORS:
                negate = True
                continue
            if clean in _INTENSIFIERS:
                intensity = 1.5
                continue

            if clean in _FINANCIAL_POSITIVE:
                contribution = 1.0 * intensity
                if negate:
                    contribution *= -1
                polarity += contribution
                n_scored += 1
                negate = False
            elif clean in _FINANCIAL_NEGATIVE:
                contribution = -1.0 * intensity
                if negate:
                    contribution *= -1
                polarity += contribution
                n_scored += 1
                negate = False
            intensity = 1.0

        if n_scored > 0:
            score = max(-1.0, min(1.0, polarity / n_scored))
        else:
            score = 0.0

        label = "positive" if score > 0.15 else ("negative" if score < -0.15 else "neutral")

        entry = {"text": text, "score": round(score, 4), "label": label}
        if include_certainty:
            entry["certainty"] = round(min(1.0, abs(score) * 1.5 + 0.3), 4)
        results.append(entry)

    return {"results": results}


def analyze_lexicon(texts: list[str], sources: list[str] | None = None) -> dict:
    if not texts:
        return {"error": "No texts provided"}

    results = []
    for i, text in enumerate(texts):
        words = text.lower().split()
        pos_count = sum(1 for w in words if w.strip(".,!?;:") in _FINANCIAL_POSITIVE)
        neg_count = sum(1 for w in words if w.strip(".,!?;:") in _FINANCIAL_NEGATIVE)
        total = pos_count + neg_count

        polarity = (pos_count - neg_count) / total if total > 0 else 0.0
        subjectivity = total / len(words) if words else 0.0

        results.append({
            "text": text,
            "polarity": round(max(-1.0, min(1.0, polarity)), 4),
            "subjectivity": round(min(1.0, subjectivity), 4),
            "source": (sources[i] if sources and i < len(sources) else "text"),
        })

    return {"results": results}
