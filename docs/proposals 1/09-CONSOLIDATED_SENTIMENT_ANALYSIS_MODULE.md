# Consolidated Proposal: Sentiment Analysis Module

**Status:** Proposal
**Applicability:** Tickonomics v3 (Java 25 Spring Boot + Python FastAPI + TimescaleDB)
**Consolidation Date:** 2026-05-21

## v3 Integration Points

| Track                             | Components Affected                                                                                                  |
|-----------------------------------|----------------------------------------------------------------------------------------------------------------------|
| Track 2 (DB)                      | `sentiment_history` hypertable (dual-score: lexicon + BERT), `sentiment_score` column normalization                  |
| Track 3 (Analytics Worker)        | `SentimentService`, FinBERT transformer integration, async news processing queue, multi-source NLP pipeline          |
| Track 5 (Computation)             | `SaliProcessor`, `SignalGenerator` sentiment confirmation logic, `RegimeDetector` certainty-based threshold widening |
| Track 7 (Dashboard)               | Sentiment Heatmap panel, News Correlation overlay on ILI chart, sentiment polarity visualization                     |
| Track 8 (Backtesting)             | Semantic backtesting (Old NLP vs New NLP comparison), sentiment-augmented strategy replay                            |
| Track 10 (Demo/Virtual Portfolio) | Sentiment-suppressed signals during contradictory news, SALI-weighted position sizing                                |

---

## PART I: CORE PROPOSAL (PRIMARY)

The recommended primary sentiment engine for Tickonomics v3.

---

### 1.1 LLM-Based Sentiment Intelligence (FinBERT Integration)

**Source:** SSRN-4995172 (STGP-SATA Oct 2024 Final Update)
**Tracks:** Track 2 (DB), Track 3 (Analytics Worker), Track 5 (Computation), Track 7 (Dashboard), Track 8 (Backtesting),
Track 10 (Demo/Virtual Portfolio)

#### Rationale

Traditional lexicon-based NLP (TextBlob/AFINN) cannot understand context, sarcasm, or negation -- critical limitations
for interpreting financial news where "Rates didn't fall as much as expected" is a bearish signal despite containing "
didn't fall." The October 2024 update of the STGP-SATA research highlights that BERT (Bidirectional Encoder
Representations from Transformers) can significantly enhance the robustness of trading strategies. FinBERT, pre-trained
specifically on financial communication, provides superior contextual accuracy and can identify "Hawkish/Dovish" shifts
in Fed sentiment days before they fully reflect in FRED quantitative data.

#### Module: Transformer-Based Scoring (Track 3)

- **Location:** `analytics/app/services/sentiment/finbert_service.py`
- **Dependencies:** `transformers`, `torch`
- **Model:** `ProsusAI/finbert` (pre-trained on financial communication).
- **Implementation:**
    - Replace (or augment) `TextBlob` with fine-tuned FinBERT from Hugging Face library.
    - Analyze FOMC minutes and Fed speakers for qualitative "Z-score" complementing quantitative FRED data.
    - Example: "Powell's tone was more hawkish than the previous meeting" produces a measurable sentiment delta.

#### Module: Asynchronous News Processing Queue (Track 3)

- **Location:** `analytics/app/services/sentiment/news_queue.py`
- **Rationale:** Transformer models are compute-intensive. Implement an async news processing queue in the Python worker
  to ensure it does not block critical-path Arrow IPC requests.
- **Implementation:**
    - Poll live news feeds (e.g., via OpenBB or RSS) every 15 minutes.
    - Process news asynchronously with results stored for downstream consumption.
    - Queue supports batch processing of accumulated articles during off-peak periods.

#### Module: Contextual Macro Analysis (Track 3)

- **Location:** `analytics/app/services/sentiment/macro_sentiment.py`
- **Implementation:** Use BERT to specifically analyze:
    - FOMC minutes for policy direction signals.
    - Fed speaker statements for tone analysis.
    - Macro-economic news for liquidity impact assessment.
- Output: Qualitative Z-score complementing quantitative FRED data.

#### Module: Sentiment-Volatility Interaction (Track 5)

- **Location:** `computation/src/main/java/com/tickonomics/computation/signal/SentimentVolatilityGuard.java`
- **Implementation:** Use BERT-derived "Certainty Scores" to adjust `RegimeDetector`.
    - If news is highly contradictory (low BERT certainty), automatically widen ILI signal thresholds.
    - High certainty in a direction can narrow thresholds, increasing signal sensitivity.

#### Module: Hybrid Sentiment Hypertable (Track 2)

- **Schema:** Add to `sentiment_history` hypertable:
    - `lexicon_score` (fast, from TextBlob/AFINN)
    - `bert_score` (accurate, from FinBERT)
    - `bert_certainty` (confidence measure)
    - `source_type` (FOMC, Fed speaker, market news, etc.)
    - `timestamp`
- **Rationale:** Store both scores for comparative analysis and A/B testing.

#### Module: Semantic Backtesting (Track 8/10)

- **Implementation:** Compare historical signals using "Old NLP" (lexicon) vs. "New NLP" (BERT).
- Quantify reduction in false-positive signals during high-noise periods (e.g., earnings season).
- Benchmark false-positive reduction percentage.

#### Validation Criteria

- [ ] FinBERT correctly identifies contextual financial sentiment in test corpus.
- [ ] BERT certainty scores correlate with signal accuracy (high certainty = higher hit rate).
- [ ] False-positive signal reduction during high-noise periods is measurable.
- [ ] Async queue processes news without blocking critical-path requests.
- [ ] Dashboard displays sentiment attribution for each signal.

---

## PART II: ALTERNATIVE PLUGGABLE IMPLEMENTATION

A lighter-weight alternative that can be swapped in for the FinBERT engine via configuration.

---

### 2.1 Sentiment-Augmented Liquidity Index (SALI) - Lexicon-Based

**Source:** SSRN-4458417 (STGP-SATA original)
**Tracks:** Track 2 (DB), Track 3 (Analytics Worker), Track 5 (Computation), Track 7 (Dashboard)
**Type:** ALTERNATIVE PLUGGABLE IMPLEMENTATION

#### Rationale

The STGP-SATA algorithm handles Technical Analysis (TA) and Sentiment Analysis (SA) in separate, specialized tree
branches with an AND-gate root ensuring signals are confirmed by both data types. This lexicon-based approach is
simpler, faster, and requires significantly less compute than FinBERT while still providing meaningful sentiment
augmentation. SALI adds a qualitative layer, distinguishing between "technical" RRP drains and "sentiment-driven"
panics.

#### Module: SentimentService (Track 3)

- **Location:** `analytics/app/services/sentiment/lexicon_sentiment_service.py`
- **Dependencies:** `TextBlob`, `AFINN`
- **Implementation:**
    - Process live news feeds (OpenBB or RSS) every 15 minutes.
    - Multi-source NLP: Analyze titles, summaries, and text separately as per the paper's methodology.
    - Generate "Sentiment Score" for relevant equity symbols and macro events (Fed announcements).
- **Output:** Normalized scores in the `[-1, 1]` range, consistent with existing `zscore_series`.

#### Module: SALI Processor (Track 5)

- **Location:** `computation/src/main/java/com/tickonomics/computation/signal/SaliProcessor.java`
- **Implementation:** Combine `ili_value` with `sentiment_score` using:
    - Configurable weighting (e.g., 70% ILI / 30% sentiment).
    - OR the paper's "AND-gate" logic: both branches must agree for signal confirmation.
- **Signal Confirmation Logic:**
    - `ACTIONABLE` signal from `SignalGenerator` must be confirmed by corresponding sentiment regime.
    - Example: SELL signals suppressed if sentiment is extremely BULLISH despite high ILI.

#### Module: Sentiment History Persistence (Track 2)

- **Schema:** `sentiment_history` hypertable storing:
    - `symbol` (nullable for macro events)
    - `polarity` score
    - `subjectivity` score
    - `source` (news title, summary, or full text)
    - `timestamp`
- Consistent with existing TimescaleDB hypertable conventions.

#### Module: Sentiment Heatmap (Track 7)

- **Location:** Dashboard panel component.
- **Features:**
    - Visualize sentiment polarity across tracked symbols, complementing the Liquidity Heatmap.
    - Overlay news events onto the ILI chart to show causal impact of sentiment on liquidity stress.
    - News Correlation markers on ILI timeline.

#### Validation Criteria

- [ ] Sentiment scores normalized to `[-1, 1]` range.
- [ ] SALI-weighted signals show reduced false positives vs. ILI-only signals.
- [ ] AND-gate logic prevents "catching falling knives" during negative news cycles.
- [ ] Dashboard heatmap updates reflect real-time sentiment shifts.

---

## PART III: A/B TESTING FRAMEWORK

Compare FinBERT vs. lexicon-based SALI on key performance dimensions.

### Test Configuration

| Dimension             | FinBERT (PRIMARY)               | Lexicon SALI (ALTERNATIVE)      |
|-----------------------|---------------------------------|---------------------------------|
| Model                 | ProsusAI/finbert (Transformer)  | TextBlob + AFINN + SentiWordNet |
| Compute requirement   | GPU recommended, ~200ms/article | CPU-only, ~5ms/article          |
| Context awareness     | Full contextual understanding   | Pattern matching only           |
| Negation handling     | Native                          | None                            |
| Sarcasm detection     | Partial                         | None                            |
| Processing mode       | Async queue                     | Sync or async                   |
| Latency               | Higher (queue-based)            | Lower (immediate)               |
| Deployment complexity | Higher (torch dependency, GPU)  | Lower (pure Python)             |

### Evaluation Metrics

| Metric                   | Description                                                   | Target                               |
|--------------------------|---------------------------------------------------------------|--------------------------------------|
| False Positive Reduction | Signals triggered by misleading sentiment                     | FinBERT expected to outperform       |
| Processing Latency       | Time from article ingestion to sentiment score                | SALI expected to be faster           |
| Sentiment Accuracy       | Correlation between sentiment score and subsequent price move | FinBERT expected to be more accurate |
| Hawkish/Dovish Detection | Accuracy of Fed tone classification                           | FinBERT expected to detect nuances   |
| FOMC Minutes Analysis    | Correct identification of policy direction changes            | FinBERT essential for this use case  |
| Throughput               | Articles processed per minute                                 | SALI higher throughput               |

### Recommended Deployment Strategy

1. **Phase 1 (Initial v3):** Deploy lexicon-based SALI as the default. It requires no GPU, has lower latency, and
   provides immediate value.
2. **Phase 2 (Post-validation):** Deploy FinBERT alongside SALI. Run both in parallel, storing both scores in the
   `sentiment_history` hypertable.
3. **Phase 3 (Optimization):** Use A/B test results to determine optimal configuration:
    - Use FinBERT for high-impact events (FOMC, major economic releases).
    - Use lexicon-based SALI for routine market news where latency matters.
    - Hybrid approach: Use SALI for initial filtering, FinBERT for confirmation of borderline cases.

### Hybrid Architecture

Both approaches share the following integration points regardless of which is active:

- **SaliProcessor (Track 5):** Accepts sentiment scores from either engine via a common interface.
- **Signal Confirmation Logic:** Same AND-gate / weighted logic applies regardless of sentiment source.
- **Dashboard Visualization:** Same heatmap and news correlation displays work with both engines.
- **Backtesting:** Same semantic backtesting framework compares both approaches on identical historical data.

---

## Source Proposals

| # | Original Proposal                    | File                                     | Section         |
|---|--------------------------------------|------------------------------------------|-----------------|
| 1 | LLM Sentiment Intelligence (FinBERT) | `LLM_SENTIMENT_INTELLIGENCE_PROPOSAL.md` | Core 1.1        |
| 2 | Sentiment-Augmented ILI (SALI)       | `SENTIMENT_AUGMENTED_ILI_PROPOSAL.md`    | Alternative 2.1 |
