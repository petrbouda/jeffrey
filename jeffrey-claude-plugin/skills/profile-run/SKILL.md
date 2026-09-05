---
name: profile-run
description: Runs this project under a profiler and analyses what comes out — a benchmark, a test, a load script or the application itself, recorded with JFR or async-profiler, imported into a running Jeffrey Microscope and handed to analyze-jfr. Use whenever the user wants to profile something that is not recorded yet: "profile this benchmark", "record a JFR while the tests run", "why is this slow" with no recording in hand, or when a hotspot needs measuring rather than guessing.
allowed-tools: mcp__plugin_microscope_jeffrey__* mcp__jeffrey__*
argument-hint: "[what to run] [cpu|wall|alloc|lock]"
---

# Recording a profile, then reading it

Every other skill here starts from a recording that already exists. This one starts from a command
that does not have a recording yet, and ends where `analyze-jfr` begins.

Requested scope: `$ARGUMENTS` — what to run, and optionally what to look for. Empty means ask, or
infer from the repository if a benchmark or a test is the obvious thing to profile.

Tool names below omit the prefix your client puts in front of them
(`mcp__plugin_microscope_jeffrey__` for the Claude Code plugin, `mcp__jeffrey__` in Codex).

## 1. Decide what to run, and say so before running it

Profiling costs minutes and, for a load test, may touch things outside this machine. Name the
command and the recording it will produce, then run it.

Look for the target in this order: a benchmark the user named; a JMH harness (`jmh-tests`,
`*Benchmark.java`); the test task; an entry point with a `main`. If none is obvious, ask — profiling
the wrong workload produces a real recording of an irrelevant thing, which is worse than no recording
because it looks like an answer.

## 2. Record

**JFR is the default**, because it needs nothing installed and every `jvm_` tool reads it:

```
-XX:StartFlightRecording=settings=profile,filename=/absolute/path/run.jfr,dumponexit=true
```

`settings=profile` rather than `default`: the default configuration omits allocation sampling and
most of what makes a profile worth reading. Pass the flag through whatever the build uses — Maven's
`-DargLine=`, Gradle's `jvmArgs`, JMH's `-jvmArgsAppend`, or `JAVA_TOOL_OPTIONS` for an application
started by a script.

Add these when the question calls for them:

- **Allocation** — already in `settings=profile`. `jdk.ObjectAllocationSample` is what
  `flamegraph_export` graphs with `useWeight: true`.
- **Wall-clock, or off-CPU time** — JFR does not sample it. That needs async-profiler:
  `-agentpath:/path/libasyncProfiler.so=start,event=wall,jfr,file=/absolute/path/run.jfr`.
- **Locks** — `jdk.JavaMonitorEnter` is in `settings=profile`, but threshold-gated at 20ms. Lower it
  with a custom `.jfc` when contention is short and frequent.
- **A JMH run** — profile the benchmark, not the harness: `-prof jfr` gives one recording per
  benchmark rather than one for the whole fork.

**Record long enough to be worth reading.** A run of a few seconds produces a few hundred samples,
and a flamegraph of a few hundred samples is noise with a shape. Aim for a minute of steady state,
after warm-up. For a JIT-sensitive benchmark, discard the first iterations rather than profiling them
— they measure the interpreter.

The file has to land where **Jeffrey** can open it, not merely where you can: same machine, absolute
path. If Jeffrey runs in a container, write into a mounted directory.

## 3. Import and analyse

```
recordings_analyzeFile(path="/absolute/path/run.jfr")
```

A small recording comes back with a `profileId`. A large one comes back with a status of `running`;
poll `recordings_status` with the `recordingId` rather than importing it again, which would build a
second profile of the same file.

Then hand off: the **analyze-jfr** skill has the families, the entry sequence and the flamegraph
choice per question. Start with `profiles_summary` — it reports what the recording actually captured,
which is the first thing to check after a run you configured yourself. An empty `jdk.ObjectAllocationSample`
means the settings did not take, and that is a fact about step 2 rather than about the application.

## 4. Re-profiling after a change

The point of recording rather than reading an old profile is that you can do it twice. After a change,
record again the same way and use **compare-jfr**: it weighs the two against each other and, first of
all, says whether they are comparable. Two runs of different lengths, or one warm and one cold, are
not — and a comparison that ignores that reports a regression that is really a difference in method.

Keep the profiles named so the pair is obvious later: pass `name` to `recordings_analyzeFile`.

## What this skill will not do

- **It will not profile production.** Attaching a profiler to something a user did not name as theirs
  to profile is not this skill's decision. Recording a deployed application is a Jeffrey Hub question;
  the **analyze-hub** skill fetches what production already recorded.
- **It will not choose the workload silently.** A benchmark that does not represent the problem
  produces a profile that answers the wrong question convincingly.

Related skills: `analyze-jfr` for reading what this produced, `compare-jfr` for before and after,
`advise-jfr` to turn a hotspot into a change in this repository.
