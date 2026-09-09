---
name: report
description: The shape and the evidence rules for any performance finding drawn from a Jeffrey Microscope profile — what a finding must carry before it is worth writing down, how a figure is quoted and against what denominator, what caps a claim at medium confidence, and how the questions a recording cannot answer are reported apart from the findings. Use whenever writing up an analysis, summarising an investigation, answering "what did you find", or handing conclusions to another person or agent — once analyze-jfr, analyze-heap, compare-jfr, regression-check or advise-jfr has done the reading.
---

# Reporting a finding

A performance report is an argument, and an argument needs evidence. The standard is one sentence:
a reader who never saw a tool result must be able to re-run every number in the report. Everything
below follows from that.

## The shape

Findings ranked by impact — the share of the recording's wall clock, of its samples, or of its heap
that each one accounts for — never by how confident the wording sounds. Each in this shape:

> **Symptom** — what the user or the system observes.
>
> **Evidence** — the tool call, its arguments, and the figures exactly as the tool printed them.
>
> **Interpretation** — what the figures mean, and why this explanation rather than another.
>
> **Recommendation** — the change, at a named location; or "none yet", and what would be needed.
>
> **Confidence** — high, medium or low, and what would raise it.

Then, separately and always: **Not assessed** — what this profile could not answer, and why.

Keep it short. Three findings that carry their evidence beat twelve that do not.

## The evidence rule

Every quantitative claim names the call that produced it, with the profile id and the arguments
that shaped the result:

> `jdk.JavaMonitorEnter` on `com.example.SessionCache` accounts for 41.2 s of blocked time across a
> 300 s recording — 13.7% of wall clock.
> Evidence: `blocking_monitors profileId=p-7c1` → `SessionCache` total 41,203 ms over 3,812
> contentions; recording length from `profiles_summary` (`startedAtMillis` to `finishedAtMillis`)
> = 300.4 s.

If you cannot name the call, you cannot make the claim. Delete it, or go and measure it.

Tool names above omit the prefix your client puts in front of them —
`mcp__plugin_microscope_jeffrey__` for the Claude Code plugin, `mcp__jeffrey__` in Codex and for any
hand-registered server, `mcp_jeffrey_` in Gemini CLI, which spells it with single underscores.

## Shares and rates, with the denominator stated

The exports already speak in shares: a frame's `total` and `self` are percentages of *that
export's* total. Quote them as such, and say what the total was — how many samples, bytes or
nanoseconds — and what shaped it: the `thresholdPct`, a `startMs`/`endMs` window, `useWeight`,
`excludeIdle`. A share of a filtered export is not a share of the recording.

Everything else needs a denominator before it means anything:

- **events → per second**, using the recording length from `profiles_summary` or `profiles_get`
- **pause time → fraction of wall clock**: 200 ms of GC pause is 0.07% of a five-minute recording
  and 10% of a two-second one; the count of collections says nothing on its own
- **allocation → MB/s**, and say it is estimated from sampled weights when the source is
  `jdk.ObjectAllocationSample`
- **a heap figure → retained bytes**, never shallow; and against the heap's total

Counts from two recordings of different lengths are not comparable. `compare_list` reports both
lengths and `compare_movements` scales the baseline onto the primary's; a comparison you do by hand
has to do the same, or say that it did not.

## Confidence, honestly

| Level | When |
|---|---|
| **High** | Direct measurement of the thing itself — a trace's span timings, a monitor's blocked time, a dump's retained size — with a large sample, corroborated by a second tool |
| **Medium** | A strong single-tool signal, or an inference from a well-understood mechanism |
| **Low** | A heuristic, a small sample, a correlation in time only, or a source that is known to be approximate |

Things that cap a finding at **medium** at most, and must be said out loud when they apply:

- **Sampled data.** `jdk.ExecutionSample`, `jdk.CPUTimeSample`, `profiler.WallClockSample` and
  `jdk.ObjectAllocationSample` are samples, not a census. A frame at 0.4% of 800 samples is three
  samples.
- **A lossy sampler.** `profiles_samplerHealth` reports the CPU-time samples the kernel dropped.
  Loss lands during the busiest moments, so a lossy recording *understates* its own hot paths —
  say the loss share beside any share you quote from it.
- **The auto-analysis rules.** `jvm_autoAnalysis` applies fixed thresholds that know nothing about
  this service's normal behaviour. A rule that fired is a lead worth following into the matching
  `jvm_` section for the figures; it is not a diagnosis, and its `solution` is not a recommendation
  until the figures support it.
- **Leak candidates.** `memory_leakCandidates` lists objects that survived collections. Survival is
  not retention: without a heap dump and a GC-root path it stays a candidate.
- **A comparison whose `compare_list` notes fired** — a duration mismatch, an event type on one
  side only, a thin profile. Report movements from such a pair as suggestive, not measured.
- **Time overlap.** "During the same window as" is concurrency, not cause. Report it as
  *concurrent with*, and prove cause with a code path or a fix that measurably helped.
- **A frame you never read in source.** Until the file has been opened — with your own tools or
  through `ide_resolve` — a finding names a frame, not a line.

## Absence of evidence

A profile can only answer what its recording captured. Read `capabilityGaps` in `profiles_summary`
before you believe a negative result, and carry every gap that touches the question into the
**Not assessed** section, with the remedy the gap names.

- "No allocation hotspots found" is wrong when the allocation group is in `notRecorded`. The true
  statement is "allocation was not assessed: the recording holds no `jdk.ObjectAllocationSample`
  events", plus how to enable it next time.
- `blocking_`, `io_` and `jvm_jit` read event types that are **threshold-gated**: a recording holds
  none either because nothing crossed the threshold or because the profiler was never asked. The
  tools say which, and the two are different sentences in a report.
- A feature in `disabledFeatures` — traces, the HTTP or JDBC dashboards, a heap dump — was never
  instrumented or captured. Its absence is a fact about the recording, not about the application.
- A rule under `notEvaluated` in `jvm_autoAnalysis` had no events to run on. It did not pass.
- A heap report that has not been computed is built by `heap_prepare`; an empty retained ranking
  before that is a signal to prepare, never a finding that nothing retains memory.

## Reproducibility

End with the profile id — both ids for a comparison, saying which is the baseline — and the calls
in the order they were made, with their arguments, so the reader or the next agent can re-run
the whole thing:

```
profiles_summary profileId=p-7c1
blocking_overview profileId=p-7c1
blocking_monitors profileId=p-7c1
flamegraph_export profileId=p-7c1 eventType=jdk.JavaMonitorEnter useWeight=true thresholdPct=1
```

For a regression claim, the two profiles and the `compare_list` call are the reproduction.

## What a recommendation must contain

Not "reduce allocations", but: the file and line, read from source; the change; the expected
effect with its basis; and how it will be verified.

> `OrderService.reprice` (`src/main/java/com/example/OrderService.java:118`) allocates a new
> `HashMap` per call inside the pricing loop and carries 34% of sampled allocation weight
> (`flamegraph_export eventType=jdk.ObjectAllocationSample useWeight=true`). Hoisting it out of the
> loop should remove most of that share. The effect is on allocation rate and young-collection
> frequency, not on p99 directly — confirm with a re-profile and `compare_movements` on the same
> event type with the same export parameters.

Keep **configuration findings** apart from code findings and labelled as such — a pool that ran
out of connections, a flag left at its ergonomic default, a container quota the scheduler
enforced. They are real, often the largest single win, and none of them is an edit; they are
verified on the application's next run, not by a test. The `advise-jfr` skill has the full shape.

If you did not locate the code, say so and give the frame rather than inventing a path.

## Where the report is written

When a `profile-analyst`, `heap-triage` or `profile-lead` agent did the reading, it returns the
findings with their evidence in this shape and stops there: it has no file tools and makes no
recommendations. Mapping a frame onto the checkout, the recommendation, and every question put to
the user belong to the session that delegated — add them there, in the same shape, before the
report goes to the reader.

## Never

- Report a number you did not see in a tool result in this session. Never estimate, round a figure
  you did not see, or carry a total from one export into another.
- Present a threshold breach as a diagnosis.
- Claim an improvement without a measured comparison. "This should be faster" is a hypothesis and
  is labelled as one; `compare_movements` on a re-profile is the measurement.
- Swap the primary and the baseline to make a result read better.
- Let a gap pass as a clean result. A question the recording could not answer is reported as such,
  in its own section, every time.
