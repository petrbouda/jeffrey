---
name: code-insight
description: Answers questions about the code in the current repository from a Jeffrey profile, asked by the names in the source - whether a method or class is hot, who calls it at runtime, which of its lines the time goes to, what it calls into, which exceptions are thrown under it, which requests run it, which of the repository's own methods a profile's cost is charged to, which lines issue a SQL statement or call and how often one request does (N+1), and what the application did at runtime - its operations, statements, attributes and own events - tied back to the code. Use whenever the user asks about the runtime behaviour of code they are reading or editing - "is this method hot", "who calls this in production", "what does this class cost", "which endpoint runs this", "where does our code spend its time", "which line issues this query", "is there an N+1", "what does this service actually do at runtime" - and a profile of that code exists or can be recorded.
allowed-tools: mcp__plugin_microscope_jeffrey__* mcp__jeffrey__* Read Grep Glob Bash(git rev-parse *) Bash(git status *)
argument-hint: "[Class#method | Class | file path] [profile-id]"
---

# What a profile measured of the code in front of you

The other skills start from a profile and work towards the code. This one starts from the code — a
method the user has open, a class they are about to change, a file in this checkout — and asks the
profile about it by the names in the source. Jeffrey answers with the same names, so nothing has to
be found in a flamegraph first.

Requested scope: `$ARGUMENTS` — substituted by Claude Code; where a client does not substitute it,
take the same scope from the request itself. A method (`com.acme.Checkout#checkout`), a class, or a
file path, then optionally a profile id. Empty means the code the conversation is about.

Tool names below omit the prefix your client puts in front of them — `mcp__plugin_microscope_jeffrey__` in Claude Code with the `microscope` plugin, `mcp__jeffrey__` in Codex or wherever the server is registered by hand as `jeffrey`, `mcp_jeffrey_` in Gemini CLI; the rest of the name is exact camelCase.

Track progress:

```
- [ ] 1. Profile chosen, its commit compared with HEAD
- [ ] 2. Own packages named
- [ ] 3. The code named as the JVM runs it
- [ ] 4. Asked: code_method for one method or class, code_hotspots for the repository,
        code_callSites for a statement or call, code_runtimeSurface for what ran
- [ ] 5. Every figure tied to source that was actually read
```

## 1. Choose the profile and check its commit

`profiles_list`, newest first, unless the user named one; nothing there → offer **profile-run** to
record this project, or `recordings_analyzeFile` for a recording file they have. Then `profiles_get`
and read `recordingCommit` against `git rev-parse HEAD`:

- equal — say so in one line; with `recordingDirty` true, add that the build had uncommitted changes,
  so a line may not be where the checkout has it now;
- different — say so first, with both commits: the code may have moved, been renamed or deleted
  since, and a figure mapped onto the wrong tree is confident nonsense. Ask before checking anything
  out;
- `null` — the commit is unknown, not matched. `recordings_analyzeFile` with `commit` (and `dirty`)
  fixes that for a recording you import; `-Dgit.commit=<sha>` on the profiled JVM fixes it for one
  made elsewhere (`recordingCommitSource` then reads `SYSTEM_PROPERTY`).

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

## 2. Name the code as the JVM runs it

From a file path: the `package` declaration and the class name; a nested class in source spelling
(`Outer.Inner`) is fine. From a method: the name as written — overloads share it, and the profile
cannot tell them apart. A Kotlin top-level function: the facade class `FileKt`. For a whole class,
leave `methodName` out; nested and anonymous classes come with it.

## 3. Ask

**One method or class** — `code_method` with `className`, `methodName` and your `framePrefixes`.
It reads every sample kind the profile recorded (or the `eventType` you name) and answers, per kind:

- `inclusive` and `self` with their share of the kind — how much ran while it was on the stack, and
  how much in it;
- `callers` and `callees` — who called it and what it called into, with shares of its inclusive
  figure; `ownCallers` — for a library method, which of **your** methods reach it;
- `lines` — the lines of the method the samples landed on. Open the file at each one before saying
  anything about it;
- `exceptions` — what was thrown with it on the stack, over the whole recording;
- `operations` — the busiest traced requests whose spans ran it, with the share of each request's
  samples and its p95, each with its `uiLink`.

`status: NOT_SEEN` is a measurement: no sample ran it in this profile. Before saying "it is not hot",
read `nearestFrames` — a rename, an overload in another class or a package move shows there — and
the commit check from step 1.

**The repository as a whole** — "where does our code spend its time" — `code_hotspots` per kind of
sample (`jdk.ExecutionSample` or `jdk.CPUTimeSample` for CPU, `profiler.WallClockSample` for wall
clock, `jdk.ObjectAllocationSample` for allocation, `jdk.JavaMonitorEnter` for locks;
`flamegraph_list` names what this profile recorded) with your `framePrefixes`. Each sample is
charged to the deepest frame of your code on its stack, so the rows add up to `chargedToOwnCode` and
rank the methods you can change. `delegatedTo` says where a method's charged cost went: a
method whose time is 90 % `String#format` is a formatting problem at that call, not a CPU problem
in the JDK. `ownSharePct` far below 100 means most of the cost ran no code of yours at all — the
JVM, GC or a library thread — and `jvm_gc` or a `flamegraph_export` of the same kind says what.

For a burst rather than the whole run, `timeline_hotWindows` gives the window and both tools take
`startEpochMs`/`endEpochMs`.

**What ran, against the code** — "which endpoints does this service really serve", "where does this
request go in our code", "which business attributes do we record" — `code_runtimeSurface` with your
`framePrefixes`. It lists the busiest traced operations with count, errors and p95 and, from the
samples inside their spans, the methods of yours they entered through (`entryPoints` — the handler)
and spent most in (`topMethods`); the JDBC statements with the paths of yours that issue them; the
trace attribute keys, the developer's own first; the notification types; the application's own JFR
event types. Reconcile it with the source: a route the code declares that no operation shows was not
exercised in this recording, an operation with no attribute of its own is one the code never
describes, a custom event recorded without stacks cannot name its caller. Read `entryBasis` before
naming a handler: `EVENT_STACK` is recorded (the stacks of the statements the requests issued),
`SAMPLED_SPANS` is inferred from where samples landed — read `ownSamples`, two samples place an
operation loosely — and say which. When the question is what one request runs step by step, its steps
and their order come from the profile, not from reading the handler: `traces_operationExport` lists
the operation's spans, each with its share of the operation's time. Name each step as the profile records it
(the span's name) beside the code that runs it, and mark anything taken from the source alone as such.

**Who issues a statement or a call** — "which line runs this query", "where is the N+1" —
`code_callSites` with the event type (`jeffrey.JdbcQuery` and its siblings; `jdk.SocketRead`,
`jdk.FileRead`, `jdk.JavaMonitorEnter`; your own JFR events), your `framePrefixes`, and `groupBy` or
`filterField`/`filterValue` to split or narrow by a field. Each row's `ownPath` is the frames of yours
from the deepest up — an own database wrapper first, then the repository or service that called it —
with the line of each; open the caller, not only the wrapper. `repeatedPerTrace` marks a statement one
request issues many times by the median: look for the loop around that line. `NO_STACKS` means the
type was recorded without stacks — Jeffrey's HTTP and gRPC exchanges and its spans are — and
`code_runtimeSurface` places those through samples instead.

## 4. Ground every figure in source

- **Never name a line you have not read.** Open the file at each line a figure points at.
- **Say which runtime names a figure counts** when `matchedFrames` holds more than the method
  itself — a lambda's cost belongs to the lambda's body, not to the line that created it.
- **Shares, not just counts**: a method with 200 samples is a different finding in a 3 000-sample
  profile than in a 300 000-sample one.
- **A figure is a sample**, not a measurement of every call: a method at 0.2 % may simply not have
  been caught, and nothing below 1 % is a finding unless the user is chasing that method.

## 5. What next

Offer, without doing them: **optimize-code** to turn a hot method or a repeated statement into a change; **compare-jfr** or
**regression-check** when the question is whether a change made it worse (`compare_methods` with the
methods a diff touched); `traces_operationFlamegraphExport` of the request a method runs in, when its
place among the request's other work matters; and, when the IDE is linked, `ide_resolve` to put the
method on the user's screen.

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
