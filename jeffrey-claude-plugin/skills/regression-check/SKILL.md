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

Tool names below omit the prefix your client puts in front of them.

## The shape of it

```
1. Pick the pair, and the workload both can run
2. Record the baseline          → recordings_analyzeFile → profileId B
3. Record the candidate         → recordings_analyzeFile → profileId C
4. compare_list(C, baseline=B)  → are these two even comparable
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
recordings_analyzeFile(path="/abs/base.jfr", name="baseline <short-sha>")
recordings_analyzeFile(path="/abs/head.jfr", name="candidate <short-sha>")
```

Both may come back with a status of `running`; poll `recordings_status` rather than importing again.

## 4. Ask whether they are comparable before reading the difference

```
compare_list(profileId=<candidate>, baselineProfileId=<baseline>)
```

This is not a formality. It reports whether the two recordings cover comparable windows and carry the
same event types, and **"these two are not comparable" is the finding** — reporting a regression from
an incomparable pair is worse than reporting nothing, because it will be believed. If they are not
comparable, say why and either re-record or stop.

## 5 and 6. Read what moved, then say what it means

`compare_movements` ranks what grew and what shrank; `compare_flamegraph` shows where in the call
tree. Use `useWeight: true` when comparing allocation or lock time rather than sample counts.

Report:

- **The verdict first** — slower, faster, or indistinguishable — and the figure it rests on.
- **Where**, as a frame with its share on each side.
- **The uncertainty.** One run each on a shared machine supports "no obvious change"; it does not
  support "3% slower". A movement smaller than the difference between two runs of the *same* revision
  is noise, and the honest way to know that is to record the baseline twice.

A regression whose cause is visible in the diff is worth naming; **advise-jfr** turns it into a
change. A regression with no candidate in the diff is still a finding — report it rather than
hunting until something fits.

## What this skill will not do

- **It will not bisect silently.** A range of commits is many builds and many profiles; propose it,
  with the cost, and let the user decide.
- **It will not declare a regression from one pair of short runs.** It says what it measured and how
  confident that makes it.

Related skills: `profile-run` for the recording flags, `compare-jfr` for reading a pair that already
exists, `advise-jfr` for turning a confirmed regression into a fix.
