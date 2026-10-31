---
name: analyze-jfr
description: "Analyses a JVM profile held by a running Jeffrey Microscope — CPU, wall-clock, allocation, lock contention, trace latency, and the machine underneath: garbage collection, safepoints, JIT compilation, threads, native memory, the container and the JVM's configuration. Starts from the catalogue or from a .jfr file Jeffrey has not seen yet. Use whenever the user asks why something is slow, where the time goes, what is allocating, why GC pauses are long, what is pausing the JVM, what the JIT compiler or deoptimisation is doing, which threads are burning CPU, why memory grows outside the heap, whether the container is throttling, what a JFR recording or flamegraph shows, or mentions a Jeffrey profile, a .jfr file or async-profiler output. For a heap dump or .hprof file, analyze-heap applies instead."
allowed-tools: mcp__plugin_microscope_jeffrey__* mcp__jeffrey__*
---

# Analysing a Jeffrey profile

Jeffrey holds *profiles*: one analysed JFR recording or heap dump each. Nearly every tool reads.
The few that do not create a profile or a cache, start work on the remote or in your editor, or stop
work that was started — `recordings_` turns a recording *file* into a profile, and
`operations_cancel` stops an import that was a mistake — and none of them changes an analysed
profile.

Tool names below omit the prefix your client puts in front of them — `mcp__plugin_microscope_jeffrey__` in Claude Code with the `microscope` plugin, `mcp__jeffrey__` in Codex or wherever the server is registered by hand as `jeffrey`, `mcp_jeffrey_` in Gemini CLI; the rest of the name is exact camelCase: `jfr_listTables`, not `jfr_list_tables`.

## 1. Get a `profileId`

Every tool takes one, except `profiles_list` and the `recordings_`, `remote_` and `operations_`
families, which are how a `profileId` comes to exist. `profiles_list` pages: a hundred profiles per
call, with `nextCursor` and `hasMore` in the answer, so a catalogue past that size needs a second
call carrying the cursor before "it is not there" is true.

**The user named a file** (`target/app.jfr`, anything with a recording extension) — it may not be
in Jeffrey yet. Call `recordings_analyzeFile` with the **absolute** path;
`recordings_analyzeFile` returns the existing profile for a file with the same name and size as one
already imported; after re-recording to the same path, pass `force=true`. The Jeffrey process opens
that path, so
the file has to be on the machine Jeffrey runs on — a container or a remote Jeffrey cannot see your
working directory. A small recording is analysed inside the call and comes back with its
`profileId`. A large one comes back with a status of `RUNNING` and an `operationId` — and no
`recordingId`, because the copy may not have finished — and `operations_status(operationId)`
follows the copy and the analysis until the `profileId` appears. Poll that rather than calling
`recordings_analyzeFile` a second time to see whether it is done. `operations_cancel(operationId)` stops an import started by
mistake. Operation ids live for an hour in Jeffrey's memory and do not survive a restart.

**The recording is on the remote** — the user asked about an environment rather than a file
("production", "staging", "what recorded in the last hour"), and nothing local matches. Switch to
the **analyze-remote** skill: it finds the session on the remote, pulls it in and comes
back here with a `profileId`.

**Otherwise** — `profiles_list`, pick the profile, then `profiles_summary` to learn what it can
answer before asking for it. That one call carries what `profiles_get`, `profiles_features` and
`profiles_samplerHealth` each carry a part of: `disabledFeatures` names what the profile lacks
(traces exist only if the app ran Jeffrey's instrumentation; `HEAP_DUMP` there means no dump),
`eventTypes` lists every recorded event type with its totals, and `topFindings` carries the
auto-analysis findings when they have been computed.

A profile whose `event source` column reads `HEAP_DUMP` is a heap dump: switch to the
`analyze-heap` skill, because flamegraphs and traces do not apply to it.

## Ask before you analyse — the user picks what to spend on

Orientation is cheap; analysis is not. `profiles_summary` is one compact call, while one flamegraph
or trace export runs to tens of thousands of characters and a sweep of every family costs a dozen
calls whose findings mostly go unread. So the user decides what is worth that, not habit:

- **The question names an area** — "why is `/orders` slow", "what is allocating", "are the GC
  pauses long" — go straight to that area through the routing table below. Do only that area, and
  do not show the menu.
- **The question is open** — "analyse this", "what is wrong", "review this recording" — call
  `profiles_summary`, put its `investigationAreas` to the user as below, and **stop**. Wait for the
  user's choice before any other call. A fired rule in `topFindings` and a `capabilityGaps` entry
  the question needs are worth one line each above the menu, because they are already paid for;
  neither is a reason to start work unasked.

### The menu is `investigationAreas`

Jeffrey builds the menu, so do not assemble one yourself. Each entry has an `area`, its `group`
(`CODE`, `WAITING`, `JVM`, `OVERALL` for a recording), a `title`, the `question` it answers, and its
`availability`:

- **`AVAILABLE`** — offer it, with its `weight` and, when `suggested` is true, its `evidence` (the
  rule that fired, or the share of samples that points there). `nextTools` are the calls that open
  it, ready to send.
- **`NEEDS_PREPARATION`** — its data is there but has to be built first; its `nextTools` are that
  build (`heap_prepare`, slow). Only a heap dump's areas are ever in this state.
- **`NOT_RECORDED`** — do not offer it. Name these in one line beneath the menu, with their `remedy`
  (`event=cpu`, `wall=10ms`, `alloc=512k`, `lock=10ms`, …) so the next recording can capture them.

A menu of the heap groups (`HEAP_CONTENTS`, `HEAP_RETENTION`, `HEAP_WASTE`) means a heap dump: switch
to `analyze-heap`, which puts that menu the same way. An empty `investigationAreas` means a
perf-counters snapshot, which no tool reads yet: give the user its `uiLink`.

**Put it as a structured question when your client has one.** In Claude Code that is
`AskUserQuestion`: one multi-select question per group that has an `AVAILABLE` area, in group order,
`header` the group in a word (Code, Waiting, JVM, Overall), one option per area with the `title` as
its label and the description `weight · question` — with `· suggested: <evidence>` when suggested,
and `· covered` when the ledger already has it. A group with a single available area joins another
group's question when that one has room, since a question needs two options. Everything else — the
fired rules, the gaps the question needs, the not-recorded line — is text above the question.

**Without a structured question tool** (Codex, Gemini CLI), write the menu as a table grouped by
group — area, weight, what it answers, why it is suggested — then the not-recorded line, and stop.

The user may pick several; take them in the order they gave, sending each area's `nextTools`
unchanged. A pick that comes back `NOT_RECORDED` after all is reported as not assessed, and the next
pick goes on.

**Weight** is what an answer puts into this conversation: **LIGHT** one or two compact records,
**MEDIUM** a dashboard or a ranking, **HEAVY** a long document — a flamegraph, a trace, a dump —
delegated to the analyst when the client has one. It is not `jeffrey/cost`, which is what Jeffrey
spends to compute the answer.

### After every answer, offer — do not run

Close each answer with at most two follow-ups taken from its `followUp.nextTools` — each carries its
`weight` and its `why` — and wait. In Claude Code put them as one `AskUserQuestion` with an option
per follow-up and a "Stop here" option; elsewhere, one line each. A follow-up the user did not ask
for is not run, however well the evidence points at it: "the allocation flamegraph would name the
call sites (HEAVY)" is the sentence, not the export.

### Keep a ledger

Across the conversation keep a short block, updated after every answer:

- **Covered** — each area answered, with its one-line result and its link;
- **Open leads** — follow-ups offered and not yet taken;
- **Not assessed** — areas the recording cannot answer, and picks that turned out `NOT_RECORDED`.

When the user asks what else there is, show the menu again with the covered areas marked. The
ledger is yours: nothing in it is sent to a tool, and the `report` skill builds its findings and its
**Not assessed** section from it.

## Route from the summary — never open with a flamegraph

A flamegraph of a recording that spent its time waiting reports the application as idle, and a
graph of a five-minute average hides a thirty-second spike. So the first export is decided by what
`profiles_summary` says, not by habit. When the question names an area, or once the user has picked
from the menu, read three things from it and route:

| What the summary shows | Go to |
|---|---|
| `capabilityGaps` names something the question needs | Say so first; a question the recording cannot answer is reported as **not assessed**, never as a negative result — the `report` skill has the shape |
| `topFindings` carries a rule that fired | The finding's `nextTool` — a ready call, `{tool, arguments, why}` — for the figures behind the rule; a fired rule is a lead, not a diagnosis |
| The question is "this endpoint is slow" and `TRACES` is not in `disabledFeatures` | `traces_` — `references/latency.md`; `http_overview` and `jdbc_overview` when it is |
| Execution or CPU-time samples dominate `eventTypes` | `flamegraph_export` of that type, after `timeline_hotWindows` when the shape over time matters |
| Threads spent the time waiting rather than running | `blocking_` and `io_` — the waiting a CPU graph cannot see |
| Allocation samples are large, or GC is in `topFindings` | `jvm_gc` for whether it hurts, then the allocation flamegraph for why |
| `jdk.OldObjectSample` is recorded, or the heap grows across the recording | `memory_leakCandidates`; a heap dump and `analyze-heap` for what retains it |
| Two profiles of the same workload | `compare-jfr` |
| The question is about exceptions, errors or timeouts in the application's code | `analyze-exceptions` |
| `topFindings` is empty and `capabilityGaps` names `autoAnalysis` | The rules did not run, so their silence is a gap and not a clean result. `jvm_autoAnalysis` with `compute: true` runs them; the other families answer either way. Empty with no such gap means the rules ran and cleared the recording |

When the summary points at more than one route and the user asked about only one, run that one and
offer the others as follow-ups — they are independent, so nothing is lost by waiting.

## Where the detail is

The route names a family; how to read it is in a file of its own beside this one. Read the one the
route goes to before its first call, and not the others:

| The route goes to | Read first |
|---|---|
| a family not named above, or every tool of one | `references/families.md` |
| a flamegraph — which event type, which window | `references/flamegraphs.md` |
| a slow endpoint, request or outbound call — traces, HTTP, JDBC, gRPC, trace attributes | `references/latency.md` |
| GC, safepoints, JIT, threads, native memory, the container, flags, exceptions, the host | `references/jvm.md` |
| waiting on I/O or locks, allocation by type, leak candidates | `references/waiting-and-memory.md` |

## 2. Read every answer the way it tells you to

Every answer is a typed record in `structuredContent` (the text beside it is the same record as
JSON, or the Markdown document with a footer), and every one reads the same way:

- **Times** are UTC epoch milliseconds (`…EpochMs`) — a window from one answer is passed to the next
  tool unchanged as `startEpochMs`/`endEpochMs`; durations carry their unit (`…Ms`, `…Nanos`).
- **`status`** says when there is nothing to show — `NOT_RECORDED`, `NOT_COMPUTED`, `NO_TRACES`,
  `EMPTY` — with a `reason`; that is an answer to report, not an error to retry. An id Jeffrey does
  not know comes back as a tool error naming it.
- **`followUp.nextTools`** are the next calls with this answer's ids, windows and cursors filled
  in — send them as they are; **`followUp.guidance`** is advice that is not a call.
- **`hasMore` + `nextCursor`** continue a list: pass `nextCursor` back as `cursor` with the same
  arguments. Enum values are upper case (`SUMMARY`, `SERVER`), in answers and arguments alike — except
  `profiles_viewLink`'s `view`, which takes Microscope's own lowercase page paths.
- **`uiLink`** opens the same view in Microscope, and a row that names its own thing — a trace, an
  operation, an exemplar — carries its own. Put each beside the name it opens (*Link what you name*
  below); never fetch it or read it back. Nothing is exported to a file.

`flamegraph_export`, `traces_traceExport` and `traces_operationExport` return Markdown documents
that open with their own reading instructions — what `self` versus `total` means, what the frame
tags mean, what was pruned, and how to analyse that event type. Follow that preamble rather than
generic flamegraph conventions; Jeffrey's accounting is stated there in the version that matches
the code that produced it.

## Hand the reading to the analyst

An export can run to 120,000 characters, and answering a question well often takes several. A
**`profile-analyst`** agent runs a sequence and returns only the findings — the hottest frames with
their shares, the causes named. Everything it read stays in its own context. The Claude Code plugin
ships it as `microscope:profile-analyst`; in Codex it is the custom agent from
`codex/agents/profile-analyst.toml`. If your client has no such agent, read the exports here and
keep the sequence short.

**Two or more exports are never read here.** When the question needs more than one — two areas the
user named or picked, a flamegraph and a trace, a deep chase down one path — send one
`profile-analyst` call per export, all in a single message so they run at once, each with the
`profileId`, the one question and the event type it reads. Read nothing they will read. An area
nobody chose is not sent to an analyst to be safe.

Read an export here, in this conversation, only when there is exactly one and its result will be
discussed turn by turn. The analyst returns a report; it cannot answer a follow-up about a document
the conversation never saw.

Whatever it reports, keep here: mapping frames onto the checkout, the recommendation, and every
question to the user. The analyst has no file tools and never proposes changes.

## Grounding claims

The exports carry call paths and figures. A frame may also carry a line, as `Class.method:214`, and
it is printed only when every sample at that frame agreed on it — a frame without one was sampled
across several lines or has no line information at all. Cite the path and the numbers the document
shows.

Read the real source before naming a file, method or line — never infer them from a frame name. Two
ways to reach it:

- If the repository is open in this session, read it with your own file tools.
- If the reader has IntelliJ open with the Jeffrey plugin, `ide_resolve` answers where a class and
  method actually live — the file, the line, and whether the position is decompiled, imprecise or
  older than the recording. It resolves nested classes, Kotlin facades and inherited methods that a
  grep for the frame name gets wrong, and it does not move the reader's editor. `ide_windows` shows
  which checkouts are open and which is on the commit the recording was built from.

The `advise-jfr` skill carries the full profile-to-code-change workflow.

## Write it up

The `report` skill defines the shape of a finding — symptom, the tool call and its figures,
interpretation, recommendation, confidence — and the rules that decide whether the report is
usable: every number names the call that produced it, shares say what they are a share of, counts
become rates against the recording length, sampled and rule-based evidence is capped at medium
confidence, and what the profile could not answer goes in its own **Not assessed** section rather
than passing as a clean result. Follow it for anything the user reads.

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

## When something fails

- `Profile … is not a heap dump` → it is a JFR recording; stay with `jfr_`, `flamegraph_`, `traces_`.
- `The recording path must be absolute` → pass the full path, not a repository-relative one.
- `No such recording file` but the file is right there → Jeffrey is looking on *its own*
  filesystem. Copy or mount the recording where Jeffrey can reach it, or upload it in the UI.
- No `recordings_` tool advertised → the installation trimmed the tool list with
  `jeffrey.microscope.mcp.families`. Upload and analyse in the Jeffrey UI, then work from
  `profiles_list`.
- Every call fails to connect → Jeffrey is not running at the configured address. Point the client
  at the real `…/api/mcp` endpoint: in Claude Code, `/plugin` → `microscope` → **Jeffrey MCP
  endpoint**; in Codex, the `[mcp_servers.jeffrey]` block in `~/.codex/config.toml`. The URL is
  `/api/mcp` on the address you open Jeffrey's UI on.
- The server answers 404 → it was started with `jeffrey.microscope.mcp.enabled=false` (it is on
  by default), or the configured URL does not end in `/api/mcp`, the one path it serves.

Related skills: `analyze-heap` for a heap dump, `compare-jfr` when there is a before and an after
to weigh against each other, `jfr-sql` for raw SQL against the profile, `advise-jfr` to go from a
hotspot to an edit in this repository, `report` for the shape every finding is written in.
