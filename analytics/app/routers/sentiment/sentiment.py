"""Sentiment analysis router: lexicon-based and SALI scoring."""

from fastapi import APIRouter
from pydantic import BaseModel

from app.services.sentiment.sentiment_service import analyze_lexicon, analyze_sentiment

router = APIRouter()


class SentimentRequest(BaseModel):
    texts: list[str]
    include_certainty: bool = False


class LexiconRequest(BaseModel):
    texts: list[str]
    sources: list[str] | None = None


@router.post("/analyze")
def run_sentiment(req: SentimentRequest):
    return analyze_sentiment(req.texts, req.include_certainty)


@router.post("/lexicon")
def run_lexicon(req: LexiconRequest):
    return analyze_lexicon(req.texts, req.sources)
