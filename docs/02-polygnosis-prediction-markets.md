# When the crowd and the news disagree, that is the signal

*Source: [PolyGnosis 2.0: Enhancing LLM Reasoning via Agentic Harness Engineering for Polymarket and OSINT Insight Extraction](https://arxiv.org/abs/2605.25958), Wang et al., May 2026.*

Prediction markets like Polymarket let people bet real money on the outcome of real events: elections, court rulings, product launches. The crowd's bets form a kind of temperature reading. News coverage forms another. Most days the two point the same way. When they drift apart, something is happening that someone has noticed and the rest have not.

Three researchers built a system to hunt for that gap. Their setup stitches Polymarket prices to a global database of news events that records what the world's media is reporting minute by minute. A team of AI agents reads both, then flags what the authors call perspective mismatches: moments when the market's mood and the media's mood point in different directions. The bet is that those mismatches are early trading signals.

Two findings stand out for anyone watching AI agents do real work.

First, the more the agents were allowed to sit and reflect at the end, the more they drifted. Extra thinking did not sharpen their answers. It produced confident drift. The team had to cap the reflection to keep the agents honest.

Second, the agents showed a consensus bias. When reasoning about messy human stories, they tended to converge on the same conclusion regardless of the evidence in front of them. Agreement does not equal correctness. The fix was hard validation: deterministic checks the agents could not talk their way around.

The lesson travels far beyond finance. When you let a group of AI agents reason about ambiguous, narrative problems, more thinking can backfire, and consensus can mean shared error rather than shared truth. You need guardrails that do not depend on the agents policing themselves.

The researchers landed on a configuration that matched professional analysts while keeping costs and response time in check. The harder part was admitting that adding more AI did not mean better answers. Sometimes the discipline of doing less mattered more.
