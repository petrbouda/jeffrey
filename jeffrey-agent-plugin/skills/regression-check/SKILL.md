---
name: regression-check
description: Decides whether a change made this project slower, by profiling two revisions the same way and weighing them against each other — build and record each, import both into a running Jeffrey Microscope, then report what moved and by how much. Use when the user asks whether a commit, branch or pull request regressed performance, wants a before-and-after measured rather than argued, or suspects a slowdown appeared somewhere in a range of commits.
allowed-tools: mcp__plugin_microscope_jeffrey__* mcp__jeffrey__* Read Grep Glob Bash(git rev-parse *) Bash(git status *) Bash(git diff *) Bash(git log *)
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
recordings_analyzeFile(path="/abs/base.jfr", name="baseline <short-sha>", force=true, commit="<baseline sha>")
recordings_analyzeFile(path="/abs/head.jfr", name="candidate <short-sha>", force=true, commit="<candidate sha>", dirty=<true for uncommitted changes>)
```

`force=true` on both, because both files were just recorded: a re-run to the same path can match an
earlier import by name and size, and then the call returns that old profile — and ignores `name`.

Both may come back with a status of `RUNNING` and an `operationId`; poll `operations_status` with
it, and `operations_cancel` it if the wrong build was recorded.

## 4. Ask whether they are comparable before reading the difference

```
compare_list(profileId=<candidate>, baselineProfileId=<baseline>)
compare_quality(profileId=<candidate>, baselineProfileId=<baseline>)
```

`compare_list` identifies shared event types and window differences. `compare_quality` returns
evidence, not a single comparability verdict: inspect `samplingConfiguration`, `findings`, each
side's `samplerHealth`, and `truncation` using the interpretation in `compare-jfr`. Call both before
quoting a delta. If no shared evidence supports the requested claim, explain why and re-record or
stop that comparison. Matching settings and lengths say the two were *recorded the same way*, not
that they did the same work: never write "the runs are comparable" on its own — say which of the two
the evidence shows, as `compare-jfr` words it.

`durationNormalization.available=true` permits exposure scaling, not an assumption of equal load.
`workloadNormalization.available=false` means the recorded server events cannot supply a complete
per-operation denominator. You may still compare hotspot distributions and qualified movements;
do not turn those alone into a faster/slower-per-request verdict. Use the controlled workload and
measured benchmark outcomes from this run to support a regression claim, or report the missing
denominator under **Not assessed**.

## 5 and 6. Read what moved, then say what it means

Ask about the change first, then about everything else. `git diff <baseline sha>..<candidate sha>`
(or against the working tree for uncommitted changes) names the methods the change touched; write
each as `Class#method` in source spelling and pass them to `compare_methods` with the event type and
your `framePrefixes` (see *Your own code*). Each comes back with its `movement` on both sides, the
change scaled onto the candidate's length, and the callees that appeared or vanished below it — and
`unlistedMovers` lists your methods outside the diff that moved most, which is where a change's cost
often lands (a cache removed in one method shows up as work in its caller).

Then `compare_movements` ranks everything that grew and shrank, and `compare_flamegraph` shows where
in the call tree. Use `useWeight: true` when comparing allocation or lock time rather than sample
counts.

Report, in the `report` skill's shape:

- **The verdict first** — slower, faster, indistinguishable, or insufficient evidence — and the
  measurement or evidence gap it rests on. Missing support for a performance claim is not proof
  that the runs are indistinguishable.
- **Where**, as a frame with its share on each side.
- **The uncertainty.** One run each on a shared machine supports "no obvious change"; it does not
  support "3% slower". A movement smaller than the difference between two runs of the *same* revision
  is noise, and the honest way to know that is to record the baseline twice.

A regression whose cause is visible in the diff is worth naming; **optimize-code** turns it into a
change. A regression with no candidate in the diff is still a finding — report it rather than
hunting until something fits.

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

Picked, it hands over to the `optimize-code` skill with what this conversation already established —
the profile id, each finding's frame or setting, its figures and its link — so it starts at reading
the source rather than choosing evidence again, and keeps its own gate: the proposed change per
finding first, then which to apply, and only the accepted ones are edited. A setting (a JVM flag, a
pool size) is proposed as a value to change, not as an edit to code. After the build and the tests,
offer **Re-profile and compare** — `profile-run` records the changed code, `compare-jfr` puts it
against this profile — so the fix is measured rather than assumed. A heap finding is measured by a
second dump taken under the same load and put against this one with `heap_diff`, the earlier dump as
the baseline.

## Your own code

Jeffrey never reads this repository, so it is told which code is the application's own: the root
packages of the source tree you are in, passed as `framePrefixes`. Work them out once and reuse them
for every call:

- `Glob` for `**/src/main/java/**/*.java` and `**/src/main/kotlin/**/*.kt` and take the shortest
  package roots shared by the files (usually two or three segments: `com.acme.orders.`). Fall back to
  the `groupId` in `pom.xml` or the `group` in `build.gradle(.kts)`. In a multi-module repository
  where the question is about one service, take that module's packages and the shared ones it uses.
- Keep the trailing dot, so `com.acme.` does not also match `com.acmecorp.`.
- Only the source tree's own packages, never a library's or the JDK's (`java.`, `org.springframework.`):
  library and JDK work is already charged to the own method that called it. What a library costs on
  its own is read from `flamegraph_export`.
- Name them in one line — "your code is `com.acme.orders.`" — so the user can correct you before
  anything depends on it. `code_hotspots` called without them answers with the profile's busiest
  non-library packages as `suggestedFramePrefixes`; one that differs from yours means the profile is
  of another application, or the code was shaded or relocated.

A frame comes back by its runtime name, and source and runtime spell code differently: a lambda body
runs as `Outer#lambda$method$1`, a nested class is `Outer$Inner` at runtime and `Outer.Inner` in
source, a Kotlin top-level function lives on `FileKt`, a Kotlin lambda or coroutine is a class named
after its function (`Outer$method$1`). `code_method` takes the source spelling and lists every runtime
name it merged in `matchedFrames` — read them before you name a line of source for a figure. A row of
`code_hotspots` or `code_callSites`, and each of `compare_methods`' `unlistedMovers`, carries its
`reference` in that spelling: pass it on as it is, never the row's runtime `methodName`. A hotspot that
is your own wrapper — a database client, an HTTP helper, a generic executor — is charged everything
below it, so the change is rarely in it: its `calledFrom` names the code of yours in another class that
asked for the cost (the repository method behind a query, the service behind a call), and that is where
a fix lands. Name both. A method `compare_methods` answers `NOT_SEEN` lists
`nearestReferences` — most often the same method with its nested class or package spelled out
(`com.acme.Checkout.PriceCalculator#formatLine` for `Checkout#formatLine`). A `NOT_SEEN` method has
no figures: if the answer's first `nextTools` entry is `compare_methods`, make that call before you
read, report or compare any figure of that method. It respells the methods Jeffrey can match without
doubt. Never stand in an `unlistedMovers` row for a listed method: its `charged` cost leaves out the
own methods it calls, so it hides the very change a diff added below it. With no such entry, the
candidates are ambiguous or live in another class; check their classes in the source before you pass
one yourself.

## Link what you name

Whenever a reply names something Microscope can open — the profile, a flamegraph, an operation, a
trace, an endpoint, a statement group, a GC, JIT or thread page, a heap class or object, a
differential, a catalogue row, a ledger entry — make the name a Markdown link marked with `↗`:
`[trace 7e34c5994dc96208 ↗](<its uiLink>)`, and in a table a column of `[↗ open](<its uiLink>)`.
Every link carries the mark — every profile named too, the analysed one and its baseline alike, and
a page named in passing ("the exceptions page"), a profile named under **Not assessed** or **Open
leads**, and a figure's source given as a link (`` [`traces_slowestTraces` ↗](<its uiLink>) ``) — the
links most often left bare: `[checkout candidate ↗](<its uiLink>)` — and never the bare URL. The `↗` is what makes it a link the
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
- A tool you did not call — a follow-up on offer, a check under **Not assessed** — is named in backticks
  and never linked, and never given the profile's own link in place of a page you have no link for:
  `` `jvm_safepoints` `` alone, not `` [`jvm_safepoints`](<the profile's link>) ``.

## What this skill will not do

- **It will not bisect silently.** A range of commits is many builds and many profiles; propose it,
  with the cost, and let the user decide.
- **It will not declare a regression from one pair of short runs.** It says what it measured and how
  confident that makes it.

Related skills: `profile-run` for the recording flags, `compare-jfr` for reading a pair that already
exists, `optimize-code` for turning a confirmed regression into a fix, `report` for how the verdict and
its uncertainty are written down.
