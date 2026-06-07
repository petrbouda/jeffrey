# Visualizing async-profiler Spans in Jeffrey — research & suggestions

> Companion to [`async-profiler-span-api.md`](./async-profiler-span-api.md) (the feature) and
> [`async-profiler-span-api-jeffrey-candidates.md`](./async-profiler-span-api-jeffrey-candidates.md)
> (where to produce spans). This doc answers: **how do you visualize span-correlated profiles, what
> does the industry do, and where would it slot into Jeffrey's frontend?** Compiled 2026-06-05.
> Interactive mockups live in [`mockups/index.html`](./mockups/index.html).

---

## 1. The core idea (how span-correlated profiling works)

A `profiler.Span` is a `[start, end]` interval on a thread, tagged with a string, written into the
**same JFR** as the profiling samples and **clock-aligned** with them. The `jdk.jfr.Contextual`
annotation on the tag means: *a JFR reader treats the tag as active context for every event on that
thread during the interval.* (See [`async-profiler-span-api.md` §4.5](./async-profiler-span-api.md).)

That single property — **samples and spans share a clock, and a reader can tell which samples fall
inside which span** — is the entire basis for visualization. Everything below is a different way of
exploiting "filter / group / attribute samples by the span that enclosed them."

The canonical interaction, used by every tool in this space:

> **Pick a span (or a time window) → the flame graph re-renders from only the samples inside it.**
> "Traces tell you *which* requests were slow; profiles tell you *why*." — Datadog

---

## 2. What the industry does (internet research)

### Grafana Pyroscope — "Traces to Profiles" / Span Profiles
- Profiles carry trace context (trace/span IDs); you can **filter profile samples to those taken
  during a specific span**, yielding a flame graph scoped to the exact slow operation.
- UX: an **embedded flame graph inside each span's detail panel** — no navigation away from the span.
- "Span Profiles deliver focused analysis on specific execution scopes (individual requests / spans)."
- Sources: [Traces to profiles](https://grafana.com/docs/pyroscope/latest/view-and-analyze-profile-data/traces-to-profiles/),
  [Java span profiles](https://grafana.com/docs/pyroscope/latest/configure-client/trace-span-profiles/java-span-profiles/),
  [InfraCloud writeup](https://www.infracloud.io/blogs/linking-traces-continuous-profiling-pyroscope/).

### Datadog — Code Hotspots + Timeline view
- APM spans and the continuous profiler are auto-linked: pivot from a span to the profile that ran
  during it, and vice-versa.
- **Code Hotspots** tab beneath the flame graph highlights the methods inside a span and their
  durations.
- **Timeline view**: threads/goroutines/loops as **horizontal lanes**, activity **color-coded**
  (blue = CPU, yellow = monitor-blocked, red = stop-the-world GC). **Selecting a time segment within
  a span filters the flame graph below** to the methods running in that window.
- Sources: [Timeline view](https://www.datadoghq.com/blog/continuous-profiler-timeline-view/),
  [Continuous Profiler](https://docs.datadoghq.com/profiler/),
  [Flame graph explainer](https://www.datadoghq.com/knowledge-center/distributed-tracing/flame-graph/).

### JDK Mission Control + JDK 25 `@Contextual`
- JMC renders **threads in a table, each row with an associated graph** of thread state — the closest
  "official" analogue to a thread-lane timeline.
- JDK 25's `@Contextual` makes a trace/order event's id+name **display alongside other events** in the
  same thread during its lifespan (e.g. a `JavaMonitorEnter` shows the active `Trace.name`).
- Sources: [What's new for JFR in JDK 25](https://egahlin.github.io/2025/05/31/whats-new-in-jdk-25.html),
  [Contextual javadoc](https://docs.oracle.com/en/java/javase/25/docs/api/jdk.jfr/jdk/jfr/Contextual.html),
  [JMC user guide](https://docs.oracle.com/en/java/java-components/jdk-mission-control/8/user-guide/using-jdk-flight-recorder.html).

### Recurring visualization patterns (synthesis)
| Pattern | What it is | Best for |
|---------|-----------|----------|
| **Waterfall / swimlane** | spans as bars on a time axis, one lane per operation/thread | "where did wall-clock time go" |
| **Timeline lanes w/ state colors** | per-thread CPU/blocked/GC bands | "what was each thread doing, and was CPU used well" |
| **Scoped flame graph** | flame rebuilt from samples in a selected span/window | "why was *this* slow" |
| **Embedded flame in span detail** | mini flame inline per span | quick drill-down without context switch |
| **Latency heatmap** | operation × time, color = latency/count | spotting spikes / outliers |
| **Span table w/ percentiles** | aggregate by tag: count, p95, CPU share | "which operation dominates / is slowest" |

---

## 3. Jeffrey's frontend — what we'd build on

Jeffrey already has every primitive these patterns need (mapped by a frontend exploration of
`jeffrey-microscope/pages-microscope/`):

| Existing piece | File | What it gives us |
|---|---|---|
| **Timeseries + brush** | `src/components/TimeSeriesChart.vue` | ApexCharts area chart with a brush that emits `update:timeRange` and calls `GraphUpdater.updateWithZoom(TimeRange)`. **This is the existing "select a window → re-render flame graph" mechanism.** |
| **GraphUpdater** | `src/services/flamegraphs/updater/GraphUpdater.ts` | The sync bus between timeseries and flame graph. Reuse verbatim for span→flame scoping. |
| **Flame graph** | `src/components/FlamegraphComponent.vue` + `services/flamegraphs/Flamegraph.ts` | HTML-canvas flame from protobuf; already re-fetches on zoom. |
| **Thread timeline** | `src/components/ThreadComponent.vue` + `services/thread/ThreadRow.ts` / `ThreadGroups.ts` | **Konva** per-thread lane drawing. `ThreadPeriod {startOffset, width, values, warning}` is the *exact* shape a span needs. The closest existing analogue to a span timeline. |
| **SubSecond heatmap** | `src/components/SubSecondComponent.vue` + `services/subsecond/HeatmapGraph.ts` | ApexCharts heatmap engine — reuse for a span latency heatmap. |
| **Tables** | `SortableTableHeader`, three-state (`LoadingState`/`ErrorState`/`EmptyState`) | the span-by-tag table. |
| **API base** | `src/services/api/BaseProfileClient.ts` | `new BaseProfileClient(profileId, 'spans')` → a `ProfileSpanClient`. |
| **Formatting** | `src/services/FormattingService.ts` | `formatDuration2Units`, `formatTimestamp`, … (never `new Date()`). |
| **Design tokens** | `src/assets/design-tokens.css` | `--color-primary #5e64ff`, `--color-success/danger/warning/info`, the `--flamegraph-color-*` palette, shadows, radii. |
| **Router / sidebar** | `src/router/index.ts`, `src/views/profiles/ProfileDetail.vue` | one entry in `profileChildRoutes` + one `nav-item` adds a "Spans" view. |

**Prerequisite for all of it:** the backend `recording-parser` must learn to parse `profiler.Span`
into the profile DuckDB (an event row with `tag`, `start`, `duration`, `thread`), and a
`ProfileSpanClient` must expose it. Until then the frontend has nothing to render.

---

## 4. The five mockups

Open [`mockups/index.html`](./mockups/index.html). Each is standalone HTML using Jeffrey's tokens,
with mock data themed around *Jeffrey profiling itself* (spans like `profile.initialize`,
`jfr.parse_and_ingest`, `hprof.index.build`, `ai.oql.call` — the candidates from the other doc).

| # | Mockup | Reuses | Injection point | Inspiration |
|---|--------|--------|-----------------|-------------|
| 1 | **Span Lanes** — swimlane Gantt, click → scoped flame | Konva `ThreadComponent` machinery | new `ProfileSpansTimeline.vue` + `SpanRow.ts`/`SpanGroups.ts` | Datadog Timeline, Jaeger waterfall |
| 2 | **Timeseries × Flamegraph** — span bands overlaid, click → scope | `TimeSeriesChart` + `GraphUpdater` (as-is) | add `annotations` prop to `TimeSeriesChart.vue` | Pyroscope Traces→Profiles, DD Code Hotspots |
| 3 | **Span Table × Profile** — aggregate by tag + embedded flame | tables + `FlamegraphComponent` | new `ProfileSpans.vue` | Pyroscope Span Profiles |
| 4 | **Span Heatmap** — tag × time, color = p95/count | `HeatmapGraph.ts` (ApexCharts heatmap) | new `ProfileSpanHeatmap.vue` | SubSecond, Honeycomb latency heatmaps |
| 5 | **Thread × Span overlay** — context ribbon over thread-state lanes | `ThreadRow.ts` Konva `draw()` | extend `ThreadRowData` + ribbon layer | DD Timeline, JMC, JDK 25 `@Contextual` |

---

## 5. Recommendation

1. **Start with #2 (Timeseries overlay).** Smallest diff, biggest payoff: it rides the existing
   brush→flamegraph path (`GraphUpdater.updateWithZoom`), so "click a span → flame graph scoped to
   it" is mostly wiring, not new infrastructure. And it lands spans inside a screen users already use
   (the flamegraph view), so adoption is free.
2. **Then #1 (Span Lanes) as the dedicated "Spans" tab.** It's the natural home for browsing all
   spans and mirrors `ThreadComponent`, so the Konva drawing/scaling/tooltip code is already written.
3. **#5 (Thread overlay) is the highest-insight idea** — it attributes GC pauses and lock contention
   to the *request that owned them*, which is exactly what `@Contextual` is for. Build it once the
   thread view can consume spans; it's an extension, not a new view.
4. **#3 / #4 are complementary analytics** — reach for them when the question is "which operation
   dominates / is slowest / spiked when," rather than "what happened in this one window."

**Sequencing rule:** none of this is reachable until (a) the Span API ships in a released
async-profiler (it's unreleased — PR #1755, gated on a post-v4.4 release) and (b) Jeffrey's parser
ingests `profiler.Span`. Treat the mockups as a design target to validate the parser/DTO shape
against, not an immediate build.

---

## Sources

- Grafana Pyroscope — [Traces to profiles](https://grafana.com/docs/pyroscope/latest/view-and-analyze-profile-data/traces-to-profiles/), [Java span profiles](https://grafana.com/docs/pyroscope/latest/configure-client/trace-span-profiles/java-span-profiles/)
- InfraCloud — [Linking Traces with Continuous Profiling using Pyroscope](https://www.infracloud.io/blogs/linking-traces-continuous-profiling-pyroscope/)
- Datadog — [Continuous Profiler timeline view](https://www.datadoghq.com/blog/continuous-profiler-timeline-view/), [Continuous Profiler docs](https://docs.datadoghq.com/profiler/), [What is a Flame Graph](https://www.datadoghq.com/knowledge-center/distributed-tracing/flame-graph/)
- Erik Gahlin — [What's new for JFR in JDK 25](https://egahlin.github.io/2025/05/31/whats-new-in-jdk-25.html)
- Oracle — [jdk.jfr.Contextual (JDK 25)](https://docs.oracle.com/en/java/javase/25/docs/api/jdk.jfr/jdk/jfr/Contextual.html), [JMC user guide](https://docs.oracle.com/en/java/java-components/jdk-mission-control/8/user-guide/using-jdk-flight-recorder.html)
