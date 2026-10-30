---
name: analyze-exceptions
description: "Analyses the exceptions an application threw in a Jeffrey profile and traces each one to the code in the current repository that caused it — separates the application's throws (libraries included, so timeouts and connection resets stay) from the JVM's own class-loading and linking noise, finds where the application's code entered each throw, reads that source and recommends the smallest fix. Use whenever the user asks to analyse the exceptions or errors of an application, service or recording, why an application throws so much, what an exception storm or a burst of timeouts in a profile is caused by, whether exceptions are used as control flow, or names an exception class seen in a JFR recording or Microscope's Exceptions page."
allowed-tools: mcp__plugin_microscope_jeffrey__* mcp__jeffrey__* Read Grep Glob Bash(git rev-parse *)
argument-hint: "[application | profile-id | recording-file] [exception-class]"
---

# From a profile's exceptions to the code that throws them

Exceptions are a cost and a symptom at once. Constructing one walks the whole stack, so a type
thrown in a loop shows up nowhere in a flamegraph as itself — and a burst of timeouts is a
dependency failing, which no CPU profile names. Jeffrey reads the throw events; this checkout holds
the code that threw them. This skill joins the two: it takes the application's throws from the
profile, finds the line of this repository that led to each one, reads it, and recommends what to
change — then, only when asked, changes it.

Requested scope: `$ARGUMENTS` — substituted by Claude Code; where a client does not substitute it,
take the same scope from the request itself. An application name, a profile id or a recording file,
then optionally one exception class to focus on. Empty means the profile the conversation is about
(or the most recently modified one in `profiles_list`).

Tool names below omit the prefix your client puts in front of them — `mcp__plugin_microscope_jeffrey__` in Claude Code with the `microscope` plugin, `mcp__jeffrey__` in Codex or wherever the server is registered by hand as `jeffrey`, `mcp_jeffrey_` in Gemini CLI; the rest of the name is exact camelCase.

Two phases with a stop between them — **recommend**, then **change**. Track progress:

```
- [ ] 1. Profile resolved, commit compared with HEAD
- [ ] 2. The application's own packages named from this checkout
- [ ] 3. Throws recorded? Application vs JVM-internal split read
- [ ] 4. Each site's own frame read in source
- [ ] 5. Every site classified from code that was actually read
- [ ] 6. Recommendation written — STOP
- [ ] 7. Accepted edits made, built, tested, re-profiled where possible
```

## 1. Resolve the profile

**The user named an application** ("analyze exceptions of orders-service") — match it against the
`name` and project of the rows `profiles_list` returns, following `nextCursor` while `hasMore` before
deciding it is not there. Several matches → take the newest and name the others in one line; the
user can pick another. No local match, and the name sounds like a deployed service or an
environment ("production", "staging") → switch to the **analyze-remote** skill, which finds its
recording session on the remote and comes back here with a `profileId`.

**The user named a file** → `recordings_analyzeFile` with its absolute path (the `analyze-jfr` skill
covers polling a large import).

Then `profiles_get` and read `recordingCommit`:

- equal to `git rev-parse HEAD` — say so in one line and continue;
- different — say so **before anything else**, name both commits, and ask whether to check the
  recording's commit out. Do not switch branches unasked: a profile of another commit describes code
  that may have moved, and mapping its frames onto the wrong tree produces confident nonsense;
- `null` — say the commit is unknown, not that it matched.

A heap dump has no throw events: say so and stop, or hand over to `analyze-heap` if that was the
question.

## 2. Name the application's own packages

Jeffrey does not know which packages are yours; this checkout does. Read them off the source tree —
`Glob` for `**/src/main/java/**/*.java` and `**/src/main/kotlin/**/*.kt`, and take the shortest
package roots shared by the files (usually two or three segments: `com.acme.orders.`). Fall back to
the `groupId` in `pom.xml` or the `group` in `build.gradle(.kts)`. Keep the trailing dot, so
`com.acme.` does not also match `com.acmecorp.`.

Name them in one line — "matching frames against `com.acme.orders.`, `com.acme.shared.`" — so the
user can correct you before anything depends on it. In a multi-module repository where the user
asked about one service, use that module's packages plus any shared ones it depends on.

## 3. Read the split — `jvm_exceptions`

One call. Read, in this order:

- `exceptionThrowsRecorded` — **false means the recording cannot name the throws**, whatever
  `totalThrowables` says. Stop here: say how many throwables were counted, that their types were not
  recorded, and give the remedy — enable `jdk.JavaExceptionThrow`, which the JDK's own `default`
  and `profile` settings both leave off: `-XX:StartFlightRecording=settings=profile,jdk.JavaExceptionThrow#enabled=true,…`
  (JDK 17 or later), or a custom `.jfc` with the event on (async-profiler: `jfrsync=<that .jfc>`).
  Do not guess types from the total.
- `applicationThrows` against `jvmInternalThrows`. The JVM-internal ones are the JDK resolving and
  linking — `ClassNotFoundException` while a library probes the classpath, `NoSuchMethodException`
  from reflection, `java.lang.invoke` linking a call site, a class loader failing to find a resource
  in a jar. They are thrown and caught inside the JDK and are **not** this application's to fix:
  name the count in one line and move on, unless the user asked about one of them by name.
- `topApplicationSites` — the application's throw sites (libraries included) ranked by count, each
  with its `throwingFrame` and commonest messages. `errors` and any site with `error: true` deserve
  a look even at a count of one.

Put the three or four heaviest sites to the user with their counts as a short table, plus every
`Error` site. If the user named a class, keep that one.

## 4. Find the code — `jvm_exceptionSites`

Call it with `framePrefixes` set to the packages from step 2 (and `thrownClass` when the user named
one). Each site comes back with:

- `throwingFrame` — the frame that contained the `throw`;
- `ownFrame` and `ownFrameDepth` — the topmost frame of **yours** on that stack. Depth 0 means your
  code threw; a larger depth means a library or the JDK threw while your code was calling it, and
  `ownFrame` is the line that made that call;
- `stack` — the frames from the throw down, to read what happened in between;
- `count`, `threadCount`, `messages`.

For every site with an `ownFrame`, open the file at that line — `Grep` for the class
(`class OrderService`, accounting for nested `$Inner` names), then `Read` around the line. Read enough
of the method to see what happens to the exception: is it caught right there, rethrown, logged,
retried, turned into a status code? When the reader has IntelliJ open with the Jeffrey plugin,
`ide_resolve` gives the exact file and line for anything a grep gets wrong (nested classes, Kotlin
facades, overloads), and `ide_source` reads library code that is not in this repository.

A site with no `ownFrame` threw entirely inside the JDK or a library. Read its `stack`: the deepest
frames name what started the work (a scheduler, a client's I/O thread, a framework's startup). Say
so, rather than inventing a line of this repository to blame. Follow `nextCursor` only while the
next page could still hold sites the user cares about.

Hold to these rules:

- **Never name a file, method or line you have not read.**
- **Tie every claim to the profile**: the site's `count`, its share of `applicationThrows`, its
  threads and its messages.
- The recording samples what it captured; say "thrown N times in the recording", not "per request".

## 5. Classify each site from the code

Each site gets exactly one of these, decided from the source you read:

| Kind | What it looks like | Typical fix |
|---|---|---|
| **Control flow** | A high count, one or two messages, caught right away: `NumberFormatException` from parsing input, a "not found" exception turned into `Optional.empty()`, a loop broken by a throw | Check first (`tryParse`, `containsKey`, a validation method), or return a value instead of throwing |
| **Swallowed** | Caught and ignored, or logged and continued, at a count that says it happens on every call | Fix the cause, or stop constructing it — a pre-allocated or stackless exception if the type is your own |
| **Retry / timeout storm** | A library throw (`SocketTimeoutException`, `ConnectException`, a pool timeout) with your client call as `ownFrame`, many threads, bursts | A configuration finding (timeout, pool size, retry policy with backoff) or a dependency's problem — the code change is at the call site: fewer calls, a circuit breaker, a cache |
| **Real failure** | An `Error`, or a rare class with varied messages that escapes to the caller | Fix the bug the message describes; `traces_operations` sorted by `ERRORS` says which requests it failed, when the profile has traces |
| **JVM noise that leaked** | A JVM-internal throw the user asked about | Explain what the JDK was doing; usually nothing to change, sometimes a missing optional dependency or a misconfigured classpath |

When the profile has traces, `traces_operations` with `sort: ERRORS` ties throws to the requests
they belong to — worth one call for a real failure or a timeout storm. For a control-flow site with a
very high count, the CPU it costs shows up as `fillInStackTrace` in the on-CPU flamegraph; the
`jvm_exceptions` follow-up carries that export, but offer it rather than running it — it is a HEAVY
document.

## 6. Recommend, then stop

Write it in the `report` skill's shape:

- **Summary** — the split (application vs JVM-internal throws), what dominates, and which sites
  you could not map to this repository. Say what the recording could not answer as **not assessed**.
- **Code findings.** One **`### <file>: <method>`** section per site, heaviest first: the exception
  and its count, the kind from step 5, what the code does with it (from what you read), and the
  smallest behaviour-preserving change in prose. No diffs yet.
- **Configuration findings**, kept separate: a timeout, a pool, a retry policy — the setting, its
  value, the evidence, what to change it to. Never present one as a code edit.
- **Not located** — sites with no `ownFrame`, or whose code is not at this commit.
- **Confidence per finding**, and what caps it (sampled throws, a commit mismatch, a site whose
  stack was not recorded).

Put each site's Microscope page beside its name — the answer's `uiLink` opens the Exceptions page;
never build a URL by hand.

Then ask which findings to apply, and **wait**. In Claude Code use `AskUserQuestion`, one
multi-select option per finding when there are at most four; with more, list them numbered.

## 7. Change and verify

For each accepted finding: make the smallest edit, run the project's build and tests, and — when the
workload can be reproduced — re-record, `recordings_analyzeFile` the new recording and call
`jvm_exceptionSites` with the same `framePrefixes` and `thrownClass`. Report the site's count before
and after. Never claim a reduction you did not measure. A configuration finding takes effect on the
next run of the application, not the next test: say what to watch for instead of reporting it fixed.

Keep the Covered / Open leads / Not assessed ledger as the other skills do, so the `report` skill
can build its findings from it.
