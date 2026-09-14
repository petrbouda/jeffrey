---
name: analyze-hub
description: Finds and analyses JVM recordings that live on a Jeffrey Hub rather than on this machine — the JFR recordings and heap dumps a deployed application produced. Use whenever the user asks about what an environment recorded rather than about a file they have: production, staging, a named service or pod, "the last hour", "since the deploy", "what the hub has", "why was prod slow this morning". It locates the session, pulls it into Jeffrey and hands off to analyze-jfr or analyze-heap. For a recording file already on this machine, analyze-jfr applies directly.
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
hubs_sessions(withinLastMinutes=60)      → rows, newest first, each with a session_ref
hubs_queryEvents / hubs_eventActivity    → a look at the session where it lies, optional
hubs_download(sessionRef="h1…")          → recordingId, or a running operationId
recordings_analyzeRecording(recordingId) → profileId, or a running operationId
operations_status(operationId)           → where either of the last two has got to
… then analyze-jfr (or analyze-heap for a dump)
```

Three calls on the main path, and the third is a tool you already know; the look in between is
what saves a download that would have told you nothing, and `operations_status` is how the two long
ones are followed. There is no hub-specific analysis: once a session is downloaded it is a normal
Jeffrey recording.

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
- **`size`** and **`duration`** — what a download will cost, and how much data is behind it.
- **`status`** — `ACTIVE` is still recording. That is fine to download; you get the chunks that
  have been rolled so far, not a broken file.
- **`session_ref`** — the only thing `hubs_download` takes. Copy it exactly.

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
- the session you would pick is large enough that pulling it is a real cost;
- the user named an environment that matches more than one hub.

Ask with the facts in front of them — *"production has three sessions in the last hour: checkout
(18m, 240MB), search (54m, 1.1GB), api (3m, 12MB). Which?"* — not with an abstract question. Never
ask which hub, then which workspace, then which project. Nobody knows their workspace ids, and each
step buys nothing that the table did not already show.

## 3. Look before you download

A session is a size and a duration until something reads it, and a gigabyte pulled in to learn that
the interesting minute is not in it is the expensive way to find out. Two tools read a session
**where it lies**, on the hub, without transferring or analysing it:

- `hubs_queryEvents(sessionRef, eventTypes)` returns a bounded sample of matching events in replay
  order — the first hundred by default; `limit` takes any positive integer, or `0` for no row cap,
  and either way a 15-second deadline and a byte budget (`maxBytes`, 64 KB by default, 100,000 at
  most) still bound the answer. It is a sample, not a ranking: "did this session record
  `jdk.ObjectAllocationSample` at all, and what do a few look like" is its question, not "which
  was the slowest". Narrow `eventTypes` and the time window when a limit cuts it short.
- `hubs_eventActivity(sessionRef, startTime, endTime)` starts a scan on the hub that counts events
  by time bucket and returns a `scanId`; `hubs_activityStatus(sessionRef, scanId)` reads it, ranked
  by event count, by distinct types or in time order, with counts that are lower bounds until
  `complete` is true. That is how "when was it busy" is answered before anything is downloaded —
  and, when the session is long, how the window worth analysing is chosen. The scan is work on the
  hub: it claims one of a handful of retained slots and runs a reader there, and
  `hubs_activityCancel(sessionRef, scanId)` releases one that is no longer wanted. The `scanId` is
  also an `operationId`, so `operations_status` reads the same scan.

Neither changes a recording, and both are optional: a three-minute session is cheaper to pull than
to ask about. The look is for the large one, or the one you would otherwise have to ask the user
about.

## 4. Pull it in

`hubs_download(sessionRef)` merges the session's finished recording files into one local recording
and brings its artifacts — heap dumps, JVM and application logs — with it. It returns a
`recordingId`.

It does **not** build the profile; `recordings_analyzeRecording(recordingId)` does that and returns
the `profileId` every analysis tool takes. The two are separate on purpose: a large session is a
long transfer and then a long analysis, and one call doing both is the shape that hits a tool
timeout with nothing to show for it.

Both calls answer inside the call for a small session and hand back an `operationId` for a large
one — see the last section for how that is followed. Say what you are doing before starting a big
one.

Downloading the same session twice is wasteful and never necessary — `hubs_download` returns the
recording it already has rather than fetching it again, but you should have read the `local` column
in step 1 instead of relying on that.

## 5. Analyse

You now have a `profileId` and the hub is out of the picture.

- A JFR recording → the **analyze-jfr** skill: `profiles_features` first to see what the profile
  can answer, then the family that matches the question.
- A session whose recording is a heap dump → the **analyze-heap** skill instead.

A session often carries both a JFR recording and a heap dump; the dump arrives as an artifact
alongside the recording. `profiles_features` on the resulting profile says which of the two you
actually have.

## What this skill will not do

**It does not delete anything on a hub.** No tool here removes a session, a file or a project. Data
retention on the hub is the hub's business.

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
