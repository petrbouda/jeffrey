---
name: compare-jfr
description: Compares two JVM profiles held by a running Jeffrey Microscope — a before and an after — using differential flamegraphs, to answer whether a change made the application slower, faster, or allocate more. Use whenever the user asks if a change regressed performance, what got slower or faster between two runs, to compare two recordings, benchmarks or branches, or mentions a baseline, a before/after or a performance regression. For a single profile, analyze-jfr applies instead; for a heap dump, analyze-heap.
allowed-tools: mcp__plugin_microscope_jeffrey__* mcp__jeffrey__*
---

# Comparing two Jeffrey profiles

Two recordings of the same application — one from before a change, one from after — subtracted
frame by frame. The **primary** is the run under examination and the **baseline** is what it is
measured against; a positive delta always means the primary spends *more*.

Tool names below omit the prefix your client puts in front of them — `mcp__plugin_microscope_jeffrey__` in Claude Code with the `microscope` plugin, `mcp__jeffrey__` in Codex or wherever the server is registered by hand as `jeffrey`, `mcp_jeffrey_` in Gemini CLI; the rest of the name is exact camelCase: `compare_movements`, not `compare_movements_list`.

## The one thing that makes this analysis worthless

Any two recordings can be subtracted, and the result always looks like a finding. Whether it *is*
one depends on facts the deltas do not show: that both runs did the same kind of work, for a
comparable length of time, with the same profiler settings. Nothing inside a JFR file proves any of
that.

So `compare_list` is not a warm-up call, and `compare_quality` after it is not one either. Between
them they decide whether the rest means anything, and their answer is a finding in its own right —
"these two runs are not comparable" is a real, reportable result, and a far better one than a
confident regression that was really a recording twice as long, or a sampler that dropped a third
of its samples on one side.

## 1. Get two `profileId`s

`profiles_list` shows every analysed profile with its name, recording time and duration. Pick the
**baseline** (before) and the **primary** (after) from it.

**The user named two files** (`before.jfr` and `after.jfr`) — call `recordings_analyzeFile` with the
**absolute** path for each; `recordings_analyzeFile` returns the existing profile for a file with
the same name and size as one already imported; after re-recording to the same path, pass
`force=true`.
Jeffrey opens those paths itself, so both files must be on the machine Jeffrey runs on. If no
`recordings_` tool is advertised, this Jeffrey has ingestion switched off — upload both in the UI
first.

Getting the direction right matters: pass the **after** run as `profileId` and the **before** run
as `baselineProfileId`. Backwards, every regression reads as an improvement.

## 2. Establish comparability — always first

`compare_list` reports both recordings' span and length (`durationMs`, null for a side that
carries no span), the event types they have in common with each side's totals, and the types only
one of them recorded. Its `uiLink` opens the pair's differential grid in Microscope, for the user.

Read its `notes`, and stop to think when:

- **The durations differ.** Sample counts scale with recording time. The comparison scales the
  baseline onto the primary's length automatically, which is right for a steady workload and wrong
  for a fixed-size benchmark (N requests replayed in both runs). On a benchmark, read the **share**
  column rather than the delta.
- **An event type appears on one side only.** That may reflect instrumentation or observed
  activity; presence alone cannot distinguish them. Report the evidence gap rather than claiming
  that the work appeared or vanished.
- **`comparable` is empty (`status: EMPTY`).** There is nothing to compare — different formats,
  one is a heap dump, or wholly different profiler settings. Say so and stop.

Then call `compare_quality` with the same two ids before quoting a delta. It returns evidence,
not a single comparability verdict. Assess the question using these fields:

- `samplingConfiguration`, `matchingSettings` and `findings`: `samplingConfiguration` lists only the
  common types whose stored settings are `MISMATCH` or `UNKNOWN` — their event volumes need
  qualification — and `matchingSettings` counts the ones that match, which supports comparing them.
  Check each side's `samplerHealth` for sample loss too. A matching snapshot does not prove
  unchanged settings throughout either recording.
- `commonEventTypes`, `onlyInPrimary` and `onlyInBaseline`: compare shared evidence. A type absent
  on one side is not evidence that the application stopped doing that work.
- `durationNormalization.available`: recording-exposure scaling is possible when true. It does
  not establish equal workload or request rate; filtered graph exports use the selected window,
  while this tool describes whole recordings.
- `workloadNormalization.available`: currently false because observed server events do not
  establish complete operation counts. This prevents per-request efficiency or speed claims from
  these counts alone. It does **not** prevent comparing observed hotspot shares or qualified
  duration-normalized movements. State the denominator and the limitation.
- `truncation`: omitted evidence is unknown, not a clean bill of health.

Open the report with your assessment of what can be compared and its limitations. That assessment
is about the **recordings**: matching settings, lengths and sampler health show the two were
*recorded the same way*, never that the runs did the same work. Say both in one sentence — "recorded
the same way (30.5 s against 30.4 s, all 89 shared types' settings match); whether both did the same
work is not shown" — unless the user or the workload states it. Never write "the runs are
comparable" on its own. If no shared event type supports the requested comparison, report that gap
and stop. Otherwise compare the
supported dimensions, and keep a workload regression claim separate from an observed movement.

If the two runs came from different machines, different load levels or different JVM flags, say so
before anything else. No amount of arithmetic recovers that, and the numbers will look just as
precise either way.

## 3. Rank what moved

`compare_movements` with the `eventType` from `compare_list`. This is the main tool. It returns the
methods that grew and the ones that shrank, ranked by how much work moved with them.

- on-CPU time → `jdk.ExecutionSample`, or `jdk.CPUTimeSample` for a recording made with JDK 25's
  CPU-time sampler turned on (the JDK's settings leave it off) — take whichever `compare_list` reports
  in common
- allocation → `jdk.ObjectAllocationSample` with `useWeight: true`, so the ranking is by bytes
  rather than by allocation count
- lock contention → `jdk.JavaMonitorEnter` with `useWeight: true` (weight is nanoseconds blocked)

Movements are attributed by **self** weight, so a change is charged to the method that actually
moved rather than to every caller above it. That is why the ranked list is the first read and the
tree is the second.

The document opens with its own comparability section and an explanation of every column. Follow
that preamble; it is written against the code that produced the numbers.

A window narrows both sides: `startEpochMs`/`endEpochMs` are instants on the **primary's** clock,
applied at the same offset into the baseline, and the answer's `window` reports where it landed on
each recording. The `uiLink` opens the differential flamegraph with the same event type, filters,
window and baseline; its `uiLinkNote` says what the page shows differently from the ranking.

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
- **Nothing moved is a result too.** When `compare_movements` ranks nothing above the pruning
  threshold, report exactly that — "no movement above the threshold on this event type" — and not
  that the two builds perform identically: a change smaller than run-to-run variance is invisible
  to this method, and the report says so.

Write the result in the `report` skill's shape: the comparability assessment first, worded as in
§2, then each movement with the `compare_movements` call and its figures, the confidence the
comparability notes allow, and the two profile ids with which one was the baseline.

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

## Implement fixes

You are usually running inside the repository of the application that was profiled, so a finding
can become a change to its code. When it can, offer that as an option labelled exactly
**Implement fixes** — not "I can propose a change" in passing — beside the follow-ups that close the
answer. In Claude Code close with `AskUserQuestion` whenever this option is on offer, a named area's
answer included: the follow-ups, **Implement fixes**, and "Stop here". Elsewhere, one line that
starts "Implement fixes:". Offer it, never take it: nothing is edited until the user picks it.

Offer it only when both hold, or it is noise — and when they do not, leave it out without a word:
no "Implement fixes: not on offer", no sentence on why there is nothing to fix. Run both checks before
you write the offer: one that says it has not yet checked whether the class is here is not offered.

- the working directory is a repository — a `.git` there (`Glob`), or `git rev-parse
  --show-toplevel` answering;
- at least one finding names something that repository holds — the class of the hot frame, the
  throw site, a leak suspect's class or the field that holds its accumulation point (its
  `gcRootPath` names the field, or the thread and `stackFrame` that hold it), the configuration file
  (`logback.xml`, `application.yml`, a pool's settings): `Grep` or `Glob` for it finds a file there,
  not only JDK, framework or library code.

Then say how the code relates to the profile in the option's description: `profiles_get`'s
`recordingCommit` against `git rev-parse HEAD` — "profiled at abc1234, HEAD is def5678" when they
differ, "commit not recorded" when `recordingCommit` is null (a heap dump carries none). That
qualifies the offer; it never removes it.

The option's description names what it would change, at most two findings by place
(say, logback.xml's caller data, the pricing class's line formatter, or the audit log's unbounded
list). When the user asks what else there
is, or the analysis winds down, the ledger's **Covered** findings that pass the same check become
one multi-select **Implement fixes for:** question.

Picked, it hands over to the `advise-jfr` skill with what this conversation already established —
the profile id, each finding's frame or setting, its figures and its link — so it starts at reading
the source rather than choosing evidence again, and keeps its own gate: the proposed change per
finding first, then which to apply, and only the accepted ones are edited. A setting (a JVM flag, a
pool size) is proposed as a value to change, not as an edit to code. After the build and the tests,
offer **Re-profile and compare** — `profile-run` records the changed code, `compare-jfr` puts it
against this profile — so the fix is measured rather than assumed. A heap finding is measured by a
second dump taken under the same load and put against this one with `heap_diff`, the earlier dump as
the baseline.

## Link what you name

Whenever a reply names something Microscope can open — the profile, a flamegraph, an operation, a
trace, an endpoint, a statement group, a GC, JIT or thread page, a heap class or object, a
differential, a catalogue row, a ledger entry — make the name a Markdown link marked with `↗`:
`[trace 7e34c5994dc96208 ↗](<its uiLink>)`, and in a table a column of `[↗ open](<its uiLink>)`.
Every link carries the mark — every profile named too, the analysed one and its baseline alike, and
a page named in passing ("the exceptions page"), the links most often left bare:
`[checkout candidate ↗](<its uiLink>)` — and never the bare URL. The `↗` is what makes it a link the
user sees: a terminal that renders links shows the text alone, clickable, with the URL behind it,
and an unmarked name reads as plain words. That holds in every reply, not only in a written report:
a finding, a ranked list, a follow-up offer, a sentence in passing. **Before you send a reply, check
it: every `](` closes a link text that holds `↗`.**

- Take the link from the answer the thing came from. A row with its own `uiLink` — a trace, an
  operation, an exemplar — links with that one; otherwise the answer's `uiLink` is the page that
  shows the row. Quote the `uiLinkNote` beside it when there is one.
- No link in hand for one of the profile's pages (GC, JIT, threads, the heap views, …) →
  `profiles_viewLink`, one cheap call; the profile itself → `profiles_link`.
- Never build, edit or guess a URL, and never fetch one: the link is for the user, and one typed by
  hand opens the wrong page or none.
- An area not yet analysed is not a thing and needs no link, and a result no page shows — a raw SQL
  query — has none; say so rather than inventing one.

## When something fails

- `profileId and baselineProfileId are the same profile` → pick two different runs.
- `no event type in common` → the two profiles cannot be compared; `profiles_features` on each
  shows what they actually hold.
- Everything reads as new or as gone → almost always different profiler settings between the runs,
  or the two profiles are of different applications. Check `compare_list` before reporting it.
- A movement that looks enormous → check the recording lengths in `compare_list` first, then the
  candidate renames, before believing it.

Related skills: `analyze-jfr` for a single profile, `advise-jfr` to turn a located regression into
an edit, `jfr-sql` for raw SQL against either profile, `report` for the shape the result is written
in.
