---
name: analyze-remote
description: "Finds and analyses JVM recordings that live on the remote (a Jeffrey Hub, or the recording volume on an Azure file share) rather than on this machine — the JFR recordings, heap dumps, application logs, GC logs and crash files a deployed application produced. Use whenever the user asks about what an environment recorded or wrote rather than about a file they have: production, staging, a named service or pod, \"the last hour\", \"since the deploy\", \"what the remote has\", \"why was prod slow this morning\", \"why did the pod's JVM die\", \"the exceptions in the service log\". It locates the session, asks which interval matters, pulls in that part of the recording — or the one file that matters — and hands off to analyze-jfr or analyze-heap, or reads the log itself. For a recording file already on this machine, analyze-jfr applies directly."
allowed-tools: mcp__plugin_microscope_jeffrey__* mcp__jeffrey__*
---

# Analysing a recording that lives on the remote

The **remote** is where deployed applications send their recordings: a Jeffrey Hub, or the
recording volume reached straight on an Azure file share. Microscope connects to exactly one, and
this skill is the bridge: find the session, pull it in, and from there it is an ordinary profile
that `analyze-jfr` and `analyze-heap` answer about.

Tool names below omit the prefix your client puts in front of them — `mcp__plugin_microscope_jeffrey__` in Claude Code with the `microscope` plugin, `mcp__jeffrey__` in Codex or wherever the server is registered by hand as `jeffrey`, `mcp_jeffrey_` in Gemini CLI; the rest of the name is exact camelCase: `remote_sessions`, not `remote_list_sessions`.

If no `remote_` tool is advertised, this Jeffrey has remote access switched off
(`jeffrey.microscope.mcp.remote.enabled=false`). Say so rather than guessing at a path — the
recordings are not reachable from here.

If `remote_status` answers `state: NOT_CONNECTED`, remote access is on but no remote is connected.
Connecting one is the user's step, and no tool does it. Give them both routes from the answer: the
Remote page (its link), whose form checks that the Jeffrey Hub answers, and the
`jeffrey.microscope.remote.*` properties (name, hostname, port, plaintext) followed by a restart.
Use the user's own hostname and port if they gave them, otherwise keep the placeholders. Never
invent an address. `plaintext=true` is only for an in-cluster or trusted-LAN hub without TLS. If
the recordings sit on an Azure file share and there is no hub to reach, the answer also gives the
`jeffrey.microscope.remote.azure-files.*` entry (endpoint, share, protocol, a SAS token or
`auth=entra`). Offer to analyse a local `.jfr` in the meantime, and call `remote_status` again once
they say it is connected.

## The shape of the whole thing

```
remote_sessions(withinLastMinutes=60)                  → rows, newest first, each with a sessionRef
  … which interval? — ask, with started/duration in front of the user
remote_download(sessionRef, window=LAST_MINUTES)       → recordingId + bundleId + items, or RUNNING with an operationId
   or remote_download(sessionRef, window=STARTUP|LATEST|PEAK|WHOLE)
   or remote_download(sessionRef, window=BEFORE|AROUND, atEpochMs, minutes)
   or remote_download(sessionRef, startEpochMs, endEpochMs) → a span of your own
   or remote_download(sessionRef, fileIds=["…"])       → the same, for files picked from remote_files
   or remote_download(sessionRef)                      → the whole session, when it is short
recordings_analyzeRecording(recordingId)             → profileId, or RUNNING with an operationId
   and, for a memory question, recordings_analyzeRecording(<heap item's recordingId>) → its own profileId
operations_status(operationId)                       → where any of these has got to
… then analyze-jfr (or analyze-heap on the heap item's profile)
recordings_delete(recordingId)                       → when the question is answered

remote_files(sessionRef)                               → the files beside the recording: logs, crash file, perf counters
remote_fetchFile(sessionRef, fileId)                   → one of them, as a path on this machine — read it yourself
```

Three calls on the main path, and the third is a tool you already know; the question in between
is what keeps a day-long session from being pulled for an hour's worth of answer, and
`operations_status` is how the two long ones are followed. There is no remote-specific analysis:
once a part of a session is downloaded it is a bundle of normal Jeffrey recordings.

## 1. Find the session — one call, not four

`remote_sessions` searches **every workspace and project of the remote at once** and returns one
flat table. Do not go looking for a workspace, then a project. There are no tools for that walk,
because it would cost round trips and let you pair a workspace with the wrong project.

Narrow it with the arguments instead, all optional and all matched loosely:

| Ask | Call |
|---|---|
| "what did production record in the last hour" | `remote_sessions(workspace="production", withinLastMinutes=60)` |
| "the checkout service today" | `remote_sessions(project="checkout", withinLastMinutes=1440)` |
| "what is recording right now" | `remote_sessions(status="ACTIVE")` |
| "anything at all" | `remote_sessions()` |

`withinLastMinutes` is an **overlap**, not a start time. A JVM that began recording three hours ago
and is still running matches a 60-minute window, because it was recording during it. That is
usually what someone means by "the last hour", and it is the opposite of what filtering on start
time would give them.

The fields to read before doing anything else — each row of `sessions` in the structured answer
(the Markdown table beside it shows the same, readably):

- **`recordingId` / `profileId`** (the table's `local` column) — both null means the session is not
  here yet. A `recordingId` alone means it has been downloaded but not analysed, so skip to step 4
  (the answer's `followUp` already offers `recordings_analyzeRecording` for it). A `profileId`
  means it is already analysed, so skip to step 5 and use that `profileId` directly. **Always check
  this before downloading.**
- **`startedAtEpochMs`** and **`durationMs`** (null while still recording) — the span there is to
  choose an interval from, and how much data is behind it; with **`sizeBytes`**, what pulling all of
  it would cost.
- **`status`** — `ACTIVE` is still recording. That is fine to download; you get the chunks that
  have been rolled so far, not a broken file.
- **`sessionRef`** — what `remote_download` and `remote_files` take. Copy it exactly.

`remote_sessions` never offers a download as a next call: which part of a session to bring is the
user's choice, so the answer says so in `followUp.guidance` instead.

If nothing comes back (`status: EMPTY`), read `failures` before concluding there is nothing. A
remote, workspace or project that did not answer is listed there, with a `kind` such as
`UNREACHABLE`, and "the remote is unreachable" is a completely different answer from "no
recordings". `remote_status` shows which remote is connected and whether it responds.

The table is a page, not the whole. `remote_sessions` defaults to 50 rows, and the output byte budget
may reduce that further. Read `returned`, `nextCursor` and `hasMore`; when `hasMore=true`, pass the
cursor back with the **same filters**, even if the page contains fewer rows than requested. Read
`complete` separately — it says whether every part of the remote answered, which `hasMore` does not. A
relative window keeps the cutoff of the first call across its pages, so paging through "the last
hour" does not slide the hour.

## 2. Choose — and ask the user only when the choice is real

Resolve it yourself when the answer is obvious: one session in the window, or one clearly matching
the project the user named. Just proceed.

**Ask once, quoting the rows, when:**

- several sessions match and they differ in a way that matters — different projects, or one is
  three minutes and another is an hour;
- the session you would pick runs for hours or days, and the user has not said which part of it
  they mean — that question is section 3;
- the user named an environment that matches more than one workspace.

Ask with the facts in front of them — *"production has three sessions in the last hour: checkout
(18m, 240MB), search (54m, 1.1GB), api (3m, 12MB). Which?"* — not with an abstract question. Never
ask which workspace, then which project. Nobody knows their workspace ids, and each
step buys nothing that the table did not already show.

## 3. Ask for the interval, not the session

A session on the remote is not a file, it is a JVM's whole recording life: hours or days of chunks
rolled every few minutes, and the question is almost never about all of it. "Why was prod slow this
morning" is about this morning. Before downloading a session that runs longer than a few minutes,
ask **which interval** — with its `started` and `duration` in front of the user, so the answer can
be concrete: *"checkout has been recording since 06:12 today, 9 hours so far. The last hour, its
startup, the busiest stretch, or a particular window?"*

Most answers have a name; pass it as `window` and let Jeffrey measure it on the session — from the
session's own end, its own chunks — rather than working out times yourself:

| The user says | `window` |
|---|---|
| the last hour, the last 20 minutes | `LAST_MINUTES` (`minutes`, 60 when omitted) |
| since it started, the startup, the warm-up | `STARTUP` — the first chunk |
| right now, what it is doing at the moment | `LATEST` — the newest finished chunk |
| the worst moment, the busiest time | `PEAK` — the largest compressed chunk; the answer's `chosen.evidence` says by how much |
| before the heap dump, before it crashed | `BEFORE` with `atEpochMs` = that file's `createdAtEpochMs` from `remote_files`, and `minutes` |
| around 10:42 | `AROUND` with `atEpochMs` and `minutes` |
| all of it | `WHOLE` |

Two times of their own ("from 09:00 to 10:30", "yesterday") are `startEpochMs` / `endEpochMs` in
UTC epoch milliseconds, with no `window`. One bound alone is fine — `startEpochMs` alone reads to the
session's end, `endEpochMs` alone back to its start. A named window is a decision: Jeffrey does not
ask the user again about the part to bring.

`STARTUP_NOT_RETAINED` means retention has already removed the session's first chunk; the
answer names the oldest chunk still there. Nothing was downloaded and nothing was substituted — say
so, and ask the user whether that chunk, `LATEST`, `PEAK` or a window will do.

A window is **always covered**: a chunk holds a stretch of the recording — its length is the
profiler's configuration — and every chunk whose stretch touches the window is brought, so the
recording begins at or before the interval and ends at or after it, with some slack at either end
rather than a gap. The answer reports the span the chunks actually cover; that span, not the
interval you asked for, is what the profile's figures are about.

When a transfer outlasts the call, follow the call its `followUp` names — it names the exact chunks
or span this download resolved to — rather than repeating `window`: `LATEST` asked again later can
be a newer chunk, and that would be a second download instead of joining the first.

A session of a few minutes is pulled whole; asking would cost more than the download. Do **not**
call `remote_files` to choose chunks by hand for a window — a long session lists thousands of rows,
and the tool does the mapping. `remote_files` is for choosing by *name*: when the user or the listing
points at particular files — the chunk rolled right after the deploy, a chunk and the heap dump
taken beside it — pass their `fileId` values as the `fileIds` array (`["c2", "c3"]`) instead of a
window.

Chunks named that way have to be **next to each other**. The recording reports one span across the
files it holds, so a skipped chunk leaves no hole to see: the recording would claim the span from
the first to the last while holding only part of it. Naming a gapped pair is refused, and names the
chunk in between. Artifacts beside the run — a heap dump, a log — are free to pick, and if
what you actually want is a span rather than particular files, `startEpochMs`/`endEpochMs` picks
the chunks for you and cannot come out gapped.

**A partial look.** To learn whether a session is worth a wider window at all — does it throw,
does it record `jdk.ObjectAllocationSample`, when is it busy — take a narrow window first, one or
two chunks, and answer it with the ordinary tools: `profiles_summary`, the timeline, `jfr_*`. Then
widen, or stop. A window is not the session: figures hold for that window only, a rate measured
in it must never be extrapolated across the whole recording, and a finding worth reporting is
confirmed on the window that matters. A single chunk may equally go `remote_fetchFile` →
`recordings_analyzeFile`; the result is the same kind of profile.

## 4. Pull it in

Every download lands as one **bundle**. `remote_download(sessionRef, startEpochMs, endEpochMs)` brings
the recording files covering the window, nothing else; `remote_download(sessionRef, fileIds=["…"])` brings
the named files, the recording files and any artifact beside them; and `remote_download(sessionRef)`
alone brings every finished file of the session. In the bundle all the recording files are **one**
JFR recording — kept as the several files they are and fetched in parallel; nothing joins them, and
the parser reads them as independent inputs. Each heap dump (`.hprof`, `.hprof.gz`) and the
perf-counters file are **recordings of their own**, and the logs and crash file are download-only
files of the bundle. Each answers `status: DOWNLOADED` with the JFR recording's `recordingId`, the
`bundleId`, and `items` — every item that is analysed on its own, the session's recording first, each
with its `recordingId`, `eventSource` (`JDK` or `ASYNC_PROFILER` for the recording, `HEAP_DUMP`,
`PERF_COUNTERS`), `name` and `profileId` once it has one; when there are heap dumps the answer's
guidance names them. A part of a session reports `coveredStartEpochMs` / `coveredEndEpochMs`, the span
its files cover. A part is a bundle of its own: it is never answered from a whole-session copy that
is already here, and the Recordings list marks it as a part (its span is in the `origin.window`
tag), so it never reads as the whole session. A whole session is answered from the copy already here only when that copy was taken after the
session finished; a copy taken while it was still recording is downloaded again as a new bundle.

It does **not** build a profile; `recordings_analyzeRecording(recordingId)` does that, one item at a
time, and returns the `profileId` every analysis tool takes. The `recordingId` at the top of the
answer is the JFR item; a heap dump is analysed by its own item's `recordingId`, into a profile of
its own. The two are separate on purpose: a large session is a
long transfer and then a long analysis, and one call doing both is the shape that hits a tool
timeout with nothing to show for it.

Both calls answer inside the call for a small session and hand back an `operationId` for a large
one — see the last section for how that is followed. Say what you are doing before starting a big
one.

Downloading the same whole session twice is wasteful and never necessary — `remote_download` returns
the recording it already has rather than fetching it again, but you should have read the row's
`recordingId`/`profileId` in step 1 instead of relying on that. Those speak only of the whole session: a window
pulled earlier is in `recordings_list`, tagged `origin.window`, and is where to look before pulling
the same hour a second time.

## 4b. The files beside the recording — when the question is about what the JVM *wrote*

A session holds more than its JFR chunks. A JVM provisioned by Jeffrey leaves `gc.jvm-log` (the
`-Xlog` output, rotated as `.0`, `.1`…), `perf-counters.hsperfdata`, the application's own `.log`
if it was pointed at the session directory, and — when it died — `hs-jvm-err.log`, often with
**nothing else beside it**, because the first chunk never rolled. `remote_sessions` shows only a file
count; `remote_files(sessionRef)` shows what they are.

When the question is "what exceptions did the service log", "why did the JVM crash", "what does
the GC log say" — or the session has no finished recording at all — do not download the session:

1. `remote_files(sessionRef)` — read the `type` column: `APP_LOG`, `JVM_LOG`, `HS_JVM_ERROR_LOG`,
   `PERF_COUNTERS`, `HEAP_DUMP`, `HEAP_DUMP_GZ`. The `fetch` value decides the row: only one
   reading `FETCH` can be fetched, by its `fileId`. `DOWNLOAD` means a recording chunk, and `NEVER`
   means a type Jeffrey does not classify, which the remote will not serve on its own — `remote_download`
   is the only way to that one. Every artifact reads `FETCH`, including one of a session still
   recording: an application log is worth grepping while it is being written. More rows than one
   page: `hasMore` with a `nextCursor`, passed back as `cursor`.
2. `remote_fetchFile(sessionRef, fileId)` — answers `status: FETCHED` with the **absolute `path`**
   the file now has on this machine, and its `filename` (or `RUNNING` with an `operationId` for a
   transfer that outlasts the call). A file already fetched comes back as it is; one whose transfer
   failed or was cancelled is started again by calling the tool again.
3. **Read the file with your own tools.** Jeffrey runs on this machine and hands you the path
   rather than parsing the log for you: `grep -n 'Exception' <path>`, `sed -n '1200,1260p' <path>`,
   `tail -200 <path>`, your file reader. A crash file reads top-down — the `#` header names the
   signal or the `fatal error`, `Current thread` and the `Java frames:` block under it say where,
   `VM state` and the `Heap:` block under `P R O C E S S` say what the JVM was doing. A GC log's
   `[12.345s]` decoration is uptime; when the session's recording has been analysed, `remote_files`
   and `remote_fetchFile` name the profile and its zero point (`profilingStartedAtEpochMs`), so that uptime is `zero point + 12.345s`
   on the JFR timeline and `jvm_gc` on the profile can be read against it.
4. A fetched heap dump is a profile's input, not a text: pass its path to `recordings_analyzeFile`
   and hand off to **analyze-heap**. A heap dump the session's download already brought needs no
   fetch — it is an item of the bundle; analyse that item's `recordingId` instead.

The path is where the file stays: beside the profile (`profiles/<id>/artifacts/`) when the
session is analysed, under `artifacts/<workspace>/<project>/<session>/` otherwise. A file fetched
*before* the session was analysed is moved beside the profile by the next `remote_fetchFile` rather
than pulled down twice, so the path in an older answer can be stale — take the current one from
`remote_files`, which gives it as `localPath` once it is there, and for an artifact
`remote_download` brought along with the recording.

Cite a log the way `report` asks: the file name from `remote_files` and the line number of what you
quote, so the reader can open the same line.

## 5. Analyse

You now have a `profileId` and the remote is out of the picture.

- A JFR recording → the **analyze-jfr** skill: `profiles_summary` first to see what the profile
  can answer, then the route it points at.
- A heap dump → the **analyze-heap** skill instead, on the heap item's own profile.

A session often carries both a JFR recording and a heap dump. The dump is never inside the JFR
profile: it arrives as another item of the bundle — the `items` entry with `eventSource: HEAP_DUMP`.
For a memory question, `recordings_analyzeRecording` on that item's `recordingId` builds its profile,
and the `heap_` tools take *that* `profileId`; called on the JFR profile they are refused with a
pointer back to the bundle. A perf-counters item builds a profile too, but no tool reads its
counters yet: `profiles_summary` describes it in one line, and the user reads it at its `uiLink`.

## 6. Clean up

The remote is the copy of record; Microscope holds what is being read. A window that has answered its
question, and a partial look once the real window is downloaded, are removed with
`recordings_delete(recordingId)` — recording, profile and files together, nothing on the remote
touched. It deletes one item of the bundle and reports the `bundleId`; the other items stay, and
the bundle goes with its last item, so removing a whole window means deleting each of its items. Say which one you are deleting; a profile the user opened in the browser stops working
the moment it goes. Leave a profile the user asked to keep, or one another skill is still reading.

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

**It does not delete anything on the remote.** `recordings_delete` removes a recording from *this*
Jeffrey; no tool here removes a session, a file or a project from the remote. Data retention there
is the Jeffrey Hub's business.

**It does not push.** Recordings travel from the remote into this Jeffrey, never the other way.

**It does not connect a remote.** The remote is declared in Microscope's configuration or connected
through its UI, and there is only ever one. If `remote_status` shows a different one than the user
wants, say so — changing it is an operator's decision, not something to work around.

**It treats a file share like a hub.** A remote of kind `AZURE_FILES` is the recording volume
reached straight on an Azure file share, with no hub in between: its sessions list, download and
fetch like a hub's. One with `readOnly: true` is declared to refuse every change; no tool here
changes a share either way.

## When a transfer outlasts the call

A large session takes longer to pull than a client waits for a tool call. `remote_download` then
returns a status saying the transfer continues, and an `operationId`, rather than a `recordingId`.
**Poll `operations_status(operationId)`** — it reports the attempt's progress, its result once the
transfer lands, and the exact retry instructions when it failed — rather than calling
`remote_download` again; a second call answers from the local store and is harmless, but it is the
poll that says what is happening. `operations_cancel(operationId)` asks a transfer to stop, best
effort, and rolls back nothing already written. A failed transfer is remembered for an hour, and
during that hour `remote_download` reports the failure rather than starting again unless it is called
with `retry=true`.

The same applies one step later: `recordings_analyzeRecording` on a large recording returns a status
of `RUNNING` with its own `operationId`, and `operations_status` says when the profile is ready.
`recordings_status(recordingId)` answers the same question for a `recordingId` you already hold,
which after `remote_download` you do. Operation ids live in Jeffrey's memory for an hour after the
work completes and are forgotten on restart.
