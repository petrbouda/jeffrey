---
name: optimize-from-profile
description: Turn a Jeffrey profile into actual code changes in the repository you are working in — find the hotspots, locate them in the source, make the minimal change, build it, and say what it should buy. Use when the user asks to make something faster, cut allocation, reduce lock contention, or act on a profile, recording or flamegraph they already have in Jeffrey.
allowed-tools: mcp__plugin_microscope_jeffrey__* mcp__jeffrey__* Read Edit Write Grep Glob Bash
---

# Acting on a Jeffrey profile

This skill is for the case where the profile and the source are in the same place: Jeffrey holds the
measurement, you are sitting in the repository that produced it, and the deliverable is a change
rather than an explanation.

Jeffrey supplies measurement. Everything else — finding the code, judging whether the change is
right, making it, building it — is yours. The tools below are the only ones you need from it; use
`analyze-profile` instead when the user wants to understand a profile without changing anything.

## 1. Confirm you are in the right repository — before anything else

Call **`profiles_buildInfo`** and compare what it returns against the checkout:

- `recordingCommit` against `git rev-parse HEAD`
- `jvm.javaArguments` and `jvm.jvmArguments` against what this repository builds — the main class, the
  `-jar` name, the classpath entries, the `-D` properties
- `recordingTags` for anything that names the service or the image

Then decide, and say which case you are in:

| What you found | What to do |
|---|---|
| Commit matches, or is an ancestor/descendant of HEAD | Go on |
| Commit disagrees with HEAD | Say so, name both commits, ask before editing — the profile may describe code that is already gone |
| No commit, but the main class or jar plainly belongs to this repository | Go on, and say the identification was by command line, not by commit |
| Nothing identifies the build | Stop. Report what you know and ask the user to confirm the repository |

Never skip this because the profile name looks familiar. Everything you are about to claim assumes
this repository is the one that ran.

Wanting a second signal when there is no commit tag: the frames themselves are one. Through
`jfr_executeQuery`:

```sql
SELECT class_name, count(*) FROM frames
WHERE class_name IS NOT NULL GROUP BY class_name ORDER BY 2 DESC LIMIT 40
```

The packages in that list should be packages this repository contains.

## 2. Take the right graph

`flamegraph_panels` lists the event types this recording actually carries. Match the graph to what
the user asked for:

| The question | Event type | Also pass |
|---|---|---|
| Where is on-CPU time going | `jdk.ExecutionSample` | — |
| Where is wall-clock time going, waiting included | `jdk.WallClockSample` | — |
| What is allocating | `jdk.ObjectAllocationSample` (older recordings: `jdk.ObjectAllocationInNewTLAB`, `jdk.ObjectAllocationOutsideTLAB`) | `useWeight: true` — rank by bytes, not by call count |
| What is waiting on locks | `jdk.JavaMonitorEnter` (also `jdk.JavaMonitorWait`, `jdk.ThreadPark`) | `useWeight: true` — weight is nanoseconds blocked |

"It's slow" usually means wall-clock first, then CPU; "it's GC-heavy" means allocation; "it stalls
under load" means blocking.

Then `flamegraph_export` over the **whole recording** — no thread scoping, no search, no time window
unless the user asked about one specific interval. Those filters each turn the question into a
narrower one. Read the export's preamble before the tree: it defines what `total`, `self`, `+pruned`
and the tier tags mean in Jeffrey's accounting, which differs in places from flamegraph conventions
elsewhere.

`thresholdPct` decides how much survives pruning. Start where the default leaves you, lower it to
chase one path deeper. A frame that is absent was below the threshold for its parent — absence is not
zero.

## 3. Land the frames on source

This is where the work is, and where it goes wrong quietly. The tree gives you method signatures, not
file paths.

- Search for the declaring type first, then the method. `Grep` for the class name; confirm the
  overload by its parameters, not by its name alone.
- `[INL]` means the frame was inlined into its caller at runtime — the source still has both methods,
  but there is no separate function whose cost you can remove.
- A `$$Lambda` frame belongs to the method that declares the lambda. Find that method, not a file
  named after the synthetic class.
- `[SYNTHETIC]` frames are markers Jeffrey inserted — thread names, allocated-object placeholders,
  collapsed subtrees. They are not call frames and are not in the source.
- `self` far below `total` means the frame is orchestration. The cost is in what it calls, and the fix
  is usually "call it less often", one level up.

**Only change code this repository contains.** When the hot frame is in the JDK, a library or a
driver, the change is at your call site: call it less, call it differently, batch it, cache it, or
change the dependency's version or configuration. Do not propose edits to code that lives in somebody
else's repository.

Read the file before you say anything about it. Never infer a path, a signature or a line number from
a frame label — and where you cannot find the code behind a hotspot, say so. An admitted gap is worth
more than a confident guess.

## 4. Change it, then build it

Keep each edit minimal and behaviour-preserving, and tie it to a specific frame and its measured
share. A few changes that matter beat many that might.

Then build and run the tests this repository actually uses — check `CLAUDE.md`, the README or the CI
workflow for the real commands. A change that does not compile is not a recommendation. If something
turns out to need a redesign rather than an edit, say that instead of half-doing it.

## 5. Report, and prove it if you can

Say what you changed, which frame and percentage each change addresses, what you expect it to buy, and
what you deliberately left alone and why. Quote the numbers from the tree — not adjectives.

To prove it rather than assert it: record the workload again, import the new recording with
**`recordings_analyzeFile`** (absolute path, on the machine Jeffrey runs on), and compare the same
event type's totals and hot path against the original profile. A change that does not move the number
is worth reverting.

## Cross-checks worth making

- **Latency, not CPU** → `traces_operations` sorted by `TOTAL_TIME`, then `traces_operationExport` and
  `traces_spanFlamegraphExport` for the frames inside one slow span. An endpoint maps onto a controller
  method far more directly than a frame does.
- **Memory that grows rather than churns** → the `heap_` family, `getLeakSuspects` and
  `getPathToGCRoot`, not the allocation profile.
- **Something no graph answers** → `jfr_listEventTypes` and `jfr_executeQuery` against the profile's
  DuckDB.

## When something is missing

- `profiles_buildInfo` returns no commit and no tags → the recording was made without build metadata.
  Tag future recordings with `git.commit` so this check has something to work with.
- `flamegraph_panels` comes back empty → the recording carries no sample events; it may be a heap dump.
  Check `profiles_features`.
- Jeffrey has a recommendation of its own for this profile → that is the in-app Advisor, which reasons
  over the same tree without being able to compile anything. Treat it as a second opinion, not as a
  finding; verify it in the source like your own.
