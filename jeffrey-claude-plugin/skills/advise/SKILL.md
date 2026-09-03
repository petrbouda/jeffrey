---
name: advise
description: Turn a Jeffrey profile into concrete code changes in this repository — map the hottest CPU, wall-clock, allocation and blocking frames to real source, recommend minimal behaviour-preserving edits, apply them on request, and verify with the tests and a re-profile. Use when asked what to change, optimise or fix based on a profile, a JFR recording or a flamegraph, or when a hotspot has been found and the next question is "so what do I do about it".
allowed-tools: mcp__plugin_microscope_jeffrey__* mcp__jeffrey__*
---

# From a profile to a code change

The analysis tools read a profile; this skill is what happens after the reading. It takes the
measured call trees, finds the code behind the heaviest frames in the checkout you are sitting in,
and proposes the smallest change that would reduce the measured cost — then, only when asked,
makes it and checks that it helped.

Two phases, with a stop between them: **recommend**, then **change**. Never edit before the
recommendation has been read.

## 1. Resolve the profile and its commit

- Start from `profiles_list` (or `recordings_analyzeFile` when the user named a file — the
  `analyze-profile` skill covers that path). Nothing works without a `profileId`.
- Call **`profiles_get`** and read `recordingCommit`. If it is set, compare it with
  `git rev-parse HEAD`:
  - equal — say so in one line and continue;
  - different — say so **before anything else**, name both commits, and ask whether to check
    the recording's commit out. Do not switch branches unasked. A profile of another commit
    describes code that may have moved, been renamed or been deleted; mapping it onto the wrong
    tree produces confident nonsense.
  - `null` — say the commit is unknown, not that it matched. (Tag the recording with
    `git.commit` at build time to fix that for next time.)

## 2. Pick the groups

A recording answers up to four questions, each with its own event type. Analyse every group the
profile actually carries, in this order; if the user named one (`cpu`, `wall`, `alloc`, `lock`),
do only that one.

| Group | Event type (first present wins) | Export options |
|---|---|---|
| CPU | `jdk.ExecutionSample` | defaults |
| Wall-clock | `profiler.WallClockSample` | defaults |
| Allocation | `jdk.ObjectAllocationSample`, else `jdk.ObjectAllocationInNewTLAB`, else `jdk.ObjectAllocationOutsideTLAB` | `useWeight: true` (bytes, not call count) |
| Blocking | `jdk.JavaMonitorEnter`, else `jdk.JavaMonitorWait`, else `jdk.ThreadPark` | `useWeight: true` (nanoseconds blocked) |

`flamegraph_panels` says which of these the profile recorded. A group with no samples is
reported, not analysed, with the async-profiler flag that would capture it next time:
`event=ctimer` (CPU), `wall=10ms`, `alloc=512k`, `lock=10ms`.

## 3. Export and read

`flamegraph_export` once per group, whole recording, default threshold. Every export opens with
its own reading instructions and an analysis section written for that event type — what counts
as a hotspot, what the frame tags mean, what to skip. **Follow the document, do not substitute
generic flamegraph lore.** Lower `thresholdPct` only to chase one specific path deeper.

## 4. Ground every finding in source

The export has call paths and numbers, never file or line numbers. Map the heaviest frames to
the checkout with your own Read, Grep and Glob, and hold to these rules — they are what separates
a recommendation from a guess:

- **Never name a file, method or line you have not read.** Open it first.
- **Tie each finding to a frame and its share** (`total`, `self`, the percentage) taken from the
  export, so the reader can check the claim against the profile.
- **Prefer a few high-impact findings** over many speculative ones. Frames under 1 % are noise
  unless the user is chasing something specific.
- **Say when a hotspot cannot be located** — a frame in a library you cannot patch, generated
  code, or a method that no longer exists at this commit. Note it once and move on to the next
  frame that is in this repository.
- Distinguish leaf work (`self ≈ total`) from orchestration (`self << total`); recommend
  changes to the former, and walk into the latter.

## 5. Recommend, then stop

Write the recommendation in this shape:

- **Summary** — the dominant hotspots per group, two or three sentences.
- One **`### <file>: <method>`** section per finding: the cause, why it is hot according to the
  profile (frame, share), and the proposed change in prose — minimal, behaviour-preserving, and
  reviewable on its own. No diffs in this phase.
- **Not located** — hotspots you could not map to this repository, if any.

Then ask which findings to apply. This is the gate; wait for the answer.

## 6. Change and verify

On go, for each accepted finding:

1. Make the smallest edit that implements it. One reviewable change beats a sweeping rewrite.
2. Run the project's build and its tests the way a contributor would.
3. If the recording can be reproduced (a benchmark, a load script, a command the user names),
   run it, then `recordings_analyzeFile` the new recording and export the **same group with the
   same parameters**. Report the delta on the frames you changed; keep the threshold and options
   identical, or a difference in pruning will read as a change that is not there.
4. Never claim a saving you did not measure. Without a re-profile, the estimate is capped at the
   frame's own `total` share — a change cannot save more time than the frame used.

## When the question is latency, not throughput

"This endpoint is slow" is a traces question first: `traces_operations`, `traces_slowestTraces`,
`traces_traceExport`, then `traces_spanFlamegraphExport` for the frames inside the slow span —
the sequence in the `analyze-profile` skill. Once a span's flamegraph names the hot frames,
continue from step 4 above with that export instead of the whole-recording one.

## When something is missing

- `flamegraph_panels` is empty → a heap dump or a recording without samples; there is nothing to
  advise on from a flamegraph. For a heap dump, the `heap_` family and the `heap-sql` skill apply.
- The profile's commit differs from `HEAD` and the user does not want to switch → analyse anyway,
  but say in the summary that every file reference was checked against a different commit than
  the one profiled.
- The code behind the top frame is in a dependency → say so, name the calling frame in this
  repository, and advise there (fewer calls, a cheaper API, caching) rather than inside the library.
