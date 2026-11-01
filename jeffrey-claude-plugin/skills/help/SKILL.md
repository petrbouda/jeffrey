---
name: help
description: "Explains what a running Jeffrey Microscope can investigate and how to get there — the areas a JFR recording or a heap dump can answer (CPU, wall-clock, allocation, locks, requests and outbound calls, the database, I/O, exceptions, GC and pauses, JIT, threads, native memory, the container, leaks, and a heap dump's contents, retention and waste), what each needs recorded, which skill takes each, and which profiles are here now. Use whenever the user asks what Jeffrey or Microscope can do, what they can investigate or ask, which areas there are, what to record so a question can be answered, how to start, or says \"help\" about Jeffrey, before any profile is chosen. For a profile already named with an open question, analyze-jfr's menu applies directly."
allowed-tools: mcp__plugin_microscope_jeffrey__* mcp__jeffrey__*
---

# What Jeffrey can investigate

An orientation, not an analysis. It answers "what can I ask?" with what this Jeffrey serves and what
is here now, then hands the user to the skill that does the work. It runs nothing slow and builds
nothing.

Tool names below omit the prefix your client puts in front of them — `mcp__plugin_microscope_jeffrey__` in Claude Code with the `microscope` plugin, `mcp__jeffrey__` in Codex or wherever the server is registered by hand as `jeffrey`, `mcp_jeffrey_` in Gemini CLI; the rest of the name is exact camelCase.

## 1. What Jeffrey reads

Recordings that already exist: JFR files from the JDK or async-profiler, pprof and OpenTelemetry
profiles (flamegraphs only), and HPROF heap dumps. It never attaches to a running process — the
**profile-run** skill records this project first, and **analyze-remote** pulls what a deployed
application recorded. Every answer carries a link to the same view in Microscope.

## 2. What it can answer

These are the areas `profiles_summary` offers as its `investigationAreas` menu. Which of them a given
profile can answer depends on what was recorded — that is server data, so for a real profile take the
menu from `profiles_summary` (step 3), never from this table.

**A recording (JFR, async-profiler, pprof)**

| Group | Area | What it answers | Needs recorded |
|---|---|---|---|
| Code | CPU hotspots | Which code burned the CPU | async-profiler `event=cpu` (or `event=ctimer` in a container), or JFR's `jdk.ExecutionSample` / `jdk.CPUTimeSample` |
| Code | Wall-clock time | Where the time went, running or waiting | async-profiler `wall=10ms` |
| Code | Allocation | Which types allocate most, then from where | async-profiler `alloc=512k`, or JFR's `jdk.ObjectAllocationSample` |
| Code | Lock contention | Which locks and waits held threads back | async-profiler `lock=10ms`, or JFR's `jdk.JavaMonitorEnter` and `jdk.ThreadPark` |
| Waiting | Traces | Which requests were slow, and where each one's time went, span by span | the application instrumented with Jeffrey tracing |
| Waiting | Requests and calls | Which endpoints, outbound calls and traced methods took the time — HTTP and gRPC, server and client | the application instrumented with jeffrey-events; for traced methods `jdk.MethodTrace` / `jdk.MethodTiming` with a method filter |
| Waiting | Database | Which statements ran, and whether the pool made requests wait | jeffrey-events JDBC instrumentation |
| Waiting | I/O waiting | Sockets and files, and how long it waited | `jdk.SocketRead`, `jdk.SocketWrite`, `jdk.FileRead`, `jdk.FileWrite` |
| JVM | GC and pauses | Whether collections and safepoints stopped the application | JFR's GC and safepoint events (default settings) |
| JVM | JIT compilation | Slow compilations and deoptimisation | JFR's compiler events |
| JVM | Threads | Which threads were busiest, and in which states | JFR's thread events |
| JVM | Native memory | Memory outside the Java heap | JFR's native-memory events |
| Overall | Rule findings | What Jeffrey's rules flag across the whole recording | a JFR recording |
| Overall | Container and flags | Throttling, and what the JVM really ran with | JFR's container and configuration events |
| Overall | Leak candidates | Objects that survived collections | `jdk.OldObjectSample` (JFR's profile settings) |
| Overall | Exceptions | What the application threw, and from where | JFR's exception events (`jdk.ExceptionStatistics`, `jdk.JavaExceptionThrow`) |

Each Code area opens on its flamegraph — Allocation and Lock contention with their table beside it.
Any other recorded type with stacks (a second sampler, native allocation, method traces, custom
events) is listed in `moreFlamegraphs`, beside the menu, each with the export that draws it.

**A heap dump (`.hprof`)** — the index and most reports are built by `heap_prepare` the first time;
an area waiting for it is offered as `NEEDS_PREPARATION`:

| Group | Areas |
|---|---|
| Contents | Heap overview, Class histogram, Top consumers, Biggest objects |
| Retention | Leak suspects, Dominator tree, Class loader leaks, GC roots and threads |
| Waste | Duplicate strings, Wasteful collections |

**Beyond one profile:** comparing two recordings (**compare-jfr**), deciding whether a change
regressed (**regression-check**), SQL over a profile's events (**jfr-sql**) or a heap dump's index
(**heap-sql**), and turning a hotspot into a change in this repository (**advise-jfr**).

## 3. What is here now

Show what the user can start from, not only what is possible:

1. `profiles_list` — the analysed profiles, newest first. Name a few with their kind and recording
   window, each linked.
2. When the `remote_` tools are served, `remote_status` — whether a remote with deployed recordings
   is configured and answering. Mention it in one line; finding sessions there is **analyze-remote**.
3. When the user names one of the profiles, call `profiles_summary` on it and put **its**
   `investigationAreas` to the user exactly as the **analyze-jfr** skill describes — one multi-select
   question per group in Claude Code (`AskUserQuestion`), a table elsewhere. `AVAILABLE` areas are
   offered with their weight; `NEEDS_PREPARATION` ones with "needs heap_prepare (slow)"; `NOT_RECORDED`
   ones are named in one line beneath, with their `remedy`, so the next recording can capture them.
   Then hand off to the skill that takes the chosen area.

Nothing here? Say so, and offer the two ways in: a recording file on this machine
(`recordings_analyzeFile` with its absolute path, through **analyze-jfr** or **analyze-heap**), or
**profile-run** to record this project now.

## 4. Which skill takes it

| The user wants to… | Skill |
|---|---|
| Read a recording: where the time, CPU, allocations or waits went; GC, JIT, threads, the container | **analyze-jfr** |
| Understand what an application threw | **analyze-exceptions** |
| Read a heap dump: what holds the memory | **analyze-heap** |
| Compare two recordings, or check a change for a regression | **compare-jfr**, **regression-check** |
| Analyse what a deployed environment recorded | **analyze-remote** |
| Record this project first | **profile-run** |
| Fix what a profile found, in this repository | **advise-jfr** |
| Ask a question no dashboard answers | **jfr-sql**, **heap-sql** |
| Write the findings up | **report** |

## Link what you name

Whenever a reply names something Microscope can open — the profile, a flamegraph, an operation, a
trace, an endpoint, a statement group, a GC, JIT or thread page, a heap class or object, a
differential, a catalogue row, a ledger entry — make the name a Markdown link marked with `↗`:
`[trace 7e34c5994dc96208 ↗](<its uiLink>)`, and in a table a column of `[↗ open](<its uiLink>)`.
Every link carries the mark — the profile's own too, the one most often left bare:
`[checkout candidate ↗](<its uiLink>)` — and never the bare URL. The `↗` is what makes it a link
the user sees: a terminal that renders links shows the text alone, clickable, with the URL behind
it, and an unmarked name reads as plain words. That holds in every reply, not only in a written
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

- **It will not analyse.** It shows what can be asked and stops; the chosen area is the next skill's
  work.
- **It will not decide what a profile can answer.** The tables above say what is possible in
  general; only `profiles_summary` says what one profile recorded.
- **It will not start slow work.** No `heap_prepare`, no `recordings_analyzeFile`, no download — each
  is offered and waits for the user.

Related skills: `analyze-jfr` for the menu of one profile and the work after it, `analyze-heap` for a
heap dump, `profile-run` when there is nothing to read yet.
