---
name: advise-jfr
description: Turns a Jeffrey profile into concrete code changes in the current repository — maps the hottest CPU, wall-clock, allocation and blocking frames, the slowest traces, the waiting on locks and I/O, and the database and HTTP work to real source, recommends minimal behaviour-preserving edits, applies them on request and verifies with the tests and a re-profile. Use whenever the user asks what to change, optimise or fix based on a profile, JFR recording, flamegraph or trace, why an endpoint is slow and what to do about it, or a hotspot is known and the question is what to do about it.
argument-hint: "[profile-id | recording-file] [cpu|wall|alloc|lock|latency|waiting|memory]"
---

# From a profile to a code change

The analysis tools read a profile; this skill is what happens after. It takes the measured call
trees, finds the code behind the heaviest frames in the checkout you are sitting in, and proposes
the smallest change that would reduce the measured cost — then, only when asked, makes it and
checks that it helped.

Requested scope: `$ARGUMENTS` — substituted by Claude Code; where a client does not substitute it,
take the same scope from the request itself. A profile id or a recording file, then optionally one
area (`cpu`, `wall`, `alloc`, `lock`, `latency`, `waiting`, `memory`). Empty means the profile the
conversation is about (or the most recently modified one in `profiles_list`) and whatever evidence
it actually carries.

Tool names below omit the prefix your client puts in front of them
(`mcp__plugin_microscope_jeffrey__` for the Claude Code plugin, `mcp__jeffrey__` in Codex and for any
hand-registered server).

Two phases with a stop between them — **recommend**, then **change** — because an edit made
before the recommendation has been read cannot be reviewed on its own terms. Track progress:

```
- [ ] 1. Profile resolved, commit compared with HEAD
- [ ] 2. Evidence chosen from what the profile carries — not everything, what it has
- [ ] 3. Each source read the way its own document says
- [ ] 4. Every code finding tied to a frame and to source that was actually read
- [ ] 5. Recommendation written, code and configuration findings kept apart — STOP
- [ ] 6. Accepted edits made, built, tested, re-profiled where possible
```

## 1. Resolve the profile and its commit

Start from `profiles_list`, or `recordings_analyzeFile` when the user named a file (the
`analyze-jfr` skill covers that path). Then `profiles_get` and read `recordingCommit`:

- equal to `git rev-parse HEAD` — say so in one line and continue;
- different — say so **before anything else**, name both commits, and ask whether to check the
  recording's commit out. Do not switch branches unasked. A profile of another commit describes
  code that may have moved, been renamed or been deleted; mapping it onto the wrong tree produces
  confident nonsense.
- `null` — say the commit is unknown, not that it matched. (Tagging the recording with
  `git.commit` at build time fixes that for next time.)

When the code being advised on is not this checkout but one open in the reader's IntelliJ,
`ide_windows` reports the branch and HEAD commit of every open window and marks which of them is on
the commit the recording was built from — the same check, for the tree the reader is actually
looking at.

## 2. Choose the evidence

**Ask what the profile carries before asking anything else.** One call to `profiles_features` says
which families can answer at all — its `disabledFeatures` rules out a whole family, its
`eventTypes` names what was captured — and `flamegraph_list` splits the graphable types into
`available` and `notRecorded`. Analysing every family unconditionally is a fishing expedition: it
costs a dozen calls and buries the two findings that matter under twelve that do not. Pick what the
profile has and the question needs, and say what you skipped.

If the user named an area, do that one. Otherwise start with the flamegraph groups, and add the
others when the profile carries them.

### The flamegraph groups — where the time was spent

| Group | Event type (first present wins) | Export options |
|---|---|---|
| `cpu` | `jdk.ExecutionSample`, else `jdk.CPUTimeSample` | defaults |
| `wall` | `profiler.WallClockSample` | defaults |
| `alloc` | `jdk.ObjectAllocationSample`, else `jdk.ObjectAllocationInNewTLAB`, else `jdk.ObjectAllocationOutsideTLAB` | `useWeight: true` (bytes, not call count) |
| `lock` | `jdk.JavaMonitorEnter`, else `jdk.JavaMonitorWait`, else `jdk.ThreadPark` | `useWeight: true` (nanoseconds blocked) |

Report a `notRecorded` group rather than analysing it, with the async-profiler option that would
capture it next time: `event=ctimer` (cpu), `wall=10ms`, `alloc=512k`, `lock=10ms`.

### Everything else — and how it reaches source

A flamegraph is not the only evidence, and for several real problems it is the wrong one: a thread
blocked on a lock or a socket is not on-CPU, so it is never sampled and the graph reports the
application as idle rather than as waiting. Each row below says what the evidence proves and how it
gets you to a line of code, because a finding that cannot reach source is not something this skill
can act on.

| Area | Start with | How it reaches source |
|---|---|---|
| `latency` | `traces_overview`, then `traces_operations`; `traces_notifications` first when any is `CRITICAL` or `HIGH` | `traces_slowestTraces` → `traces_traceExport` → `traces_spanFlamegraphExport`, which gives frames for **one span** — continue from step 4 with that export rather than the whole-recording one |
| `latency`, per population | `traces_attributeKeys` → `traces_attributeValues` | A value whose p95 stands apart names the population; `traces_attributeSearch` gives its traces, and one of them exports to frames |
| `waiting`, locks | `blocking_overview` → `blocking_monitors` | The monitor class names the lock; find the `synchronized` block or `Lock` in the checkout. `blocking_pinnedThreads` points at a `synchronized` block on a carrier thread |
| `waiting`, I/O | `io_overview` (`SOCKET`, `FILE`) → `io_endpoints` → `io_slowest` | The target names the dependency or file; the fix is at the **calling** code — fewer round trips, batching, caching, or not waiting |
| `memory` | `memory_allocations` | The types ranked here are the other axis from the allocation flamegraph; take a type back to the `alloc` export to find the site |
| database | `jdbc_overview` → `jdbc_statementGroup` | The statement text names the query; find where it is issued. `jdbc_pools` is a configuration finding, not a code one — see step 5 |
| HTTP, gRPC | `http_overview` / `grpc_overview` (`direction`: `SERVER` or `CLIENT`) | An inbound endpoint is your handler; an outbound call is a dependency, and the change is at the call site |
| when, not where | `timeline_hotWindows` | Not a finding on its own. It gives `startMs`/`endMs` to scope an export to, which is what makes a burst visible that a whole-recording graph averages away |

Two that shape a recommendation without being one: `profiles_samplerHealth`, because a recording
that dropped a large share of its samples understates its own hot paths and every percentage below
inherits that; and `jvm_flags` plus `jvm_container`, because a tuning claim is only worth making
against the values the JVM really ran with.

## 3. Read each source the way it asks to be read

One export per flamegraph group, whole recording, default threshold. Every export opens with its own reading
instructions and an analysis section written for that event type — what counts as a hotspot, what
the frame tags mean, what to skip. That document governs, not generic flamegraph lore. Lower
`thresholdPct` only to chase one specific path deeper.

Send the groups to a **`profile-analyst`** agent — `microscope:profile-analyst` from the Claude
Code plugin, or the Codex custom agent from `codex/agents/profile-analyst.toml` — one delegation per
group and all of them in a single message so they run at once. Each returns the hot frames with
their shares; four raw exports would otherwise crowd out the source reading that step 4 depends on.
Call `flamegraph_export` here only when you are working a single group and want the document in
front of you, or when your client has no agent to delegate to.

## 4. Ground every finding in source

The export has call paths and numbers; a frame carries a line only when every sample at it agreed on
one, so treat a printed line as a place to start reading and its absence as no information. Map the
heaviest frames to the checkout with Read, Grep and Glob — or, when the reader has IntelliJ open
with the Jeffrey plugin, ask it: `ide_resolve` returns the file and line for a class and method and
flags a position that is decompiled, imprecise, or in a file edited since the recording. It is worth
a call for anything a grep would get wrong — a nested class, a Kotlin facade, an inherited or
overloaded method — and for library code, where `ide_source` reads what is not in this repository at
all. It does not move the reader's editor; `ide_open` does, so save that for when they ask to be
shown something.

Hold to these rules — they are what separates a recommendation from a guess:

- **Never name a file, method or line you have not read.** Open it first.
- **Tie each finding to a frame and its share** (`total`, `self`, the percentage) from the export,
  so the reader can check the claim against the profile.
- **Prefer a few high-impact findings** over many speculative ones. Frames under 1 % are noise
  unless the user is chasing something specific.
- **Say when a hotspot cannot be located** — a library you cannot patch, generated code, a method
  that no longer exists at this commit. Note it once and move to the next frame in this repository.
- Distinguish leaf work (`self ≈ total`) from orchestration (`self << total`): recommend changes
  to the former, walk into the latter.

## 5. Recommend, then stop

Write the recommendation in this shape:

- **Summary** — what dominates, across every source you read, in two or three sentences. Say which
  evidence you used and which you skipped because the profile does not carry it.
- **Code findings.** One **`### <file>: <method>`** section each: the cause, why it is hot according
  to the profile (the frame and its share, the operation and its p95, the monitor and its blocked
  time — whichever measured it), and the proposed change in prose — minimal, behaviour-preserving,
  reviewable on its own. No diffs in this phase.
- **Configuration findings**, kept separate and labelled as such. A pool that ran out of
  connections, a JVM flag left at an ergonomic default, a container quota the scheduler enforced —
  these are real and often the largest single win, and none of them is a code change. Presenting one
  as an edit would misrepresent both the fix and the risk. Give the setting, its current value, the
  evidence, and what to change it to.
- **Not located** — hotspots that could not be mapped to this repository, if any.

Then ask which findings to apply, and wait for the answer. This is the gate.

A finding that reaches neither source nor a setting is not a finding yet. Say what it measured and
what would be needed to act on it, rather than inventing a change to attach to it.

## 6. Change and verify

For each accepted finding:

1. Make the smallest edit that implements it; one reviewable change beats a sweeping rewrite.
2. Run the project's build and tests the way a contributor would.
3. If the recording can be reproduced (a benchmark, a load script, a command the user names),
   run it, `recordings_analyzeFile` the new recording, and export the **same group with the same
   parameters** — keep threshold and options identical, or a difference in pruning will read as a
   change that is not there. Report the delta on the frames you changed.
4. Never claim a saving you did not measure. Without a re-profile, the estimate is capped at the
   frame's own `total` share: a change cannot save more time than the frame used.
5. A configuration finding is verified differently, and usually not by you: a pool size or a flag
   takes effect on the next run of the application, not on the next test. Say what to watch for in
   that run — the timeout count back to zero, the throttling gone — rather than reporting it fixed.

## Latency rather than throughput

The order matters here more than anywhere else, because the wrong first question wastes the whole
investigation. A `CRITICAL` or `HIGH` notification is the application's own account of what went
wrong and comes before any frame: `traces_notifications` usually names the cause a span tree only
shows the cost of. Then the population before the exemplar — `traces_operations` for where the
wall-clock went, or `traces_attributeValues` when one tenant or customer is slow and the rest is
fine — and only then one trace, its span tree, and the frames inside the slow span.

Two traps worth naming. A request that is slow while every statement is fast is waiting for a
connection: that is `jdbc_pools`, it appears nowhere else, and no change to a query will fix it. And
an outbound call that is slow belongs to a dependency — the local change is to call it less often,
batch it, cache it or stop waiting for it, not to optimise code you do not own.

## When something is missing

- `flamegraph_list` reports no graphable event types → a heap dump, or a recording without
  samples; there is nothing to advise on from a flamegraph. For a heap dump, the `analyze-heap`
  skill applies.
- The profile's commit differs from `HEAD` and the user does not want to switch → analyse anyway,
  and say in the summary that every file reference was checked against a different commit than
  the one profiled.
- The code behind the top frame is in a dependency → say so, name the calling frame in this
  repository, and advise there (fewer calls, a cheaper API, caching) rather than inside the library.
