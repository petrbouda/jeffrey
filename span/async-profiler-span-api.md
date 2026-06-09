# async-profiler — Span API (deep analysis)

> Research notes on the **`span-api`** branch of
> [`async-profiler/async-profiler`](https://github.com/async-profiler/async-profiler/tree/span-api).
> Captured 2026-06-05. Branch state at time of writing: **4 commits ahead / 1 behind `master`**,
> dated **June 3–4 2026**. Feature branch, **not yet merged or released**.

---

## 1. TL;DR

The **Span API** lets application code mark **latency intervals** (requests, transactions,
operations) and record them as events **inside the same JFR file** as the profiling samples.
Span timestamps share the profiler's clock, so afterwards you can open the JFR and ask:

- *"What was the app doing during the slowest requests?"*
- *"Show me only the stacks captured while a `GET /api/orders` span was open."*

It is conceptually **tracing-meets-profiling** — like OpenTelemetry spans, but written straight
into the profile and correlated with the actually-sampled stacks, instead of shipped to a tracing
backend.

Key properties:

- **Static, allocation-free API** — cheap enough to leave in production code.
- **No-op when the profiler isn't running** — zero hard dependency on async-profiler being loaded.
- **Clock-aligned with samples** — spans and `jdk.ExecutionSample` events use the same TSC clock.
- **Optional "only if profiled" recording** — high-frequency spans that enclose no sample are
  skipped without entering native code.
- **`Contextual` JFR annotation** — tooling (JMC, `jfr view`) can propagate the span tag to all
  events inside the interval, enabling filter/color by span.

> **Looking for what you can _build_ on spans?** Jump to **§9–§14**: native-vs-reconstructable
> (§9), parent/child hierarchy by time-containment (§10), per-span-instance flamegraph/timeseries
> (§11), the HTTP-exchange-rooted "request explorer" (§12), other patterns like differential
> slow-vs-fast flamegraphs (§13), and a built-vs-new inventory of Jeffrey's span feature (§14).

---

## 2. Branch contents

Commits unique to `span-api` (vs `master`):

| Commit | Date | Message |
|--------|------|---------|
| `c9df7d15` | 2026-06-03 | Span API |
| `9ea98e3b` | 2026-06-04 | Fixed JNI field signature |
| `de3df481` | 2026-06-04 | Make Spans Contextual |
| `1315d6b9` | 2026-06-04 | doc: loaded -> running |

Files changed:

```
added     src/api/one/profiler/Recording.java
added     src/api/one/profiler/Span.java
added     test/test/span/SpanApiApp.java
added     test/test/span/SpanApp.java
added     test/test/span/SpanAttachApp.java
added     test/test/span/SpanTests.java
modified  docs/IntegratingAsyncProfiler.md
modified  docs/ProfilingNonJavaApplications.md
modified  src/asprof.cpp / asprof.h
modified  src/event.h
modified  src/flightRecorder.cpp
modified  src/instrument.cpp
modified  src/javaApi.cpp / javaApi.h
modified  src/jfrMetadata.cpp / jfrMetadata.h
modified  src/log.cpp
modified  src/profiler.cpp / profiler.h
modified  src/threadLocalData.h
modified  src/vmEntry.cpp
```

---

## 3. Public API

Two new classes live in `src/api/one/profiler/`:

- **`Span`** — the user-facing API. All static, no-op when the profiler isn't running.
- **`Recording`** — tracks profiler state and provides a profiler-aligned clock.

### 3.1 `Span` — record an interval

| Method | Behavior |
|--------|----------|
| `long start()` | Returns a start timestamp (or `0` if profiler not running). |
| `end(long startTime, String tag)` | Records the span **unconditionally**. `tag` may be `null`. |
| `endIfProfiled(long startTime, String tag)` | Records **only if ≥1 profiling sample landed on this thread while the span was open**. |
| `emit(long start, long end, String tag)` | Record with explicit timestamps from `Recording.timestamp()`. |
| `emitIfProfiled(long start, long end, String tag)` | Same as `emit`, but only if a sample fell inside `[start, end]`. |

Scoped usage:

```java
import one.profiler.Span;

long span = Span.start();
try {
    handleRequest();
} finally {
    Span.end(span, "GET /api/orders");   // tag may be null
}
```

### 3.2 The `...IfProfiled` variants (the clever part)

For very high-frequency spans, a span enclosing *no* sample adds nothing useful to a sampling
profile — it is just noise and overhead. `endIfProfiled` / `emitIfProfiled` skip emitting such a
span **without even entering native code**: the "was a sample taken?" check is a pure-Java
thread-local comparison.

```java
// Idle (sleeping) workload recorded two ways:
for (int i = 0; i < IDLE_SPANS; i++) {
    long span = Span.start();
    Thread.sleep(5);
    Span.endIfProfiled(span, "idleOptional");  // mostly skipped — no samples while sleeping
}
for (int i = 0; i < IDLE_SPANS; i++) {
    long span = Span.start();
    Thread.sleep(5);
    Span.end(span, "idleNormal");              // all recorded
}
```

Rule of thumb:
- **CPU-busy** spans → kept by `endIfProfiled` (they enclose samples).
- **Idle / off-CPU** spans → mostly dropped by `endIfProfiled` (a sleeping thread takes no samples).
- Use plain `end` when you want *every* span regardless of sampling.

### 3.3 `Recording` — state and clock

```java
Recording.state();      // UNAVAILABLE(0) | STOPPED(1) | RUNNING(2)
Recording.timestamp();  // current time in the profiler's clock (TSC-aligned)
```

State semantics:

| State | Value | Meaning | Spans |
|-------|-------|---------|-------|
| `UNAVAILABLE` | 0 | async-profiler library not loaded at all | dropped |
| `STOPPED` | 1 | loaded, but no JFR session running | dropped |
| `RUNNING` | 2 | a JFR session is active | recorded |

```java
Span.end(Span.start(), "beforeSession");   // dropped (no session yet)
profiler.execute("start,event=cpu,interval=1ms,file=out.jfr");
// Recording.state() == RUNNING
Span.end(Span.start(), "duringSession");   // recorded
profiler.execute("stop");
Span.end(Span.start(), "afterSession");    // dropped (session stopped)
```

---

## 4. How it works under the hood

### 4.1 Clock alignment — spans line up with samples

`Recording.timestamp()` does **not** just call `System.nanoTime()`. When async-profiler uses
**TSC** (CPU timestamp counter) for its sample timestamps, native code (`RecordingAPI::updateClock`)
reaches into the JVM via `MethodHandles.Lookup.IMPL_LOOKUP` and rebinds the timestamp
`MethodHandle` to `jdk.jfr.internal.JVM.counterTime()` — the **exact same clock JFR / async-profiler
stamps samples with**. It uses a `MutableCallSite` (`MutableCallSite.syncAll`) so the swap is
JIT-friendly. If TSC isn't enabled, it falls back to `System.nanoTime()`.

This is why a span's `[start, end]` can be compared instant-for-instant against
`jdk.ExecutionSample` start times. The `SpanTests.spans` test asserts CPU samples fall *inside* the
`busyRequest` span.

The clock is (re)bound:
- at `Recording.registerNatives()` time,
- and on `FlightRecorder::start` via `RecordingAPI::start()` (which also flips `state` to `RUNNING`).

### 4.2 "Was this thread profiled?" — repurposing the sample counter

async-profiler already exposed a per-thread `asprof_thread_local_data.sample_counter`
(originally for native metadata correlation, incremented once per sample). This branch
**repurposes** it: on every recorded sample, `flightRecorder.cpp` now stores the event's
**timestamp** into it instead of a plain increment.

```cpp
// flightRecorder.cpp — FlightRecorder::recordEvent
if (event_type < PROFILING_WINDOW) {   // real samples only, not windows/spans
    asprof_thread_local_data* tld = ThreadLocalData::getIfPresent();
    if (tld != nullptr && event->_start_time > tld->sample_counter) {
        tld->sample_counter = event->_start_time;   // timestamp of last sample on this thread
    }
}
```

`Span.start()` hands the current thread a direct `ByteBuffer` view over that `tld` struct
(`Recording.getThreadLocalBuffer()` → JNI `NewDirectByteBuffer`). Then `emitIfProfiled` is a pure-Java
comparison — no lock, no JNI, no allocation on the skip path:

```java
// Span.emitIfProfiled
if (Recording.state == RUNNING && LOCAL_BUF.get().getLong(0) >= startTime) {
    Recording.emitSpan(startTime, endTime, tag);   // last-sample timestamp falls within the span
}
```

So *"did a sample happen during my span?"* becomes *"is the last-sample timestamp ≥ my start
time?"*.

### 4.3 Virtual threads

A virtual thread can't safely cache a platform-thread-local buffer, so `Span` detects
`java.lang.BaseVirtualThread` (via reflection) and uses a `SENTINEL_BUF` pre-filled with
`Long.MAX_VALUE`. That makes `endIfProfiled` **always record** for virtual threads — it can't cheaply
prove the negative, so it errs toward recording. The same sentinel is used when no thread-local
buffer is available.

### 4.4 The JFR event

A new `profiler.Span` JFR type (`jfrMetadata.cpp`), category "Profiler":

| Field | Type | Notes |
|-------|------|-------|
| `startTime` | long | `F_TIME_TICKS` |
| `duration` | long | `F_DURATION_TICKS` |
| `eventThread` | thread | `F_CPOOL` |
| `tag` | string | `F_CPOOL | F_CONTEXTUAL` — interned in the constant pool |

Native emission path:

```
Span.end / emit
  → Recording.emitSpan (JNI)
    → Java_one_profiler_Recording_emitSpan   (javaApi.cpp)
      → builds SpanEvent { _start_time, _end_time, _tag }
      → Profiler::recordEventOnly(SPAN, &event)
        → FlightRecorder::recordEvent → recordSpan(buf, tid, event)
```

`recordSpan` writes: `T_SPAN` type, var64 start, var64 duration (`end - start`), var32 tid,
var32 tag-id (`0` if null, else `_string_pool.lookup(tag)`).

Repeated tags are written **once** thanks to a new general-purpose JFR **string pool**
(`Dictionary _string_pool`) added to the `Recording` class in `flightRecorder.cpp`. The existing
user-event-type writer was generalized into `writeStringMap(...)` and now both user event types and
the span string pool flow through it.

### 4.5 The `Contextual` annotation ("Make Spans Contextual")

The branch adds a new JFR annotation type **`jdk.jfr.Contextual`** (`T_CONTEXTUAL = 210`,
flag `F_CONTEXTUAL = 0x800`) and applies it to:

- the span's **`tag`** field, and
- the **`method`** field of execution samples.

`jdk.jfr.Contextual` is a real **JDK 25+ JFR annotation**
([JDK-8284453](https://bugs.openjdk.org/browse/JDK-8284453)). It tells JFR analysis tools that the
field is *context* that should be propagated/attached to other events occurring within the same
interval. This is what elevates a span from "just another event" to something tooling can use to
**filter or color all samples that occurred while the span was open** — the feature's whole point.

#### Is the Span API "related to" JDK 25's `@Contextual`? — Yes, directly.

The Span API is essentially **async-profiler's producer side of the `@Contextual` mechanism.**
The relationship has three roles:

| Role | Who | What |
|------|-----|------|
| **Producer** | async-profiler Span API | Writes `profiler.Span` events whose `tag` is `Contextual`, clock-aligned to the samples. |
| **Contract** | `jdk.jfr.Contextual` | A *metadata annotation* meaning "this field is per-thread context, active for its event's `[startTime, startTime+duration]` interval." |
| **Consumer** | JDK 25+ JFR tooling | JMC, `jfr print/view`, `jdk.jfr.consumer`, or a tool like Jeffrey — reads the annotation and attaches the context to every other event on the same thread during the interval. |

Key points:

- **`@Contextual` is a consumer/parser-side contract, not a writer behavior.** It does not change
  how the event is recorded; it changes how a JFR *reader* interprets the recording. The reader
  tracks which contextual fields are active per thread and decorates enclosed events with them.
  Oracle docs: *"Contextual information is data that applies to all events happening in the same
  thread from the beginning to the end of the event with a field annotated with Contextual."*
- **The span's `duration` field is what defines the context window.** An instant event couldn't do
  this — the interval is essential. A `profiler.Span { tag="GET /api/orders", duration=300ms }`
  makes every CPU sample on that thread during those 300 ms inherit `tag="GET /api/orders"`. That is
  exactly what enables "filter/color the flame graph by span."
- **async-profiler does *not* use the JVM's JFR engine.** It generates the JFR binary (and its
  metadata) itself, so it does not import `jdk.jfr.Contextual` or call into the JVM — it
  **re-declares the annotation type in its own metadata** (`T_CONTEXTUAL = 210`) and stamps the flag
  on the field. So the feature is *compatible with* JDK 25's contextual mechanism rather than
  *calling into* it.
- **Producing vs. consuming JDK requirement:** events still record on older JDKs (the annotation is
  just a flag in async-profiler's own metadata), but you need **JDK 25+ tooling** to get the
  automatic context propagation. A pre-25 consumer ignores the annotation and falls back to manual
  timestamp correlation.
- **The two halves are complementary, not redundant.** Span marks the intervals; `@Contextual` tells
  readers how to use them. Without the annotation a `profiler.Span` is just a standalone duration
  event you'd have to time-correlate by hand.

For comparison, JDK's own `@Contextual` example (Erik Gahlin, *What's new for JFR in JDK 25*) shows
an unrelated event decorated with active context:

```
Context: Trace.id   = "00-0af7651916cd43dd8448eb211c80319c-00f067aa0ba902b7-01"
Context: Trace.name = "POST /checkout/place-order"
```

The Span API produces the same kind of context, but its interval is a profiler span and its enclosed
events are the profiling samples.

### 4.6 Internal refactors riding along

- `ProfilingWindow` class is **deleted** and folded into `SpanEvent` (a profiling window is now just
  an internal span: `static SpanEvent profiling_window;` in `profiler.cpp`). The `_start_time` field
  moved up into the base `Event` class.
- `ThreadLocalData` API renamed:
  - `incrementSampleCounter()` → replaced by the timestamp-storing logic in `recordEvent`.
  - `getThreadLocalData()` → `get()`.
  - new async-signal-safe `getIfPresent()` (returns `nullptr` instead of initializing).
- JNI native-registration macro generalized to register both `AsyncProfiler` and `Recording`
  natives (`F(cls, name, sig)`).
- Dead `Profiler::writeLog` overloads removed.
- `jfrMetadata.h` JfrType ids renumbered: `T_NATIVE_LOCK=122`, **`T_SPAN=123`**, `T_USER_EVENT=124`,
  `T_PROCESS_SAMPLE=125`, `T_CONTEXTUAL=210`.

---

## 5. Usage guide

### 5.1 No code coupling required

`one.profiler.Span` can sit in your app with **zero hard dependency** on a running profiler. Calls
are no-ops until async-profiler is loaded — via `-agentpath:libasyncProfiler.so`, dynamic attach, or
`System.loadLibrary`. Spans called *before* the profiler attaches start being recorded the moment it
attaches (proven by `SpanAttachApp`).

### 5.2 Wrap hot paths

```java
long span = Span.start();
try {
    processOrder(order);
} finally {
    Span.end(span, "processOrder");
}
```

### 5.3 Keep frequent spans lean

```java
long span = Span.start();
try {
    cache.lookup(key);
} finally {
    Span.endIfProfiled(span, "cacheLookup");  // only kept if a sample actually landed here
}
```

### 5.4 Explicit timestamps

When the interval isn't bounded by the current call frame:

```java
long t0 = Recording.timestamp();
// ... work elsewhere / across callbacks ...
long t1 = Recording.timestamp();
Span.emit(t0, t1, "asyncStage");          // or Span.emitIfProfiled(t0, t1, "asyncStage")
```

### 5.5 Analyze

Open the resulting `.jfr`. Spans appear as `profiler.Span` events with `startTime`, `duration`,
`eventThread`, `tag`. Because they're `Contextual` and clock-aligned, JFR tooling can filter the
flame graph / sample set down to "only what ran during spans tagged X" or "during spans longer than
N ms".

Reading spans programmatically (from `SpanTests`):

```java
try (RecordingFile rf = new RecordingFile(path)) {
    while (rf.hasMoreEvents()) {
        RecordedEvent e = rf.readEvent();
        if (e.getEventType().getName().equals("profiler.Span")) {
            String tag = e.getString("tag");
            Duration d  = e.getDuration();
            Instant from = e.getStartTime();
            Instant to   = e.getEndTime();
        }
    }
}
```

---

## 6. Tests (reference behavior)

All in `test/test/span/`, Linux-only (`os = Os.LINUX`).

| Test app | What it exercises |
|----------|-------------------|
| `SpanApp` | Unconditional vs optional spans; busy vs idle; null tag. Run with agent `start,event=cpu,interval=1ms,file=%f.jfr`. |
| `SpanApiApp` | Loads profiler via Java API; spans before/during/after a session; asserts only `duringSession` is recorded. |
| `SpanAttachApp` | Uses `Span` in a loop **without** loading the profiler; the test attaches async-profiler mid-run; asserts `attachRequest` spans are captured. |
| `SpanTests` | JUnit assertions over the produced JFR. |

Notable assertions in `SpanTests.spans`:

- Unconditional spans always present: `busyRequest`, `idleRequest`, a null-tag span,
  `idleNormal == IDLE_SPANS (20)`.
- Optional spans kept only when they enclose a sample: `busyOptional` present;
  `count("idleOptional") < count("idleNormal")`.
- Duration recorded: `busyRequest` lasted ~300 ms, asserted `> 200 ms`.
- Clock alignment: CPU samples fall **inside** the `busyRequest` `[start, end]` interval.

---

## 7. Documentation changes

### `docs/IntegratingAsyncProfiler.md` — new "Span API" section

Adds a user-facing section that documents `Span.start/end`, `endIfProfiled`, and the explicit-
timestamp `emit` / `emitIfProfiled`. Key statements from the docs:

- *"The API is static and allocation-free, so it is cheap enough to leave in production code. When
  async-profiler is not running, all calls are no-ops."*
- *"Spans appear as `profiler.Span` JFR events with `startTime`, `duration`, `eventThread` and `tag`
  fields. Tags are deduplicated automatically in the JFR recording. Span timestamps use the same
  clock as the profiling samples, so they line up exactly."*
- *"`one.profiler.Span` can be used independently of the rest of the API; async-profiler connects to
  it automatically whenever it is loaded (via `-agentpath`, dynamic attach, or `System.loadLibrary`)."*

### `docs/ProfilingNonJavaApplications.md` — wording fix

Clarifies the native `asprof_get_thread_local_data` docs: the struct contains *a field* (not "a
pointer") that increments per sample — matching the new timestamp-storing behavior of
`sample_counter`.

---

## 8. Status & caveats

- **Fresh / unmerged** — 4 commits (June 3–4 2026), feature branch only. Not in a release.
- **Linux-only tests** so far (`os = Os.LINUX`).
- **JDK 25 dependency for full value** — `jdk.jfr.Contextual` is what makes tooling treat the tag as
  propagated context. Events still record on older JDKs, but the context-propagation/filter
  experience needs JDK 25+ tooling.
- Lives alongside the existing **unstable** native `asprof_get_thread_local_data` API, whose
  `sample_counter` semantics this branch changed (increment → last-sample timestamp).

---

## 9. What's native vs. what a reader reconstructs

Everything interesting Jeffrey can do with spans is a **reader-side derivation**, not an
async-profiler capability. The recording is deliberately minimal — four flat fields per span, one
thread, a deduplicated tag. There is **no span id, no parent id, no depth, no correlation id, no
scope stack** anywhere in the Java API or the native code (confirmed against §4.4: a span is just a
`SpanEvent { _start_time, _end_time, _tag }` written under `OS::threadId()`).

| What the recording literally contains | What a reader (Jeffrey) can derive from it |
|---|---|
| `profiler.Span { startTime, duration, eventThread, tag }` — flat | **Parent/child hierarchy** by time-interval containment on one thread (§10) |
| One `eventThread` per span (the caller of `end`/`emit`) | **Span-scoped flamegraph / timeseries / subsecond** by filtering samples to `[start,end]` on that thread (§11) |
| Tags interned in a string pool (bounded cardinality) | **Tag-grouped aggregation** + **differential slow-vs-fast** comparisons (§13) |
| TSC-aligned timestamps, comparable to `jdk.ExecutionSample` (§4.1) | **Sample ↔ span correlation** with no clock conversion (§11, §12) |
| `@Contextual` flag on `tag` (metadata only — async-profiler does nothing with it, §4.5) | **Context decoration**: attach the set of open span tags to each enclosed sample (§10) |

The rest of this section is the analysis of those derivations and exactly which existing Jeffrey
building blocks implement them.

---

## 10. Parent/child spans — reconstructed by containment

**There is no native hierarchy.** Two spans open on the same thread share nothing in the recording
that links them (§4.4). Nesting is *inferred* by the reader.

**Rule.** On a single `eventThread`, span **B is a child of span A** iff
`A.start ≤ B.start` and `B.end ≤ A.end`. The parent of B is the *innermost* such A.

**Algorithm (stack sweep).** Group spans by thread; within a thread sort by
`(start ASC, duration DESC)`; walk a stack — pop any open span whose `end < current.start`, then the
current top of stack (if any) is the parent, push current. O(n log n), pure in-memory; runs over the
rows the existing `JdbcSpanRepository.LIST_SPANS` already returns (each `SpanRecord` carries
`startMillisFromBeginning`, `durationNanos`, `osThreadId`, `javaThreadId`, `threadName`, `tag`). It
fits naturally in `SpanManagerImpl` as a derived parent pointer + depth — **no schema change, no new
JFR field.**

```
one thread's timeline ───────────────────────────────────────────────▶ t

  [== A: "GET /api/orders"  (320ms) ===========================]
      [== B: "loadOrders" (140ms) =====]   [== C: "render" (90ms) ==]
         [= D: "db.query" (60ms) =]

reconstructed tree:                A
                                 ┌─┴─┐
                                 B   C
                                 │
                                 D
```

**Caveats (state these honestly in any UI):**
- **Equal boundaries** → the longer-duration span is treated as the outer one (the `duration DESC`
  tie-break); truly identical `[start,end]` pairs are ambiguous and should be shown as siblings.
- **Thread reuse over time** — a pooled thread handles many unrelated requests; containment is only
  meaningful within one contiguous burst of activity, which the sweep already respects (a new root
  starts whenever the stack empties).
- **Virtual threads** — `eventThread` is the *carrier* OS thread (§3, §6). A virtual thread that
  unmounts and remounts on a different carrier mid-span makes carrier-based containment unreliable;
  prefer the Java thread id when present and flag VT spans.
- **Async hand-off breaks containment** — a logical parent on thread X with child work on thread Y
  cannot be linked (no correlation id exists, and spans never cross threads, §2). This is a hard
  limit of the span model, not of the reconstruction.

**JDK-25 `@Contextual` angle.** A JDK-25 reader treats the `tag` field as context that **stacks per
thread**, so every enclosed sample inherits the *set* of currently-open tags — which is exactly the
containment relation above, expressed as decoration instead of a tree. Jeffrey can produce the same
result today, without JDK 25 tooling, by computing containment itself and tagging samples whose
timestamp falls inside each open span.

---

## 11. Span-scoped flamegraph / timeseries / subsecond (per instance)

This is the single most reusable idea, and **it needs no new query infrastructure.** Jeffrey's
graph pipeline is already time- and thread-scoped: a `GraphParameters(eventType, timeRange,
threadInfo, …)` flows through `EventQueryConfigurer.withTimeRange(...).withSpecifiedThread(...)` into
`FlamegraphDataProvider.provideFrame()` / `TimeseriesDataProvider.provide()` /
`DbBasedSubSecondGeneratorImpl`, and the underlying DuckDB queries already carry `:from_time` /
`:to_time` and per-thread predicates.

Given one span instance, the scope is `[span.start, span.end]` **on `span.thread`**:

```java
// from a SpanRecord (already returned by JdbcSpanRepository.LIST_SPANS)
long startMs = span.startMillisFromBeginning();
long endMs   = startMs + span.durationNanos() / 1_000_000;

RelativeTimeRange window = new RelativeTimeRange(startMs, endMs);          // relative to recording start
ThreadInfo thread = new ThreadInfo(span.osThreadId(), span.javaThreadId(), span.threadName());

GraphParameters params = new GraphParameters(Type.EXECUTION_SAMPLE, window, thread, /* … */);
Frame flame = new FlamegraphDataProvider(eventStreamRepository, params).provideFrame();
// TimeseriesData ts = new TimeseriesDataProvider(eventStreamRepository, params).provide();
```

Notes:
- **Distinct from the existing `SpanTagFlamegraphs.vue`**, which scopes to a *tag's overall window*
  (`min(start) … max(end)` across **all** spans of that tag, possibly across unrelated threads). That
  is coarse and mixes in samples from other requests. The per-**instance** scope above is precise:
  one request, one thread, one interval.
- **`endIfProfiled` synergy** — spans recorded with `endIfProfiled` enclose ≥1 sample by
  construction (§3.2), so their per-instance flamegraph is guaranteed non-empty. Plain `end` spans
  may be sample-free (e.g. an idle/off-CPU span) — show an explicit "no samples in this interval"
  state rather than an empty graph.
- **Swap the event type** to reuse the same window for other lenses: `wall` for off-CPU/latency,
  allocation types for memory, lock/park types for contention — "what was this span blocked on" vs.
  "what burned CPU."

---

## 12. HTTP-exchange-rooted "request explorer" (the headline)

Jeffrey **already models** `jeffrey.HttpServerExchange` (and `HttpClientExchange`,
`GrpcServerExchange`, `GrpcClientExchange`) in `Type` / `EventTypeName` — each an interval event with
start + duration + thread. Treat one exchange as a **root context** and assemble everything beneath
it by composing §10 and §11:

1. **Child spans** = `profiler.Span` rows contained in the exchange interval on the same thread
   (§10 containment, with the exchange itself as the synthetic root).
2. **Samples** in the exchange window on that thread → per-instance flamegraph + timeseries (§11),
   built straight from the providers with `timeRange = [exchange.start, exchange.end]`,
   `threadInfo = exchange.thread`.

The result is a single **request explorer**: the HTTP request on top, the nested operation spans as
a Gantt below, and a flamegraph + timeseries beside it — *"what did this one request do, and what was
the CPU actually doing while each operation was open?"*

```
 HTTP  [===== jeffrey.HttpServerExchange  GET /api/orders   312 ms =====]
 spans     [== auth (20ms) ==]                                              ┌── flamegraph (this window,
           [======== loadOrders (180ms) ========]                          │   this thread) ──────────┐
                  [= db.query (95ms) =] [= map (40ms) =]                    │  handleOrders        100% │
                                          [==== render (70ms) ====]         │   ├ loadOrders        58% │
 samples   • • •   •  • •   • • • •    •    • •   • • •  •   •   •           │   │  └ db.query       31% │
           └────────── timeseries (samples/100ms in window) ─────────┘     │   └ render            22% │
                                                                           └──────────────────────────┘
```

- **Same recipe for `GrpcServerExchange`** — any interval event with a thread can be a root.
- **Honest limit:** correlation is **time + thread containment**, *not* a propagated trace id.
  Cross-thread async work spawned by the request (executor hand-off, reactive pipelines) is **not**
  pulled in — it runs on other threads and async-profiler spans never cross threads (§2). The view
  shows "this request's synchronous work on its serving thread," which is exactly what sampling +
  spans can prove, and nothing it can't.
- **Jeffrey mapping:** a read-only correlation in `SpanManager` (exchange row → contained spans +
  scoped graph params) reusing the §10/§11 blocks; the UI could extend `SpanEventsModal.vue` (which
  already lists per-thread events during a window) into a request-rooted tree. Implementation-depth
  blueprint is intentionally left to the candidates doc — see §14.

---

## 13. Other patterns worth building

- **Differential slow-vs-fast flamegraph.** For one tag, build the per-instance flamegraph (§11) of
  the **p99** spans and subtract the flamegraph of the **median** spans. What remains is the extra
  work that makes the slow ones slow — the highest-signal view for "why is this endpoint
  occasionally slow." Reuses §11 twice plus a frame diff.

  ```
   slow (p99)            fast (median)            diff = slow − fast
   handle      100%      handle      100%         + db.retry        +34%
    ├ db.query  72%       ├ db.query  41%         + serialize.gzip  +11%
    └ serialize 24%       └ serialize 18%         - (cache hit path)  −0%
  ```

- **Tag-aggregated (union) flamegraph.** Merge the per-instance windows of *all* spans of a tag
  (precise union of intervals on their threads) — strictly better than the current single
  `min … max` tag window, which sweeps in unrelated samples between span instances.
- **Span boundaries as overlays.** Draw span start/end markers on the existing timeseries and
  subsecond views, so sample density is read *against* the operations that produced it.
- **Spans as a first-class time+thread selector.** A span click is just a preset
  `{timeRange, threadInfo}` — the same payload `TimeSeriesChart.vue` already emits via
  `update:timeRange` on brush. Any existing flamegraph/timeseries view can be driven by it.
- **On-CPU vs off-CPU split within a span.** Same window, two event types (`cpu` vs `wall`): how
  much of a 300 ms span was running vs. waiting.

---

## 14. Jeffrey today: built vs. new

Jeffrey **already ships a working span feature** — so the items below are
extensions, not greenfield. Current state: spans are read from the `events` table
(`event_type = 'profiler.Span'`, tag via `json_extract_string(fields,'$.tag')`) by
`JdbcSpanRepository`, aggregated, and visualized.

| Capability | Status | Where |
|---|---|---|
| Tag statistics, overview, heatmap, slowest | **Built** | `SpanManagerImpl`, `AsyncProfilerSpansController` (`/spans/overview\|tags\|heatmap\|slowest`) |
| Per-thread event correlation during a span | **Built** | `JdbcSpanRepository.EVENTS_FOR_THREAD`, `SpanEventsModal.vue` |
| Tag-**window** flamegraphs (coarse) | **Built** | `SpanTagFlamegraphs.vue` |
| Parent/child hierarchy by containment (§10) | **New** | derive in `SpanManagerImpl` from `LIST_SPANS` |
| Per-**instance** flamegraph/timeseries (§11) | **New** | reuse `GraphParameters` + `FlamegraphDataProvider`/`TimeseriesDataProvider` |
| HTTP/gRPC-exchange-rooted explorer (§12) | **New** | correlate `HttpServerExchange` → contained spans + scoped graphs |
| Differential slow-vs-fast, union flamegraph (§13) | **New** | compose §11 + frame diff |

**Companion docs (single-responsibility — cross-reference, don't duplicate):**
- [`async-profiler-span-api-jeffrey-candidates.md`](./async-profiler-span-api-jeffrey-candidates.md)
  — where **Jeffrey should instrument itself** with spans (AI/LLM calls, JFR ingestion, heap-dump
  indexing, REST/gRPC boundaries), with ranked `file:line` candidates and a starter set.
- [`span-visualization-research.md`](./span-visualization-research.md) — industry visualization
  patterns (Pyroscope, Datadog, JMC, JDK-25 `@Contextual`) that informed §10–§13.

> **Status reminder (§8):** the async-profiler Span API is on an **unreleased feature branch**.
> The §10–§13 features read real `profiler.Span` events from the profile database, which depend on
> the `span-api` build landing and being run as the recording agent.

---

## Appendix — source pointers (branch `span-api`)

| Concern | File |
|---------|------|
| Public `Span` API | `src/api/one/profiler/Span.java` |
| State + clock | `src/api/one/profiler/Recording.java` |
| JNI bridge, `RecordingAPI`, clock swap | `src/javaApi.cpp` / `src/javaApi.h` |
| `SpanEvent`, `EventType::SPAN`, base `Event._start_time` | `src/event.h` |
| JFR type/field defs, `Contextual` annotation | `src/jfrMetadata.cpp` / `src/jfrMetadata.h` |
| `recordSpan`, string pool, `recordEvent` counter logic | `src/flightRecorder.cpp` |
| `getIfPresent` / `get`, sample counter | `src/threadLocalData.h` |
| `ProfilingWindow` → `SpanEvent` fold-in | `src/profiler.cpp` / `src/profiler.h` |
| User docs | `docs/IntegratingAsyncProfiler.md`, `docs/ProfilingNonJavaApplications.md` |
| Tests | `test/test/span/Span*.java` |

### Jeffrey-side reuse pointers (for §10–§14)

| Concern | File (under `jeffrey-microscope/`) |
|---------|------|
| Span rows + queries (`LIST_SPANS`, `EVENTS_FOR_THREAD`) | `profiles/profile-sql-persistence/.../jdbc/JdbcSpanRepository.java` |
| `SpanRecord` (`startMillisFromBeginning`, `durationNanos`, `osThreadId`, `javaThreadId`, `threadName`, `tag`) | `profiles/profile-persistence-api/.../api/SpanRecord.java` |
| Span aggregation manager | `profiles/profile-management/.../manager/SpanManagerImpl.java` |
| Span REST endpoints | `core-microscope/.../web/controllers/profile/AsyncProfilerSpansController.java` |
| Span manager factory wiring | `profiles/profile-management/.../configuration/ProfileFactoriesConfiguration.java` |
| Time+thread scoping primitive | `profiles/profile-persistence-api/.../api/EventQueryConfigurer.java` (`withTimeRange` / `withSpecifiedThread`) |
| Graph params + providers | `profiles/flamegraph/.../provider/FlamegraphDataProvider.java`, `TimeseriesDataProvider.java`; `profiles/subsecond/.../DbBasedSubSecondGeneratorImpl.java` |
| `RelativeTimeRange` / `ThreadInfo` | `shared/common/.../model/time/RelativeTimeRange.java`, `shared/common/.../model/ThreadInfo.java` |
| HTTP/gRPC exchange event types | `shared/common/.../model/EventTypeName.java`, `Type.java` (`HTTP_SERVER_EXCHANGE`, `GRPC_SERVER_EXCHANGE`, …) |
| Span UI components | `pages-microscope/src/components/span/` (`SpanTagFlamegraphs.vue`, `SpanEventsModal.vue`, `SpanHeatmapChart.vue`, …) |
