---
name: regression-check
description: Decides whether a change made this project slower, by profiling two revisions the same way and weighing them against each other — build and record each, import both into a running Jeffrey Microscope, then report what moved and by how much. Use when the user asks whether a commit, branch or pull request regressed performance, wants a before-and-after measured rather than argued, or suspects a slowdown appeared somewhere in a range of commits.
allowed-tools: mcp__plugin_microscope_jeffrey__* mcp__jeffrey__*
argument-hint: "[baseline-ref] [candidate-ref] [workload]"
---

# Did this change make it slower

Two revisions, the same workload, measured the same way. `compare-jfr` reads two profiles that
already exist; this produces them, which is where most of the rigour lives.

Requested scope: `$ARGUMENTS` — the baseline, the candidate, and what to run. Defaults: the merge
base against `HEAD`, and whatever benchmark the repository makes obvious. Say which you picked before
spending the minutes.

Tool names below omit the prefix your client puts in front of them — `mcp__plugin_microscope_jeffrey__` in Claude Code with the `microscope` plugin, `mcp__jeffrey__` in Codex or wherever the server is registered by hand as `jeffrey`, `mcp_jeffrey_` in Gemini CLI; the rest of the name is exact camelCase.

## The shape of it

```
1. Pick the pair, and the workload both can run
2. Record the baseline          → recordings_analyzeFile → profileId B
3. Record the candidate         → recordings_analyzeFile → profileId C
4. compare_list(C, baselineProfileId=B)    → are these two even comparable
   compare_quality(C, baselineProfileId=B) → sampling, duration and workload evidence
5. compare_movements            → what moved, ranked
6. compare_flamegraph           → where it moved, in the call tree
```

Track it, because step 2 and 3 are long and it is easy to lose which build is on disk:

```
- [ ] 1. Pair and workload chosen
- [ ] 2. Baseline recorded and imported
- [ ] 3. Candidate recorded and imported
- [ ] 4. Comparability checked
- [ ] 5. Movements read
- [ ] 6. Verdict
```

## 1. Pick a pair that can be compared

Both revisions must run the **same workload** — a benchmark that only exists on one side measures
nothing. When the candidate introduces the benchmark, run it from the candidate against a checkout of
the baseline, or say plainly that no before-measurement is possible.

Check the working tree is clean before switching revisions, and put it back afterwards. A stash left
behind is a worse outcome than an unanswered question.

## 2 and 3. Record both, identically

The **profile-run** skill has the recording flags; this skill's contribution is that the two runs
must differ in exactly one thing.

- Same JVM, same flags, same `settings=profile`, same duration, same machine.
- Same warm-up. A cold JIT on one side and a warm one on the other is the commonest false regression.
- **Not in parallel.** Two profiled JVMs on one machine contend for CPU, and each records the other's
  interference as its own slowness.
- Prefer alternating repeats — baseline, candidate, baseline, candidate — when the machine is noisy
  or the difference is expected to be small. A single pair on a laptop with a browser open measures
  the browser.

Name them so the pair survives the session:

```
recordings_analyzeFile(path="/abs/base.jfr", name="baseline <short-sha>", force=true)
recordings_analyzeFile(path="/abs/head.jfr", name="candidate <short-sha>", force=true)
```

`force=true` on both, because both files were just recorded: a re-run to the same path can match an
earlier import by name and size, and then the call returns that old profile — and ignores `name`.

Both may come back with a status of `RUNNING` and an `operationId`; poll `operations_status` with
it, and `operations_cancel` it if the wrong build was recorded. `recordings_analyzeFile` returns the
existing profile for a file with the same name and size as one already imported; after re-recording
to the same path, pass `force=true`.

## 4. Ask whether they are comparable before reading the difference

```
compare_list(profileId=<candidate>, baselineProfileId=<baseline>)
compare_quality(profileId=<candidate>, baselineProfileId=<baseline>)
```

`compare_list` identifies shared event types and window differences. `compare_quality` returns
evidence, not a single comparability verdict: inspect `samplingConfiguration`, `findings`, each
side's `samplerHealth`, and `truncation` using the interpretation in `compare-jfr`. Call both before
quoting a delta. If no shared evidence supports the requested claim, explain why and re-record or
stop that comparison.

`durationNormalization.available=true` permits exposure scaling, not an assumption of equal load.
`workloadNormalization.available=false` means the recorded server events cannot supply a complete
per-operation denominator. You may still compare hotspot distributions and qualified movements;
do not turn those alone into a faster/slower-per-request verdict. Use the controlled workload and
measured benchmark outcomes from this run to support a regression claim, or report the missing
denominator under **Not assessed**.

## 5 and 6. Read what moved, then say what it means

`compare_movements` ranks what grew and what shrank; `compare_flamegraph` shows where in the call
tree. Use `useWeight: true` when comparing allocation or lock time rather than sample counts.

Report, in the `report` skill's shape:

- **The verdict first** — slower, faster, indistinguishable, or insufficient evidence — and the
  measurement or evidence gap it rests on. Missing support for a performance claim is not proof
  that the runs are indistinguishable.
- **Where**, as a frame with its share on each side.
- **The uncertainty.** One run each on a shared machine supports "no obvious change"; it does not
  support "3% slower". A movement smaller than the difference between two runs of the *same* revision
  is noise, and the honest way to know that is to record the baseline twice.

A regression whose cause is visible in the diff is worth naming; **advise-jfr** turns it into a
change. A regression with no candidate in the diff is still a finding — report it rather than
hunting until something fits.

## Link what you name

Whenever a reply names something Microscope can open — the profile, a flamegraph, an operation, a
trace, an endpoint, a statement group, a GC, JIT or thread page, a heap class or object, a
differential — put its link right beside the name as a Markdown link, so the user opens it in one
click: `[trace 7e34c5994dc96208](<its uiLink>)`. That holds in every reply, not only in a written
report: a finding, a ranked list, a follow-up offer, a sentence in passing.

- Take the link from the answer the thing came from. A row with its own `uiLink` — a trace, an
  operation, an exemplar — links with that one; otherwise the answer's `uiLink` is the page that
  shows the row. Quote the `uiLinkNote` beside it when there is one.
- No link in hand for one of the profile's pages (GC, JIT, threads, the heap views, …) →
  `profiles_viewLink`, one cheap call; the profile itself → `profiles_link`.
- Never build, edit or guess a URL, and never fetch one: the link is for the user, and one typed by
  hand opens the wrong page or none.
- An area not yet analysed is not a thing and needs no link, and a result no page shows — a raw SQL
  query — has none; say so rather than inventing one.

## What this skill will not do

- **It will not bisect silently.** A range of commits is many builds and many profiles; propose it,
  with the cost, and let the user decide.
- **It will not declare a regression from one pair of short runs.** It says what it measured and how
  confident that makes it.

Related skills: `profile-run` for the recording flags, `compare-jfr` for reading a pair that already
exists, `advise-jfr` for turning a confirmed regression into a fix, `report` for how the verdict and
its uncertainty are written down.
