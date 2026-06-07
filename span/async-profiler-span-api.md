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

## 9. Relation to Jeffrey

Jeffrey is a JFR analysis tool. If/when this lands in async-profiler, `profiler.Span` becomes a new
JFR event type Jeffrey could ingest and visualize:

- A new event type to parse (`recording-parser/`), with fields `startTime`, `duration`,
  `eventThread`, `tag`.
- Natural fit for the **timeseries** / **subsecond** views (intervals on a thread timeline) and for
  **flamegraph filtering** — filter sampled stacks to those whose timestamps fall inside a span of a
  given tag (the `Contextual` semantics map directly onto a "filter flamegraph by span" feature).
- Tags are constant-pool strings, so cardinality is bounded and groupable.

This is a *potential future integration*, not anything implemented in Jeffrey today.

A separate companion note —
[`async-profiler-span-api-jeffrey-candidates.md`](./async-profiler-span-api-jeffrey-candidates.md) —
maps the concrete places in Jeffrey Microscope where **Jeffrey instrumenting itself** with Spans
would be most valuable (AI/LLM calls, the JFR ingestion pipeline, heap-dump indexing, the REST/gRPC
boundaries), with ranked `file:line` candidates and a recommended minimal starter set.

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
