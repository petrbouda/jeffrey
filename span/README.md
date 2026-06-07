# async-profiler Span API — research notes

Research on async-profiler's upcoming **Span API** (the unmerged
[`span-api` branch](https://github.com/async-profiler/async-profiler/tree/span-api) /
[PR #1755](https://github.com/async-profiler/async-profiler/pull/1755)) and what it could mean for
Jeffrey. Captured 2026-06-05.

## What a Span is (one line)

`Span.start()` / `Span.end(startTime, "tag")` records a **latency interval** (a request or
operation) into the same JFR file as the profiling samples, clock-aligned and tagged with
`jdk.jfr.Contextual` — so the profile can later be filtered to "what the app was doing during the
slow requests."

## Documents

| File | What it covers |
|------|----------------|
| [`async-profiler-span-api.md`](./async-profiler-span-api.md) | **The feature.** Deep analysis of the `span-api` branch: public API (`Span` / `Recording`), how it works internally (clock alignment, the repurposed sample counter, the `profiler.Span` JFR event), the relationship to JDK 25's `@Contextual` annotation, release status, and a sketch of how Jeffrey could ingest spans. |
| [`async-profiler-span-api-jeffrey-candidates.md`](./async-profiler-span-api-jeffrey-candidates.md) | **The places (producing).** Where Jeffrey Microscope *instrumenting itself* with Spans would be most valuable: instrument-once chokepoints (REST / DB / gRPC), ranked `file:line` candidates by subsystem (AI calls, JFR ingestion, heap-dump indexing, analysis compute), anti-candidates (hot loops to avoid), and a recommended 4-edit starter set. |
| [`span-visualization-research.md`](./span-visualization-research.md) | **The visualization (consuming).** How span-correlated profiling is visualized in the industry (Pyroscope, Datadog, JMC, JDK 25 `@Contextual`), which Jeffrey frontend primitives we'd build on, and where each approach injects. |
| [`mockups/index.html`](./mockups/index.html) | **Five interactive browser mockups** of span visualization in Jeffrey — open in a browser. Span Lanes, Timeseries×Flamegraph, Span Table, Span Heatmap, Thread×Span overlay. Uses Jeffrey's real design tokens. |

## Status — not actionable yet

The Span API is **unreleased**. As of 2026-06-05 it lives only on an open PR; the latest release is
`v4.4` (2026-04-20), predating the Span commits, and even the `nightly` prerelease does not contain
it. Treat these notes as a **design map**, gated on the API merging and shipping in a post-v4.4
release. Details in [`async-profiler-span-api.md` §8](./async-profiler-span-api.md#8-status--caveats).
