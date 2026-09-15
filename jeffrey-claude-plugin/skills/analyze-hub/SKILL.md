---
name: analyze-hub
description: Finds and analyses JVM recordings that live on a Jeffrey Hub rather than on this machine — the JFR recordings, heap dumps, application logs, GC logs and crash files a deployed application produced. Use whenever the user asks about what an environment recorded or wrote rather than about a file they have: production, staging, a named service or pod, "the last hour", "since the deploy", "what the hub has", "why was prod slow this morning", "why did the pod's JVM die", "the exceptions in the service log". It locates the session, asks which interval matters, pulls in that part of the recording — or the one file that matters — and hands off to analyze-jfr or analyze-heap, or reads the log itself. For a recording file already on this machine, analyze-jfr applies directly.
allowed-tools: mcp__plugin_microscope_jeffrey__* mcp__jeffrey__*
---

# Analysing a recording that lives on a Jeffrey Hub

A **hub** is where deployed applications send their recordings. Microscope connects to one or more
of them, and this skill is the bridge: find the session, pull it in, and from there it is an
ordinary profile that `analyze-jfr` and `analyze-heap` answer about.

Tool names below omit the prefix your client puts in front of them —
`mcp__plugin_microscope_jeffrey__` for the Claude Code plugin, `mcp__jeffrey__` in Codex and for any
hand-registered server, `mcp_jeffrey_` in Gemini CLI, which spells it with single underscores.
The part after it is exact and camelCase:
`hubs_sessions`, not `hubs_list_sessions`.

If no `hubs_` tool is advertised, this Jeffrey has hub access switched off
(`jeffrey.microscope.mcp.hubs.enabled=false`) or is connected to no hub at all. Say so rather than
guessing at a path — the recordings are not reachable from here.

## The shape of the whole thing

```
hubs_sessions(withinLastMinutes=60)                  → rows, newest first, each with a session_ref
  … which interval? — ask, with started/duration in front of the user
hubs_download(sessionRef, startTime, endTime)        → recordingId, or a running operationId
   or hubs_download(sessionRef, fileIds="…")         → the same, for files picked from hubs_files
   or hubs_download(sessionRef)                      → the whole session, when it is short
recordings_analyzeRecording(recordingId)             → profileId, or a running operationId
operations_status(operationId)                       → where either of the last two has got to
… then analyze-jfr (or analyze-heap for a dump)
recordings_delete(recordingId)                       → when the question is answered

hubs_files(sessionRef)                               → the files beside the recording: logs, crash file, perf counters
hubs_fetchFile(sessionRef, fileId)                   → one of them, as a path on this machine — read it yourself
```

Three calls on the main path, and the third is a tool you already know; the question in between
is what keeps a day-long session from being pulled for an hour's worth of answer, and
`operations_status` is how the two long ones are followed. There is no hub-specific analysis:
once a part of a session is downloaded it is a normal Jeffrey recording.

## 1. Find the session — one call, not four

`hubs_sessions` searches **every connected hub at once** and returns one flat table. Do not go
looking for a hub, then a workspace, then a project. There are no tools for that walk, because it
would cost four round trips and let you pair a workspace with the wrong project.

Narrow it with the arguments instead, all optional and all matched loosely:

| Ask | Call |
|---|---|
| "what did production record in the last hour" | `hubs_sessions(hub="production", withinLastMinutes=60)` |
| "the checkout service today" | `hubs_sessions(project="checkout", withinLastMinutes=1440)` |
| "what is recording right now" | `hubs_sessions(status="ACTIVE")` |
| "anything at all" | `hubs_sessions()` |

`withinLastMinutes` is an **overlap**, not a start time. A JVM that began recording three hours ago
and is still running matches a 60-minute window, because it was recording during it. That is
usually what someone means by "the last hour", and it is the opposite of what filtering on start
time would give them.

The columns to read before doing anything else:

- **`local`** — empty means the session is not here yet. `recording:<id>` means it has been
  downloaded but not analysed, so skip to step 4. `profile:<id>` means it is already analysed, so
  skip to step 5 and use that `profileId` directly. **Always check this before downloading.**
- **`started`** and **`duration`** — the span there is to choose an interval from, and how much
  data is behind it; with **`size`**, what pulling all of it would cost.
- **`status`** — `ACTIVE` is still recording. That is fine to download; you get the chunks that
  have been rolled so far, not a broken file.
- **`session_ref`** — what `hubs_download` and `hubs_files` take. Copy it exactly.

If nothing comes back, read the footer before concluding there is nothing. A hub that did not
answer is listed there, and "production is unreachable" is a completely different answer from "no
recordings". `hubs_list` shows which hubs are configured and whether each responds.

The table is a page, not the whole. `hubs_sessions` defaults to 50 rows, and the output byte budget
may reduce that further. Read `returned`, `nextCursor` and `hasMore`; when `hasMore=true`, pass the
cursor back with the **same filters**, even if the page contains fewer rows than requested. Read
`complete` separately — it says whether every hub answered, which `hasMore` does not. A
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
- the user named an environment that matches more than one hub.

Ask with the facts in front of them — *"production has three sessions in the last hour: checkout
(18m, 240MB), search (54m, 1.1GB), api (3m, 12MB). Which?"* — not with an abstract question. Never
ask which hub, then which workspace, then which project. Nobody knows their workspace ids, and each
step buys nothing that the table did not already show.

## 3. Ask for the interval, not the session

A session on a hub is not a file, it is a JVM's whole recording life: hours or days of chunks
rolled every few minutes, and the question is almost never about all of it. "Why was prod slow this
morning" is about this morning. Before downloading a session that runs longer than a few minutes,
ask **which interval** — with its `started` and `duration` in front of the user, so the answer can
be concrete: *"checkout has been recording since 06:12 today, 9 hours so far. The last hour, the
morning, or a particular window?"* The usual answers are the last hour, the last day, yesterday,
"since the deploy", or a pair of hours; turn the one you get into UTC epoch milliseconds and pass
it as `startTime` / `endTime`. One bound alone is fine — `startTime` alone reads to the session's
end, `endTime` alone back to its start.

A window is **always covered**: a chunk holds a stretch of the recording — fifteen minutes is
common — and every chunk whose stretch touches the window is brought, so the recording begins at or
before the interval and ends at or after it, with some slack at either end rather than a gap. The
answer reports the span the chunks actually cover; that span, not the interval you asked for, is
what the profile's figures are about.

A session of a few minutes is pulled whole; asking would cost more than the download. Do **not**
call `hubs_files` to choose chunks by hand for a window — a long session lists thousands of rows,
and the tool does the mapping. `hubs_files` is for choosing by *name*: when the user or the listing
points at particular files — the chunk rolled right after the deploy, a chunk and the heap dump
taken beside it — pass their `file_id` values as a comma-separated `fileIds` instead of a window.

Chunks named that way have to be **next to each other**. The recording reports one span across the
files it holds, so a skipped chunk leaves no hole to see: the recording would claim the span from
the first to the last while holding only part of it. Naming a gapped pair is refused, and names the
chunk in between. Artifacts beside the run — a heap dump, a log — are free to pick, and if
what you actually want is a span rather than particular files, `startTime`/`endTime` picks the
chunks for you and cannot come out gapped.

**A partial look.** To learn whether a session is worth a wider window at all — does it throw,
does it record `jdk.ObjectAllocationSample`, when is it busy — take a narrow window first, one or
two chunks, and answer it with the ordinary tools: `profiles_summary`, the timeline, `jfr_*`. Then
widen, or stop. A window is not the session: figures hold for that window only, a rate measured
in it must never be extrapolated across the whole recording, and a finding worth reporting is
confirmed on the window that matters. A single chunk may equally go `hubs_fetchFile` →
`recordings_analyzeFile`; the result is the same kind of profile.

## 4. Pull it in

`hubs_download(sessionRef, startTime, endTime)` brings the recording files covering the window as
one local recording, nothing else; `hubs_download(sessionRef, fileIds="…")` brings the named files,
the recording files and any artifact beside them; and `hubs_download(sessionRef)` alone brings every
finished file of the session — its recording files, with heap dumps and logs beside them. The
recording files are kept as the several files they are and fetched in parallel; nothing joins them,
and the parser reads them as independent inputs. Each returns a `recordingId`, and a part of a
session reports `windowStart` / `windowEnd`, the span its files cover. A part is a recording of
its own: it is never answered from a whole-session copy that is already here, and the Recordings
list names it after its span so two windows of one session read apart.

It does **not** build the profile; `recordings_analyzeRecording(recordingId)` does that and returns
the `profileId` every analysis tool takes. The two are separate on purpose: a large session is a
long transfer and then a long analysis, and one call doing both is the shape that hits a tool
timeout with nothing to show for it.

Both calls answer inside the call for a small session and hand back an `operationId` for a large
one — see the last section for how that is followed. Say what you are doing before starting a big
one.

Downloading the same whole session twice is wasteful and never necessary — `hubs_download` returns
the recording it already has rather than fetching it again, but you should have read the `local`
column in step 1 instead of relying on that. That column speaks only of the whole session: a window
pulled earlier is in `recordings_list`, tagged `origin.window`, and is where to look before pulling
the same hour a second time.

## 4b. The files beside the recording — when the question is about what the JVM *wrote*

A session holds more than its JFR chunks. A JVM provisioned by Jeffrey leaves `gc.jvm-log` (the
`-Xlog` output, rotated as `.0`, `.1`…), `perf-counters.hsperfdata`, the application's own `.log`
if it was pointed at the session directory, and — when it died — `hs-jvm-err.log`, often with
**nothing else beside it**, because the first chunk never rolled. `hubs_sessions` shows only a file
count; `hubs_files(sessionRef)` shows what they are.

When the question is "what exceptions did the service log", "why did the JVM crash", "what does
the GC log say" — or the session has no finished recording at all — do not download the session:

1. `hubs_files(sessionRef)` — read the `type` column: `APP_LOG`, `JVM_LOG`, `HS_JVM_ERROR_LOG`,
   `PERF_COUNTERS`, `HEAP_DUMP`, `HEAP_DUMP_GZ`. The `fetch` column decides the row: only one
   reading `fetch` can be fetched. `hubs_download` means a recording chunk, `when finished` means
   still being written, and `no` means a type Jeffrey does not classify, which a hub will not
   serve on its own — `hubs_download` is the only way to that one.
2. `hubs_fetchFile(sessionRef, fileId)` — returns the **absolute path** the file now has on this
   machine, with its `filename`. A file already fetched comes back as it is; one whose transfer
   failed or was cancelled is started again by calling the tool again.
3. **Read the file with your own tools.** Jeffrey runs on this machine and hands you the path
   rather than parsing the log for you: `grep -n 'Exception' <path>`, `sed -n '1200,1260p' <path>`,
   `tail -200 <path>`, your file reader. A crash file reads top-down — the `#` header names the
   signal or the `fatal error`, `Current thread` and the `Java frames:` block under it say where,
   `VM state` and the `Heap:` block under `P R O C E S S` say what the JVM was doing. A GC log's
   `[12.345s]` decoration is uptime; when the session's recording has been analysed, `hubs_files`
   and `hubs_fetchFile` name the profile and its zero point, so that uptime is `zero point + 12.345s`
   on the JFR timeline and `jvm_gc` on the profile can be read against it.
4. A fetched heap dump is a profile's input, not a text: pass its path to `recordings_analyzeFile`
   and hand off to **analyze-heap**.

The path is where the file stays: beside the profile (`profiles/<id>/artifacts/`) when the
session is analysed, under `artifacts/<hub>/<project>/<session>/` otherwise. A file fetched
*before* the session was analysed is moved beside the profile by the next `hubs_fetchFile` rather
than pulled down twice, so the path in an older answer can be stale — take the current one from
`hubs_files`, which prints it in the `local` column once it is there, and for an artifact
`hubs_download` brought along with the recording.

Cite a log the way `report` asks: the file name from `hubs_files` and the line number of what you
quote, so the reader can open the same line.

## 5. Analyse

You now have a `profileId` and the hub is out of the picture.

- A JFR recording → the **analyze-jfr** skill: `profiles_features` first to see what the profile
  can answer, then the family that matches the question.
- A session whose recording is a heap dump → the **analyze-heap** skill instead.

A session often carries both a JFR recording and a heap dump; the dump arrives as an artifact
alongside the recording. `profiles_features` on the resulting profile says which of the two you
actually have.

## 6. Clean up

The hub is the copy of record; Microscope holds what is being read. A window that has answered its
question, and a partial look once the real window is downloaded, are removed with
`recordings_delete(recordingId)` — recording, profile and files together, nothing on the hub
touched. Say which one you are deleting; a profile the user opened in the browser stops working
the moment it goes. Leave a profile the user asked to keep, or one another skill is still reading.

## What this skill will not do

**It does not delete anything on a hub.** `recordings_delete` removes a recording from *this*
Jeffrey; no tool here removes a session, a file or a project from a hub. Data retention on the hub
is the hub's business.

**It does not push.** Recordings travel from a hub into this Jeffrey, never the other way.

**It does not reach a hub that is not already configured.** Hubs are declared in Microscope's
configuration or added through its UI. If the one the user wants is not in `hubs_list`, say so —
adding it is an operator's decision, not something to work around.

## When a transfer outlasts the call

A large session takes longer to pull than a client waits for a tool call. `hubs_download` then
returns a status saying the transfer continues, and an `operationId`, rather than a `recordingId`.
**Poll `operations_status(operationId)`** — it reports the attempt's progress, its result once the
transfer lands, and the exact retry instructions when it failed — rather than calling
`hubs_download` again; a second call answers from the local store and is harmless, but it is the
poll that says what is happening. `operations_cancel(operationId)` asks a transfer to stop, best
effort, and rolls back nothing already written. A failed transfer is remembered for an hour, and
during that hour `hubs_download` reports the failure rather than starting again unless it is called
with `retry=true`.

The same applies one step later: `recordings_analyzeRecording` on a large recording returns a status
of `running` with its own `operationId`, and `operations_status` says when the profile is ready.
`recordings_status(recordingId)` answers the same question for a `recordingId` you already hold,
which after `hubs_download` you do. Operation ids live in Jeffrey's memory for an hour after the
work completes and are forgotten on restart.
