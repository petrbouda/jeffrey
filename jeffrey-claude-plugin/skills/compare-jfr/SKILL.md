---
name: compare-jfr
description: Compares two JVM profiles held by a running Jeffrey Microscope — a before and an after — using differential flamegraphs, to answer whether a change made the application slower, faster, or allocate more. Use whenever the user asks if a change regressed performance, what got slower or faster between two runs, to compare two recordings, benchmarks or branches, or mentions a baseline, a before/after or a performance regression. For a single profile, analyze-jfr applies instead; for a heap dump, analyze-heap.
allowed-tools: mcp__plugin_microscope_jeffrey__* mcp__jeffrey__*
---

# Comparing two Jeffrey profiles

Two recordings of the same application — one from before a change, one from after — subtracted
frame by frame. The **primary** is the run under examination and the **baseline** is what it is
measured against; a positive delta always means the primary spends *more*.

Tool names below omit the prefix your client puts in front of them —
`mcp__plugin_microscope_jeffrey__` for the Claude Code plugin, `mcp__jeffrey__` in Codex and for any
hand-registered server. The part after it is exact and camelCase:
`compare_movements`, not `compare_movements_list`.

## The one thing that makes this analysis worthless

Any two recordings can be subtracted, and the result always looks like a finding. Whether it *is*
one depends on facts the deltas do not show: that both runs did the same kind of work, for a
comparable length of time, with the same profiler settings. Nothing inside a JFR file proves any of
that.

So `compare_list` is not a warm-up call. It is the step that decides whether the rest means
anything, and its answer is a finding in its own right — "these two runs are not comparable" is a
real, reportable result, and a far better one than a confident regression that was really a
recording twice as long.

## 1. Get two `profileId`s

`profiles_list` shows every analysed profile with its name, recording time and duration. Pick the
**baseline** (before) and the **primary** (after) from it.

**The user named two files** (`before.jfr` and `after.jfr`) — check `profiles_list` for them first,
then `recordings_analyzeFile` with the **absolute** path for each one that is missing. Every call
imports the file again and creates another profile, so never re-analyse one that is already there.
Jeffrey opens those paths itself, so both files must be on the machine Jeffrey runs on. If no
`recordings_` tool is advertised, this Jeffrey has ingestion switched off — upload both in the UI
first.

Getting the direction right matters: pass the **after** run as `profileId` and the **before** run
as `baselineProfileId`. Backwards, every regression reads as an improvement.

## 2. Establish comparability — always first

`compare_list` reports both recordings' length, the event types they have in common with each
side's totals, and the types only one of them recorded.

Read its `notes`, and stop to think when:

- **The durations differ.** Sample counts scale with recording time. The comparison scales the
  baseline onto the primary's length automatically, which is right for a steady workload and wrong
  for a fixed-size benchmark (N requests replayed in both runs). On a benchmark, read the **share**
  column rather than the delta.
- **An event type appears on one side only.** That is a difference between the two *profiler
  configurations*, not a change in the application. Report it as such; do not report the work as
  having appeared or vanished.
- **`comparable` is empty.** There is nothing to compare — different formats, one is a heap dump,
  or wholly different profiler settings. Say so and stop.

If the two runs came from different machines, different load levels or different JVM flags, say so
before anything else. No amount of arithmetic recovers that, and the numbers will look just as
precise either way.

## 3. Rank what moved

`compare_movements` with the `eventType` from `compare_list`. This is the main tool. It returns the
methods that grew and the ones that shrank, ranked by how much work moved with them.

- on-CPU time → `jdk.ExecutionSample`
- allocation → `jdk.ObjectAllocationSample` with `useWeight: true`, so the ranking is by bytes
  rather than by allocation count
- lock contention → `jdk.JavaMonitorEnter` with `useWeight: true` (weight is nanoseconds blocked)

Movements are attributed by **self** weight, so a change is charged to the method that actually
moved rather than to every caller above it. That is why the ranked list is the first read and the
tree is the second.

The document opens with its own comparability section and an explanation of every column. Follow
that preamble; it is written against the code that produced the numbers.

## 4. Drill into one method

`compare_flamegraph` exports the merged call tree, so a movement can be followed to the call paths
it travelled through — which caller started spending more, whether the work is new or grew in
place.

Pruning is by **movement**, not by size: a subtree in which nothing changed is dropped however
large it is. **Absence in this tree means "did not move", not "not present"** — the opposite of the
single-profile `flamegraph_export`. Lower `thresholdPct` to chase one path; raise it for the shape
of the change.

## 5. Read the result honestly

- **A rename is not a regression.** The tree matches method names level by level, so a renamed,
  moved or extracted method appears once as `[NEW]` and once as `[GONE]`, often of near-identical
  size. Both documents list such pairs as **candidate renames**. They are suspicions; you have the
  source diff and the profile does not, so check it before reporting either half as a real change.
- **Share and delta answer different questions.** The delta says how much more work there is; the
  share says where the profile goes. A method can grow in share while the process as a whole got
  faster. Quote whichever the question asked for, and say which one it is.
- **Small movements on thin profiles are noise.** One pair of recordings cannot separate a real 5%
  move from run-to-run variance. If the comparability section flags a thin profile, report
  movements as suggestive, not measured.
- **This is one event type's distribution, not a benchmark.** A CPU profile that shifted work into
  a shorter path may still have regressed end-to-end latency. Nothing here measures wall-clock
  improvement of the application; do not claim it did.

## 6. Tie it back to the change

The profile says where, never why. With the repository open alongside, read the real source for
each moved method before naming a file or a line — never infer them from a frame name — and map
the movements onto the actual diff. A regression that lines up with a hunk in the change is a
finding; one that does not is a lead, and often a rename or an unrelated shift in load.

`advise-jfr` carries the full profile-to-code-change workflow once the regression is located.

## Hand the reading to the analyst

Comparison documents are large, and answering well often takes several. A **`profile-analyst`**
agent — `microscope:profile-analyst` from the Claude Code plugin, or the Codex custom agent from
`codex/agents/profile-analyst.toml` — runs a sequence and returns only the findings. Give it both
profile ids, which is the baseline, and the one question. Delegate when more than one event type is
in play or the chase runs deep; read here when there is exactly one document and it will be
discussed turn by turn.

## When something fails

- `profileId and baselineProfileId are the same profile` → pick two different runs.
- `no event type in common` → the two profiles cannot be compared; `profiles_features` on each
  shows what they actually hold.
- Everything reads as new or as gone → almost always different profiler settings between the runs,
  or the two profiles are of different applications. Check `compare_list` before reporting it.
- A movement that looks enormous → check the recording lengths in `compare_list` first, then the
  candidate renames, before believing it.

Related skills: `analyze-jfr` for a single profile, `advise-jfr` to turn a located regression into
an edit, `jfr-sql` for raw SQL against either profile.
