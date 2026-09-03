---
name: advise-jfr
description: Turns a Jeffrey profile into concrete code changes in the current repository — maps the hottest CPU, wall-clock, allocation and blocking frames to real source, recommends minimal behaviour-preserving edits, applies them on request and verifies with the tests and a re-profile. Use whenever the user asks what to change, optimise or fix based on a profile, JFR recording or flamegraph, or a hotspot is known and the question is what to do about it.
argument-hint: "[profile-id | recording-file] [cpu|wall|alloc|lock]"
allowed-tools: mcp__plugin_microscope_jeffrey__* mcp__jeffrey__*
---

# From a profile to a code change

The analysis tools read a profile; this skill is what happens after. It takes the measured call
trees, finds the code behind the heaviest frames in the checkout you are sitting in, and proposes
the smallest change that would reduce the measured cost — then, only when asked, makes it and
checks that it helped.

Requested scope: `$ARGUMENTS` — a profile id or a recording file, then optionally one group
(`cpu`, `wall`, `alloc`, `lock`). Empty means the profile the conversation is about (or the most
recently modified one in `profiles_list`) and every group it carries.

Tool names below omit the server prefix (`mcp__plugin_microscope_jeffrey__` for the plugin,
`mcp__jeffrey__` for a hand-registered server).

Two phases with a stop between them — **recommend**, then **change** — because an edit made
before the recommendation has been read cannot be reviewed on its own terms. Track progress:

```
- [ ] 1. Profile resolved, commit compared with HEAD
- [ ] 2. Groups chosen from flamegraph_list
- [ ] 3. One export per group, read as the document says
- [ ] 4. Every finding tied to a frame and to source that was actually read
- [ ] 5. Recommendation written — STOP and wait for the user's choice
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

## 2. Pick the groups

A recording answers up to four questions, each with its own event type. Analyse every group the
profile carries, in this order; if the user named one, do only that one.

| Group | Event type (first present wins) | Export options |
|---|---|---|
| `cpu` | `jdk.ExecutionSample` | defaults |
| `wall` | `profiler.WallClockSample` | defaults |
| `alloc` | `jdk.ObjectAllocationSample`, else `jdk.ObjectAllocationInNewTLAB`, else `jdk.ObjectAllocationOutsideTLAB` | `useWeight: true` (bytes, not call count) |
| `lock` | `jdk.JavaMonitorEnter`, else `jdk.JavaMonitorWait`, else `jdk.ThreadPark` | `useWeight: true` (nanoseconds blocked) |

`flamegraph_list` splits these for you: `available` holds the event types the recording carries,
each with its export defaults; `notRecorded` names the groups the profiler was not configured to
capture. Report a `notRecorded` group rather than analysing it, with the async-profiler option
that would capture it next time: `event=ctimer` (cpu), `wall=10ms`, `alloc=512k`, `lock=10ms`.

## 3. Export and read

`flamegraph_export` once per group, whole recording, default threshold. Every export opens with
its own reading instructions and an analysis section written for that event type — what counts as
a hotspot, what the frame tags mean, what to skip. Follow that document rather than generic
flamegraph lore. Lower `thresholdPct` only to chase one specific path deeper.

## 4. Ground every finding in source

The export has call paths and numbers, never file or line numbers. Map the heaviest frames to the
checkout with Read, Grep and Glob, holding to these rules — they are what separates a
recommendation from a guess:

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

- **Summary** — the dominant hotspots per group, two or three sentences.
- One **`### <file>: <method>`** section per finding: the cause, why it is hot according to the
  profile (frame, share), and the proposed change in prose — minimal, behaviour-preserving,
  reviewable on its own. No diffs in this phase.
- **Not located** — hotspots that could not be mapped to this repository, if any.

Then ask which findings to apply, and wait for the answer. This is the gate.

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

## Latency rather than throughput

"This endpoint is slow" is a traces question first: `traces_operations`, `traces_notifications`
when the overview reports any (a `CRITICAL` or `HIGH` one is the application's own diagnosis and
comes before any frame), `traces_slowestTraces`, `traces_traceExport`, then
`traces_spanFlamegraphExport` for the frames inside the slow span — the sequence in the
`analyze-jfr` skill. Once a span's flamegraph names the hot frames, continue from step 4 with
that export instead of the whole-recording one.

## When something is missing

- `flamegraph_list` reports no graphable event types → a heap dump, or a recording without
  samples; there is nothing to advise on from a flamegraph. For a heap dump, the `analyze-heap`
  skill applies.
- The profile's commit differs from `HEAD` and the user does not want to switch → analyse anyway,
  and say in the summary that every file reference was checked against a different commit than
  the one profiled.
- The code behind the top frame is in a dependency → say so, name the calling frame in this
  repository, and advise there (fewer calls, a cheaper API, caching) rather than inside the library.
