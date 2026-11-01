# Microscope plugin evals

Behaviour tests for the skills and agents, run with
[`claude plugin eval`](https://code.claude.com/docs/en/plugin-evals). The structural checks
(`build/scripts/check-plugin-references.py`, `validate-agent-plugin.py`, `render-plugin.py --check`)
say the plugin names real tools and is well formed; these say whether a model that loads it does the
right thing with a real Jeffrey's answers: reaches the right skill, calls what the skill says in the
order it says, finds what the recording holds, and writes it up by the `report` skill's rules.

## Running them

```bash
claude plugin eval ./jeffrey-claude-plugin --ablation none --threshold 1 --keep-temp -j 4 --max-cost-usd 20 \
  --json /tmp/microscope-evals.json
python3 build/scripts/summarize-plugin-evals.py /tmp/microscope-evals.json
```

`--ablation none` because a run without the plugin has no Jeffrey to call: the no-plugin arm would
score every case zero and say nothing. With it, the `Skill` graders count toward the score.
`--keep-temp` keeps each run's trace, which the summary reads for its tool-call and token figures.

Each case runs three times by default (`runs` in its `prompt.md`). A full pass costs a few dollars of
model time at list price; `--max-cost-usd` stops it at a ceiling.
The explicit `--threshold 1` requires every scored check to pass, including the recorded-call semantics guard.

## The cases

| Case | The question | What it holds the plugin to |
|---|---|---|
| `open-question-shows-menu` | "What is wrong with this recording?" | `analyze-jfr`, `profiles_summary`, the menu put to the user, no export before a choice |
| `named-area-cpu` | "Where does the CPU go?" | straight to the CPU flamegraph, the dominant frame named, its source and denominator stated |
| `compare-finds-the-regression` | "Did my change make CPU worse?" | `compare_list` before `compare_movements`, `compare_quality` read, the after-run as primary, the regressed method named, a verdict that states its limits |
| `exceptions-to-the-throw-site` | "Why so many exceptions?" | `jvm_exceptions`, `jvm_exceptionSites` with the application's package, the throw site named and classified |
| `heap-dump-what-holds-memory` | "What holds the memory in this .hprof?" | the import, a GC-root path behind the leak claim, one dump read as a state |
| `help-what-is-here` | "What can Jeffrey do, and what is in it?" | `help`, the catalogue listed, nothing analysed unasked |
| `record-exceptions-next-time` | "How do I record the exception types?" | `jdk.JavaExceptionThrow#enabled=true`, and that `settings=profile` alone does not name every throw (off on JDK 21, throttled on 25) |
| `two-areas-delegated` | "CPU and allocation, both" | both exports read, each hotspot named, allocation weighed by bytes — and whether the two are handed to `profile-analyst` |
| `gc-pauses-in-proportion` | "Are GC pauses hurting it?" | `jvm_gc`, the pauses measured against the recording (about 0.4% of it), no tuning from one fired rule |
| `lock-contention-not-wall-clock` | "Which locks hold the threads back?" | `blocking_monitors`, `SessionCache` named, its ~81 s of blocked time read as thread-time, never as a share of the recording |
| `route-why-slow-no-profile-named`, `route-what-to-change`, `route-did-my-change-regress`, `route-record-first` | four prompts more than one skill's description claims | each reaches the one skill meant for it (`analyze-jfr`, `advise-jfr`, `compare-jfr`, `profile-run`) and makes that skill's first call |
| `route-two-revisions-not-profiled` | "Did my last commit make it slower? Nothing is profiled yet" | `regression-check`, not `compare-jfr`: the pair named (HEAD against its parent), both recorded the same way, no differential of the catalogue's unrelated pair |
| `route-write-it-up` | "Write up what I found, for my team" | `report`, each of the user's figures read back from Jeffrey before it is written down (`flamegraph_export`, `blocking_monitors`), confidence stated, a **Not assessed** section |

Two more cases, under [`../evals-live/`](../evals-live/), ask what only SQL answers and run against a
real Jeffrey — see [The SQL cases](#the-sql-cases).

## Where the answers come from

An eval run never starts the plugin's server: every tool is answered by a mock under
[`mocks/jeffrey/`](mocks/jeffrey/), and a tool with no mock is not offered at all. The mocks are a real
Jeffrey's answers, recorded by `build/scripts/record-plugin-eval-mocks.py` from the workload in
[`fixtures/workload/`](fixtures/workload/) — a checkout service in miniature with a CPU hotspot,
allocation, lock contention, exceptions used as control flow and a slow leak, recorded twice
(`baseline`, then `candidate` with a deliberate regression in the pricing path) and once as a heap
dump. `mocks/jeffrey/_tools.json` is that Jeffrey's real `tools/list`, so each mocked tool carries its
real description and input schema.

To re-record after a change to what the tools return — the answers describe the build they were
recorded from — start a Jeffrey with an empty home directory and run:

```bash
java --add-modules jdk.incubator.vector -Djeffrey.microscope.home.dir=/tmp/jeffrey-eval-home \
  -Dserver.port=8586 -jar build/build-microscope/target/microscope.jar &
python3 build/scripts/record-plugin-eval-mocks.py --url http://localhost:8586/api/mcp \
  --work-dir /tmp/jeffrey-eval
```

It refuses a Jeffrey that already holds profiles, and rewrites the profile ids the graders name to
the new recording's. With `--reuse` it records from the files already in `--work-dir` — a copy of
jeffrey-recordings' `plugin-evals/`, checked by checksum — instead of running the workload again, so
the mocks follow a server change while the recording, and every figure the graders name, stay put.

A mock replays what Claude Code hands the model, which for a result with `structuredContent` is that
JSON and not the text content beside it; the recorder writes it so. Recorded from the text, the mocks
once replayed a flamegraph tree the real model never received.

What the mocks cannot do, so the cases stay away from it:

- **State.** A mock answers the same for the same arguments: the heap dump is recorded already
  prepared, and `jvm_autoAnalysis` as not yet computed.
- **Free-text arguments.** A tool whose required argument is a SQL query, a URI or a path other than
  the recorded heap dump has no mock, so `jfr_executeQuery`, `heap_executeQuery`, `heap_oql`,
  `jfr_getProfileInfo`, `http_endpoint` and the like are absent from the runs (85 of the 112 tools
  are mocked). The SQL cases run against a real Jeffrey for that reason.
- **A class name with a `$`.** A fixture is keyed by its arguments as a file name, so a nested class
  (`Checkout$Order`) can key none: `heap_browseClassInstances` for one answers that the name "is not a
  plain file-name segment", which a model reads as Jeffrey refusing the class.
- **The remote and the IDE.** The recording Jeffrey has no remote and no IntelliJ attached.
- **Arguments the recorder did not reach.** A call with arguments nothing in the recording offered
  answers `no such fixture` as a tool error.

## The SQL cases

`jfr-sql` and `heap-sql` are for the questions no dashboard or report shapes, and a question like that
takes a query nobody can write down in advance — so no mock can answer it. Their two cases live in
[`../evals-live/`](../evals-live/) and run with `--mocks off`, against a Jeffrey that holds the very
recordings the mocks were taken from:

| Case | The question | What it holds the plugin to |
|---|---|---|
| `exception-inputs-counted-in-sql` | "A handful of bad inputs thrown over and over, or a different one every time?" | `jfr-sql`; the distinct messages counted in SQL over `events` (never `events_raw`): 100 inputs, all `SPRING-` coupons — `jvm_exceptions` lists only the commonest three |
| `orders-by-coupon-in-sql` | "How many of the audit log's orders carry a `SPRING-` coupon?" | `heap-sql`; the coupons read without the `string` table (the HPROF name pool), the count and its denominator, as the recording holds them (`figures` in `fixtures/recorded-profiles.json`: 1,427 of 5,775 since the 1.0.17 recording) |

```bash
git clone --depth 1 https://github.com/petrbouda/jeffrey-recordings /tmp/jeffrey-recordings
java --add-modules jdk.incubator.vector -Djeffrey.microscope.home.dir=/tmp/jeffrey-live-home \
  -Dserver.port=8585 -jar build/build-microscope/target/microscope.jar &
python3 build/scripts/record-plugin-eval-mocks.py --url http://localhost:8585/api/mcp \
  --work-dir /tmp/jeffrey-recordings/plugin-evals --load
claude plugin eval ./jeffrey-claude-plugin --eval-dir evals-live --mocks off \
  --allow-tools 'mcp__plugin_microscope_jeffrey__*' --ablation none --keep-temp -j 3 \
  --json /tmp/microscope-live-evals.json
```

Port 8585 is the plugin's default endpoint, which the runs connect to. The recording the mocks were
taken from is kept in [jeffrey-recordings](https://github.com/petrbouda/jeffrey-recordings)
(`plugin-evals/`: the two JFR files and the dump gzipped, 8 MB), since the workload is random and no
re-run reproduces it. `--load` unpacks the dump, imports the three files, and refuses them unless their
checksums are the ones in `fixtures/recorded-profiles.json` and they answer the figures the graders
name (`figures`, measured by the recorder's figure queries). A re-recording measures those figures and
checksums anew and rewrites the figures in the graders, as it rewrites the profile ids — and its three
files then replace the ones in `plugin-evals/`, or the live cases cannot be loaded anywhere else.

**Why not a stand-in.** `claude plugin eval` can mock a tool with an agent — the judge model playing
the server from a description — so the first version of these cases did that: each SQL tool was
played from the real answers to a set of recorded queries (52 by the second round, the first round's
unanswered queries added), refusing any it could not answer from them. Replaying every query of the
six runs per case against the real Jeffrey:

| Case | Queries | Answered as Jeffrey does | Same rows, another order | Refused | Wrong |
|---|---|---|---|---|---|
| `exception-inputs-counted-in-sql` | 12 | 7 | 1 (tied counts) | 3 | 1 — a count of 28,638 where Jeffrey answers 0 |
| `orders-by-coupon-in-sql` | 36 | 11 | 3 | 21 | 1 — "Schema with name recurring does not exist" for the skill's own `USING KEY` walk, which DuckDB runs |

A model explores a heap through ids, `information_schema` and multi-hop joins nobody records in
advance, and a stand-in that refuses a correct query fails the plugin for its own reason: two of the
heap runs it failed had sent queries that, against the real Jeffrey, answer 1,446 of 5,825.

Reading the figures:

- **Tokens and calls are the main conversation's and its subagents' calls, but only the main
  conversation's tokens**: a trace counts a delegated agent's tool calls, not what that agent read.
  A case that delegates — the heap case hands its route to `heap-triage` — reads as cheap on tokens.
- **An LLM judge is noisy at three runs**: one dissenting run in three moves a case's score by about
  0.1. Where a rule can be checked deterministically (a frame named, a denominator stated), a
  `regex` or `tool_used` grader is used instead.

## Current replay and grading contract

The optional-input guards and comparison verdict rubric changed after the measurements below.
Those figures remain historical measurements of their original mocks and graders. A future
comparison needs a fresh baseline and candidate evaluated with the **same current instrument**;
this change includes no new paid eval run or new score, token, latency or cost claim.

Fixed mocks address files with required arguments. Claude Code's fixed mock format cannot dispatch
on an optional argument while accepting its omission: missing file-template segments are errors,
and `expect` does not express “missing or this default”. So a fixture answers a call whatever its
optional arguments say. For most of them that is still the right answer: a different size, order,
page or level of detail of the same figures (`limit`, `topN`, `cursor`, `detail`, `thresholdPct`),
or a filter that picks rows the fixture holds anyway (`search` on a list, `thrownClass`). A few change
what the figures *are* — the weighting, the window, the frames left out (`excludeIdle`,
`excludeNonJava`) or credited (`framePrefixes`, `includeJvmInternal`), `threadMode`, `bucketMs`,
`compute`, and `search` on `flamegraph_export`, which adds the matches. A deterministic
`recorded-call-semantics` grader in every mocked case fails the run when one of those differs from
every recorded call with the same required arguments (`mocks/jeffrey/_inputs.json`), a known default
standing for an omitted option: omitting `useWeight` on an allocation flamegraph and setting it to
`true` both pass; `false` fails. It is a **post-call grading failure**, not a conditional responder:
the fixed mock has already answered when the grader rejects the run, so do not read an unsupported
call's fixture as evidence. Unknown required-argument file addresses remain tool errors. The import
responder additionally guards the one recorded dump path with `expect`. The live cases
(`../evals-live/`) run against a real Jeffrey and carry no such grader.

Replayed against the 15 runs of the cases measured on 2026-10-31 (58 Jeffrey calls), the grader
fails none: the first version, which rejected every unrecorded optional argument, failed 9 of 9 runs
that called Jeffrey — `profiles_list` with `search`, the heap tools with `topN` or `limit`, and
`flamegraph_export` with `detail: "SUMMARY"`, which the tools page recommends.

The checked-in input addresses were reconstructed mechanically from fixture names, recorder seeds
and the response headers; answer contents and tool schemas were preserved. The recorder saves
full inputs on future captures, retaining the first response and input together when required keys
collide. It still captures only a bounded subset of calls; it is not a general server simulator.
`recordings_analyzeFile` replays a snapshot of the recorded import (name/force do not simulate a
second import). A slow future import retains its initial operation answer and records the terminal
`operations_status` for that operation, so the model can poll to the same profile. No synthetic
operation answer has been added to the current synchronous-import fixtures.

The comparison uses an `honest-verdict` semantic judge again. “Confidence” or “comparable” alone
cannot establish honest limits; the rubric rejects invented end-to-end slowdowns and unqualified
comparability while accepting a qualified sampled-CPU finding. Offline tests check the rubric's
contract and its explicit false-slowdown counterexample; only a future paid judge run can measure
how consistently a model follows that rubric.

Run the offline contract tests (Python 3.10+ with PyYAML and Node.js) with:

```bash
python3 build/scripts/test-plugin-eval-mocks.py
```

CI runs these along with the plugin validators. The tests exercise generated grader patterns with
Node's JavaScript regex engine, including weighted/default inputs, windows, filters, frame prefixes,
slow import completion and checked-in contract regeneration. They never call Jeffrey or a model.

## Baseline

The figures a restructure of the skills is meant to move — recorded before it, so the change is
measured rather than argued. Medians per case; see the summary script for the definitions.

Recorded 2026-10-31 on the plugin as of PR #373, `claude-sonnet-5-5` (the eval runs' default model),
three runs a case, against the 85 mocked tools:

| Case | Score | Runs | Turns | Tool calls | Jeffrey calls | Tokens in | Tokens out | Cost (USD) | Skills invoked |
|---|---|---|---|---|---|---|---|---|---|
| compare-finds-the-regression | 1.00 | 3 | 10 | 7 | 4 | 195,902 | 2,629 | 0.17 | microscope:compare-jfr |
| exceptions-to-the-throw-site | 0.93 | 3 | 10 | 7 | 4 | 156,532 | 2,484 | 0.16 | microscope:analyze-exceptions |
| heap-dump-what-holds-memory | 0.87 | 3 | 1 | 19 | 17 | 29,180 | 1,259 | 0.3 | microscope:analyze-heap |
| help-what-is-here | 1.00 | 3 | 6 | 3 | 2 | 86,992 | 1,150 | 0.09 | microscope:help |
| named-area-cpu | 1.00 | 3 | 7 | 4 | 3 | 198,241 | 1,678 | 0.18 | microscope:analyze-jfr |
| open-question-shows-menu | 1.00 | 3 | 6 | 3 | 2 | 161,978 | 1,611 | 0.2 | microscope:analyze-jfr |
| record-exceptions-next-time | 1.00 | 3 | 1 | 0 | 0 | 18,809 | 1,069 | 0.07 | — |

Overall score 0.97, 5/7 cases at the threshold; 3.53 USD including the judge; Claude Code 2.1.316. Medians per case.

What it says:

- **Every case reaches the skill it should**, and the open question stops at the menu.
- **`heap-dump-what-holds-memory` is the one failing rule:** `analyze-heap` hands the route to
  `heap-triage` before importing the file the user named, and `heap-triage` — which may not import —
  picks the catalogue's existing heap-dump profile instead. It happened in seven of ten runs across
  the four passes taken while the suite was written, and the answer says so itself ("could not
  confirm that this profile came from …"). The skill's delegation section should import first.
- **The cost of an answer is mostly instructions.** The one case that loads no skill reads ~19k tokens;
  a case that loads `analyze-jfr` or `compare-jfr` reads 160k–200k across its turns — the skill, the tool
  list and the answers so far, read again (mostly from cache) on every turn. The skill's share of that
  is what the restructure is meant to cut.

## Changes measured against it

| Change | Case | Before | After | What moved |
|---|---|---|---|---|
| `analyze-heap` imports a named dump before delegating; `heap-triage` never looks a profile up by name (plugin 1.0.6) | `heap-dump-what-holds-memory` | 0.87 — the named file not imported in 7 of 10 runs | 1.00 — imported in 3 of 3, in the main conversation before the hand-off | `recordings_analyzeFile` → `Agent(heap-triage)` with its `profileId`; tool calls, tokens and cost unchanged (18 calls, 30k tokens in, $0.26) |
| `analyze-jfr` cut to its entry, menu, routing and answer rules (490 → 271 lines); the family tables and per-area detail moved to `references/`; `profile-analyst` given a 4.6 KB handbook and only `report` preloaded, instead of four skills (plugin 1.0.7) | the `analyze-jfr` cases | see below | see below | tokens down on every case that loads the skill, no score lost |

Measured on 2026-10-31, three runs a case, before (the plugin at 1.0.6) and after:

| Case | Score before → after | Tokens in before → after |
|---|---|---|
| `open-question-shows-menu` | 1.00 → 1.00 | 162k → 136k (−16%) |
| `named-area-cpu` | 1.00 → 1.00 | 200k → 186k (−7%) |
| `two-areas-delegated` | 0.86 → 0.81 | 211k → 199k (−6%) |
| `gc-pauses-in-proportion` | 1.00 → 1.00 | 154k → 140k (−9%) |
| `lock-contention-not-wall-clock` | 0.93 → 1.00 | 153k → 129k (−16%) |

What the traces add:

- **The references were never opened** — 0 of 18 runs of the new version read one, including the
  GC and lock cases whose detail moved there. The tool answers and their `followUp` carried what
  the detail used to, which is the case for moving more of it out of the skills.
- **Two areas are not handed to `profile-analyst`** — in 0 of 6 runs, before or after, although
  `analyze-jfr` says to delegate when more than one export is in play. The main conversation reads
  both exports itself. The analyst's handbook is therefore not exercised by any case yet: the 79 KB
  of preloads it replaces were not being paid either. Whether delegation should be a rule the skill
  enforces, or dropped, is the next question this suite should settle.
- `two-areas-delegated`'s dip is one judge vote on an answer that names both hotspots with their
  denominators; `lock-contention-not-wall-clock`'s judge was replaced by two `regex` graders after it
  failed answers its own criterion allowed ("66% of their combined time").
- **Routing among overlapping skills is not a problem.** The four `route-*` cases — "why is it slow"
  with no profile named, "what should I change", "did my change regress", "how do I record it" —
  reached the intended skill in 12 of 12 runs, one skill each, on plugin 1.0.7. Merging skills to
  fix routing would fix nothing these cases can see.

**Delegation enforced** (plugin 1.0.8). `analyze-jfr`'s "delegate when more than one export is in
play" became "two or more exports are never read here": one `profile-analyst` call per export, all
in one message. Measured against 1.0.7 on 2026-10-31, three runs each:

| `two-areas-delegated` | Read in the main conversation (1.0.7) | Delegated (1.0.8) |
|---|---|---|
| Score | 0.81 | 1.00 — every grader, every run |
| Main conversation's tokens in | 199k | 39k (−81%) |
| Analysts' tokens in | 0 | 202k |
| Cost per answer | $0.25 | $0.36 (+44%) |
| Wall time, median | 27 s | 46 s |

Both analysts ran in every run, in parallel, and their handbook carried them to the right answer — the
first case to exercise it. `named-area-cpu`, one export, still reads it in the main conversation
(1.00, 186k). The trade is model time for a main conversation five times smaller, which is what a
follow-up question in the same conversation pays for; these single-question cases do not measure
that second turn.

**The agents carry handbooks, not skills** (plugin 1.0.10). `profile-analyst` no longer preloads
`report` and `heap-triage` no longer preloads `analyze-heap`, `heap-sql` and `report` (38 KB): each
carries what it works by inline — the heap handbook for `heap-triage`, and for both the evidence
rules, one shared fragment (`build/plugin/shared/evidence-rules.md`). Measured on 2026-10-31, three
runs each:

| Case | Score | Agents' tokens in | Cost per answer |
|---|---|---|---|
| `two-areas-delegated` (two `profile-analyst`) | 1.00 → 1.00 | 202k → 159k (−21%) | $0.36 → $0.27 |
| `heap-dump-what-holds-memory` (`heap-triage`) | 1.00 → 1.00 | 123k → 119k (−3%) | $0.26 → $0.25 |

**Where the main conversation's tokens go**, read off the per-request usage in a trace: every request
starts from about 18k tokens — Claude Code's system prompt and the tool list — before the skill or
any answer; the skill adds 5–7k once, and each answer a few thousand. A case's tokens-in total is
mostly that base read again on every request, nearly all of it cache reads. So shortening the
main-conversation skills further moves little; the number of requests an answer takes is the lever,
and that is the tools' shape — how many calls stand between the question and the first answer —
rather than the skills' length.

**An unrecorded flamegraph says so** (server change; plugin 1.0.11). `flamegraph_export` of an event
type the profile did not record answered with an empty tree, which is why the skills and the server's
instructions said to call `flamegraph_list` before every export. It now answers `status:
NOT_RECORDED` naming the recorded types, with the export of the one that answers the same question
as its next call, and the skills, the analyst's handbook and `McpInstructions` drop the list-first
rule. The mocks were re-recorded from that build (a new recording of the same workload — figures
quoted by the graders were made recording-independent). Before and after on that recording, the
before arm with the old skills and the old `flamegraph_export` description, three runs each:

| Case | Score | Jeffrey calls | Tokens in (main / agents) | Cost |
|---|---|---|---|---|
| `named-area-cpu` | 1.00 → 1.00 | 3 → 3 | 181k → 181k | $0.19 → $0.19 |
| `two-areas-delegated` | 0.95 → 1.00 | 6 → 5 | 47k / 216k → 35k / 182k | $0.34 → $0.32 |

The main conversation already went `profiles_list` → `profiles_summary` → `flamegraph_export` before
the change, routing from the summary as `analyze-jfr` says, so its chain is at its minimum: the name
lookup, the summary the route depends on, and the export. The analysts drop the list call. The
baselines above this section were measured on the earlier recording.

**The heap answers name the real leak** (server fix, mocks re-recorded). The dominator tree was
built wrong on any dump past one DuckDB row group: the node array was filled in the order a prepared
`ORDER BY` streamed, which the driver does not keep while `preserve_insertion_order` is off, so the
recorded heap answers — leak suspects, biggest objects, the dominator tree, retained sizes — described
a scrambled graph (a `java.lang.Long` with no references "retaining" thousands of objects). The leak
suspects also listed each link of one dominator chain as a suspect of its own. Measured on 2026-10-31,
three runs:

| Case | Score | What the answer names | Cost per answer |
|---|---|---|---|
| `heap-dump-what-holds-memory` (`heap-triage`) | 1.00 → 1.00 | `Checkout$AuditLog`, 45.5% of the heap, held by a Java frame, through its `ArrayList` and `Object[]` to the `Checkout$Order` elements — one suspect; the old mocks offered two `java.lang.Long` and a `byte[]`, "analyzing" 20.3 MiB of a 10.6 MiB heap | $0.25 → $0.24 |

## Baseline on plugin 1.0.11

Taken again once the restructure above had landed, so later changes are measured against one
recording rather than across re-recordings. Recorded 2026-10-31 on plugin 1.0.11 with the mocks
recorded from the build that fixed the dominator tree, `claude-sonnet-5-5`, three runs a case:

| Case | Score | Runs | Turns | Tool calls | Jeffrey calls | Tokens in | Agents' tokens in | Tokens out | Cost (USD) | Skills invoked |
|---|---|---|---|---|---|---|---|---|---|---|
| compare-finds-the-regression | 0.94 | 3 | 11 | 8 | 4 | 190,121 | 0 | 2,830 | 0.17 | microscope:compare-jfr |
| exceptions-to-the-throw-site | 1.00 | 3 | 10 | 7 | 4 | 123,802 | 0 | 2,294 | 0.15 | microscope:analyze-exceptions |
| gc-pauses-in-proportion | 1.00 | 3 | 7 | 4 | 3 | 136,478 | 0 | 1,463 | 0.17 | microscope:analyze-jfr |
| heap-dump-what-holds-memory | 1.00 | 3 | 1 | 17 | 15 | 28,916 | 118,466 | 1,096 | 0.22 | microscope:analyze-heap |
| help-what-is-here | 1.00 | 3 | 6 | 3 | 2 | 83,521 | 0 | 1,210 | 0.09 | microscope:help |
| lock-contention-not-wall-clock | 0.93 | 3 | 7 | 4 | 3 | 124,214 | 0 | 1,482 | 0.11 | microscope:analyze-jfr |
| named-area-cpu | 1.00 | 3 | 7 | 4 | 3 | 181,347 | 0 | 1,862 | 0.19 | microscope:analyze-jfr |
| open-question-shows-menu | 1.00 | 3 | 6 | 3 | 2 | 131,650 | 0 | 1,516 | 0.18 | microscope:analyze-jfr |
| record-exceptions-next-time | 1.00 | 3 | 1 | 0 | 0 | 17,939 | 0 | 1,055 | 0.06 | — |
| route-did-my-change-regress | 1.00 | 3 | 11 | 8 | 5 | 205,047 | 0 | 2,910 | 0.2 | microscope:compare-jfr |
| route-record-first | 1.00 | 3 | 5 | 3 | 0 | 59,458 | 0 | 1,301 | 0.08 | microscope:profile-run |
| route-what-to-change | 1.00 | 3 | 9 | 5 | 3 | 129,952 | 0 | 1,629 | 0.15 | microscope:advise-jfr |
| route-why-slow-no-profile-named | 1.00 | 3 | 6 | 3 | 2 | 131,727 | 0 | 1,489 | 0.15 | microscope:analyze-jfr |
| two-areas-delegated | 0.95 | 3 | 1 | 9 | 6 | 45,994 | 212,879 | 1,417 | 0.34 | microscope:analyze-jfr |

Overall score 0.99, 11/14 cases at the threshold; 6.82 USD including the judge; Claude Code 2.1.316. Medians per case.

What it says:

- **Every case reaches its skill**, the routing cases included, and the three cases under their
  threshold each lost one run of three: two to an LLM judge (`compare-finds-the-regression`'s
  `honest-verdict`, `two-areas-delegated`'s `weighs-allocation-by-bytes`) and one to a regex
  (`lock-contention-not-wall-clock`'s `says-it-is-summed-over-threads`: its pattern was not in that
  run's answer). None failed in more than one run.
- **The heap answer walks the suspect by hand.** Of its 15 Jeffrey calls, 5–7 come after
  `heap_getLeakSuspects` and only reconstruct what the suspect could have carried: the GC-root path,
  its dominator children, `heap_getInstanceDetail`, and `heap_getDominatorTreeChildren` down the
  `ArrayList` and its `Object[]` to the `Checkout$Order` elements. The same chain in all three runs.

**A leak suspect carries its own evidence** (server change; plugin 1.0.12). Each suspect now carries
what keeps it alive (`gcRootPath`: the shortest strong path from a GC root, or, for a suspect that is
a root itself, its root type with the thread and the frame holding it), its accumulation point (the
deepest object down its largest dominated child that still retains 80% of it) and the classes that
point holds. The follow-up stops offering `heap_getPathToGCRoot` for a suspect that has its path.
`analyze-heap` passes the user's question to `heap-triage` as asked instead of a list of reports to
cover. Measured on 2026-10-31, three runs, against the baseline above:

| Case | Score | Heap calls per run | Tokens in (main / agent) | Cost | The answer names the code that holds the leak |
|---|---|---|---|---|---|
| `heap-dump-what-holds-memory` | 1.00 → 1.00 | 14, 14, 15 → 13, 12, 12 | 29k / 118k → 29k / 119k | $0.22 → $0.24 | 0 of 3 → 3 of 3 |

The calls fell less than the walk the suspect now makes redundant: in two runs of three the agent
still asks `heap_getPathToGCRoot` and walks `heap_getDominatorTreeChildren` to confirm what the suspect
says. What moved is the answer. Before, all three stopped at "held by a Java stack frame, probably a
live local variable"; now all three name thread `main` at `Checkout.main(Checkout.java:82)` — the
dump records the frame of every Java-frame root, and the path tool used to leave it out.

**One call orients a heap investigation** (server change; plugin 1.0.14). `heap_getHeapSummary`
now carries the parser's `warningCount` and `truncated` and which stored reports are built
(`reportsBuilt`, `reportsNotBuilt`), and offers `heap_prepare` only when one is missing; the
handbook and `analyze-heap` orient with it alone. Two eval artifacts were fixed with it:
- **The recorder never waited for an operation.** It read `state` from `operations_status`, whose
  field is `status`, so `heap_prepare` was recorded while it ran: every case saw "RUNNING, every
  stage pending" beside report mocks that were already built, and polled `heap_status`. It now waits
  for a finished state and records the answer at rest ("already completed").
- **`follows-a-gc-root-path` graded the route, not the outcome.** It required a
  `heap_getPathToGCRoot` call, which a suspect that carries its own `gcRootPath` makes unnecessary. It
  is now `names-what-keeps-it-alive`: the answer must name `Checkout.main`, the code holding the leak.

Measured on 2026-10-31, three runs, against the change before it:

| Case | Score | Heap calls per run | Tool calls | Tokens in (main / agent) | Cost |
|---|---|---|---|---|---|
| `heap-dump-what-holds-memory` | 1.00 → 1.00 | 13, 12, 12 → 4, 5, 6 | 15 → 8 | 29k / 119k → 29k / 120k | $0.24 → $0.17 |

The agent's tokens stay where they were: most of its 120k is its own handbook and system prompt,
read on each of its requests, so fewer requests cost less but the first ones still read it whole.

## Baseline on plugin 1.0.14

Taken once the heap changes above had landed, against the mocks recorded from that build (the
recorder now waiting for each operation), `claude-sonnet-5-5`, three runs a case. This is the
baseline the next change is measured against.

| Case | Score | Runs | Turns | Tool calls | Jeffrey calls | Tokens in | Agents' tokens in | Tokens out | Cost (USD) | Skills invoked |
|---|---|---|---|---|---|---|---|---|---|---|
| compare-finds-the-regression | 1.00 | 3 | 10 | 7 | 4 | 189,698 | 0 | 2,988 | 0.17 | microscope:compare-jfr |
| exceptions-to-the-throw-site | 1.00 | 3 | 10 | 7 | 4 | 151,922 | 0 | 2,361 | 0.16 | microscope:analyze-exceptions |
| gc-pauses-in-proportion | 1.00 | 3 | 7 | 4 | 3 | 136,294 | 0 | 1,435 | 0.16 | microscope:analyze-jfr |
| heap-dump-what-holds-memory | 1.00 | 3 | 1 | 8 | 6 | 28,713 | 119,613 | 918 | 0.2 | microscope:analyze-heap |
| help-what-is-here | 1.00 | 3 | 6 | 3 | 2 | 83,505 | 0 | 1,157 | 0.09 | microscope:help |
| lock-contention-not-wall-clock | 1.00 | 3 | 8 | 5 | 3 | 126,699 | 0 | 1,665 | 0.12 | microscope:analyze-jfr |
| named-area-cpu | 1.00 | 3 | 7 | 4 | 3 | 183,377 | 0 | 1,794 | 0.19 | microscope:analyze-jfr |
| open-question-shows-menu | 1.00 | 3 | 6 | 3 | 2 | 131,616 | 0 | 1,537 | 0.18 | microscope:analyze-jfr |
| record-exceptions-next-time | 1.00 | 3 | 1 | 0 | 0 | 17,939 | 0 | 1,033 | 0.06 | — |
| route-did-my-change-regress | 1.00 | 3 | 11 | 8 | 5 | 205,340 | 0 | 2,921 | 0.2 | microscope:compare-jfr |
| route-record-first | 1.00 | 3 | 5 | 3 | 0 | 59,559 | 0 | 1,314 | 0.08 | microscope:profile-run |
| route-what-to-change | 1.00 | 3 | 7 | 4 | 3 | 129,852 | 0 | 1,593 | 0.15 | microscope:advise-jfr |
| route-why-slow-no-profile-named | 1.00 | 3 | 6 | 3 | 2 | 131,767 | 0 | 1,438 | 0.15 | microscope:analyze-jfr |
| two-areas-delegated | 1.00 | 3 | 1 | 8 | 5 | 34,453 | 157,999 | 1,232 | 0.28 | microscope:analyze-jfr |

Overall score 1.00, 14/14 cases at the threshold; 6.50 USD including the judge; Claude Code 2.1.316. Medians per case.

Against the 1.0.11 baseline (medians):

- **Every case at its threshold**, 42 of 42 runs, where 1.0.11 lost one run in each of three cases.
  The lock case's regex and the two judges that dissented then (`honest-verdict`,
  `weighs-allocation-by-bytes`) all passed; that is three runs, not a fix to the graders.
- **`heap-dump-what-holds-memory`**: tool calls 17 → 8, Jeffrey calls 15 → 6, $0.22 → $0.20, and the
  answer names the code holding the leak.
- **`two-areas-delegated`**: 0.95 → 1.00, the main conversation 46k → 34k tokens with one call fewer,
  the analysts 213k → 158k, $0.34 → $0.28.
- **The other JFR cases** moved within a few thousand tokens and a cent either way, none of the
  changes since having touched them — except `exceptions-to-the-throw-site`, which read 123k → 151k
  with the same seven calls and $0.15 → $0.16. That is back where 1.0.6 measured it (157k): the 1.0.11
  figure is the one that stands out, and three runs do not say which is typical.

**The JFR facts, checked on JDK 25** (plugin 1.0.15). The skills' settings were read from JDK 21's
`.jfc` files; JDK 25's differ in three places, measured against its `lib/jfr/*.jfc` and a JVM that
throws 200,000 exceptions in a second:
- **`jdk.JavaExceptionThrow` is on, but throttled** (100/s under `default`, 300/s under `profile`;
  JDK 21 leaves it off). `settings=profile` recorded 300 of the 200,000 throws; the skills' advice,
  `#enabled=true`, recorded 180; `#enabled=true,…#throttle=off` recorded 200,043 on JDK 25 and 200,061
  on JDK 21, which warns that `throttle` does not exist and ignores it. That pair is now the advice,
  and `analyze-exceptions` treats JDK 25's per-type counts as a sample.
- **Socket and file waits** are recorded from 1 ms under both settings, throttled, where JDK 21 uses
  the lock thresholds (20 ms and 10 ms).
- **`jdk.CPUTimeSample`** is off in both settings, and turning it on leaves `jdk.ExecutionSample` on:
  a recording holds either or both, not one "instead" of the other.

`jdk.OldObjectSample` turned out to be on in both settings on both JDKs, so the server's "off in most
profiles" (`memory_leakCandidates`) and two docs pages were wrong too; an async-profiler recording is
the one that lacks it.

`record-exceptions-next-time` loaded no skill — the model answered from memory, with JDK 21's advice.
`profile-run`'s description now claims "how to record what a recording lacks". Three runs each:

| Case | Score | Skill | Gives the advice that works on JDK 25 | Cost |
|---|---|---|---|---|
| `record-exceptions-next-time` | 1.00 → 1.00 | — → `profile-run` | 0 of 3 → 3 of 3 | $0.06 → $0.07 |
| `route-record-first` | 1.00 → 1.00 | `profile-run` | — | $0.08 → $0.08 |

**The eval workload records every throw.** The recorder ran it with `#enabled=true` alone, so on
JDK 25 the exception mocks were a 300/s sample: 8,419 of 29,370 throwables named (29%). With
`#throttle=off` added, the same 30-second workload names 30,081 against a counted 29,699 — every
throw (the throw events can exceed the total: `jdk.ExceptionStatistics` is a once-a-second counter
and misses the throws before its first sample and after its last). Three runs of the case that reads
them:

| Case | Score | Tool calls | Tokens in | Cost |
|---|---|---|---|---|
| `exceptions-to-the-throw-site` | 1.00 → 0.93 | 7 → 7 | 152k → 153k | $0.16 → $0.15 |

Every run quotes the complete count (28,638 `NumberFormatException`s at `CouponParser.discount`). The
one miss is `classifies-it`'s judge (FAIL, FAIL, PASS) on a run that did not read the throw site's
source before classifying it — the grader that cost this case a run at 1.0.6 too, not the counts.

**Three judges become regexes, and one regex widens.** Across this file's runs the judges
`classifies-it`, `honest-verdict` and `weighs-allocation-by-bytes` failed 5 answers, and reading them
showed every one correct: "exception-driven control flow … check the format first", "medium
confidence until you confirm both runs did the same workload", "95.2% of bytes (13.6 GiB)". Each judge
bundled checks other graders already make (the site, the hotspots) with one question a pattern can
answer, so the questions became graders of their own:

| Case | Was | Now |
|---|---|---|
| `exceptions-to-the-throw-site` | `classifies-it` (judge) | `calls-it-control-flow`, `checks-before-parsing`, `does-not-claim-the-source` |
| `compare-finds-the-regression` | `honest-verdict` (judge) | `states-its-limits`, `no-measured-overall-slowdown` (not contains) |
| `two-areas-delegated` | `weighs-allocation-by-bytes` (judge) | the same name, a regex: byte figures, or shares said to be of bytes |
| `lock-contention-not-wall-clock` | `says-it-is-summed-over-threads` | the same, with "combined time", "per-thread", "cumulative" and the like |

Each pattern passes every answer this suite has kept — 18 for the exceptions case, 12 for the
comparison, 21 for the two areas, the 3 fresh lock answers — and fails a set of written-wrong ones
(a fix that catches instead of checking, an overall "12.4% slower" stated as measured, allocation
ranked by sample count, blocked time quoted against the recording). `checks-before-parsing` was
widened once, after it failed a correct "check it first … without throwing". Three runs each:

| Case | Score | Cost |
|---|---|---|
| `exceptions-to-the-throw-site` | 0.95 (the widened `checks-before-parsing` passes that run's answer, checked offline, not re-run) | $0.15 |
| `compare-finds-the-regression` | 1.00 | $0.20 |
| `two-areas-delegated` | 1.00 | $0.37 |
| `lock-contention-not-wall-clock` | 1.00 | $0.11 |

Five judges stay — `one-dump-is-a-state`, `profile-is-not-enough`, `pauses-in-proportion`, `orients`
and `puts-the-menu` — where what is checked is a claim's meaning rather than its words; none of them
has failed a run in this file's measurements.


**Two skills no case reached, and the SQL** (plugin 1.0.16). `regression-check` and `report` had no
case, and neither did `jfr-sql` or `heap-sql`.

| Case | Score | What the runs did | Cost |
|---|---|---|---|
| `route-two-revisions-not-profiled` | 1.00 | `regression-check` in 3 of 3, the pair and the one-difference rule stated, no Jeffrey call | $0.08 |
| `route-write-it-up` | 0.87 → 1.00 | see below | $0.20 → $0.20 |

`route-write-it-up` hands the model the user's own account ("most of the CPU goes to
PriceCalculator.formatLine", "about 81 seconds"). One run in three wrote it up without a call — "the
figures are yours" — which the evidence rule did not forbid in so many words. `report` now says that a
figure the user hands over is the question, not the evidence: read it back with the call that measures
it. Three runs after, every report quotes `blocking_monitors` (80.8 s over 6,079 contentions) and
corrects the user: the method holds 4.1% of the CPU samples (128), not most of them.

The SQL cases, against the real Jeffrey (`evals-live/`), three runs each:

| Case | Score | Right answer | SQL skill loaded | Tool calls | Cost |
|---|---|---|---|---|---|
| `exception-inputs-counted-in-sql` | 0.87 | 3 of 3 — 100 distinct inputs, all `SPRING-` coupons | `jfr-sql` in 1 of 3 | 6 | $0.14 |
| `orders-by-coupon-in-sql` | 0.75 | 3 of 3 — 1,446 of 5,825 orders | `heap-sql` in 1 of 3 | 20 | $0.23 |

- **Every answer is right; the misses are routing.** The runs that skipped the SQL skill wrote the
  query from `analyze-exceptions` or `analyze-heap` alone. Both now name their SQL skill where a query
  is written, and the traps it holds. That moved nothing three runs can tell apart: `heap-sql` 1 of 3
  before and after, `jfr-sql` 0 of 3 before and 1 of 3 in each of two rounds after (one of them
  against the stand-in).
- **`outbound_ref.field_kind` and `field_id` were undocumented.** The heap runs worked out which
  `field_id` is an `Order`'s coupon by experiment — grouping by it, or looking one reference up. It is
  the field's position in the object's whole layout, superclasses and primitive fields included
  (`HprofPassBWalker`), so it equals `field_index` only for a field the object's own class declares.
  `heap-sql` and the `heap-triage` handbook say so now, with `field_kind`'s three values.
- **One `heap-triage` run read the `string` table first** — the name pool, which the handbook now
  names as such — got no rows, and switched to `string_content`; `not-the-string-pool` fails it.
- `reads-string-contents` was dropped before scoring: one run counted the coupons with `heap_oql`
  (`o.coupon.toString().startsWith("SPRING-")`), which `analyze-heap` names for a filter over an
  object's own fields, and was right.

The two analysis skills this touched, re-measured on their mocked cases (three runs, against the
1.0.14 baseline): `heap-dump-what-holds-memory` 1.00 → 1.00, 8 → 9 tool calls, $0.20 → $0.18;
`exceptions-to-the-throw-site` 1.00 → 1.00, 7 → 7 tool calls, $0.16 → $0.15.

## Baseline on plugin 1.0.17

Re-recorded from the build after #377 and the fixes below (a new recording of the same workload:
86 mocked tools, 257 fixtures; the live figures are now 1,427 `SPRING-` orders of 5,775), and run
with the current graders — `recorded-call-semantics` in every mocked case, the `honest-verdict`
judge on the comparison — so these figures replace the 1.0.14 baseline as the one to measure
against. Three runs a case, `--threshold 1`:

| Case | Score | Runs | Turns | Tool calls | Jeffrey calls | Tokens in | Agents' tokens in | Tokens out | Cost (USD) | Skills invoked |
|---|---|---|---|---|---|---|---|---|---|---|
| compare-finds-the-regression | 1.00 | 3 | 10 | 7 | 4 | 191,190 | 0 | 2,584 | 0.17 | microscope:compare-jfr |
| exceptions-to-the-throw-site | 1.00 | 3 | 9 | 6 | 4 | 126,055 | 0 | 2,241 | 0.15 | microscope:analyze-exceptions |
| gc-pauses-in-proportion | 1.00 | 3 | 7 | 4 | 3 | 137,307 | 0 | 1,502 | 0.16 | microscope:analyze-jfr |
| heap-dump-what-holds-memory | 1.00 | 3 | 1 | 8 | 6 | 29,360 | 121,898 | 1,067 | 0.17 | microscope:analyze-heap |
| help-what-is-here | 1.00 | 3 | 6 | 3 | 2 | 84,435 | 0 | 1,169 | 0.09 | microscope:help |
| lock-contention-not-wall-clock | 0.94 | 3 | 7 | 4 | 3 | 125,350 | 0 | 1,367 | 0.11 | microscope:analyze-jfr |
| named-area-cpu | 1.00 | 3 | 7 | 4 | 3 | 182,364 | 0 | 1,843 | 0.19 | microscope:analyze-jfr |
| open-question-shows-menu | 1.00 | 3 | 6 | 3 | 2 | 132,590 | 0 | 1,386 | 0.17 | microscope:analyze-jfr |
| record-exceptions-next-time | 1.00 | 3 | 3 | 1 | 0 | 39,314 | 0 | 958 | 0.08 | microscope:profile-run |
| route-did-my-change-regress | 1.00 | 3 | 11 | 8 | 5 | 206,687 | 0 | 2,665 | 0.2 | microscope:compare-jfr |
| route-record-first | 1.00 | 3 | 5 | 3 | 0 | 60,588 | 0 | 1,289 | 0.08 | microscope:profile-run |
| route-two-revisions-not-profiled | 1.00 | 3 | 7 | 5 | 0 | 87,239 | 0 | 881 | 0.08 | microscope:profile-run, microscope:regression-check |
| route-what-to-change | 1.00 | 3 | 8 | 5 | 3 | 131,074 | 0 | 1,630 | 0.15 | microscope:advise-jfr |
| route-why-slow-no-profile-named | 1.00 | 3 | 6 | 3 | 2 | 132,819 | 0 | 1,517 | 0.15 | microscope:analyze-jfr |
| route-write-it-up | 1.00 | 3 | 9 | 6 | 5 | 138,886 | 0 | 3,303 | 0.2 | microscope:jfr-sql, microscope:report |
| two-areas-delegated | 0.92 | 3 | 1 | 10 | 7 | 45,834 | 213,401 | 1,111 | 0.37 | microscope:analyze-jfr |

Overall score 0.99, 14/16 cases at the threshold; 7.64 USD including the judge; Claude Code 2.1.316. Medians per case.

The live SQL cases, against a Jeffrey loaded with the same recording (`--load`):

| Case | Score | Right answer | SQL skill loaded | Tool calls | Cost |
|---|---|---|---|---|---|
| `exception-inputs-counted-in-sql` | 0.93 | 3 of 3 | `jfr-sql` in 2 of 3 | 6 | $0.14 |
| `orders-by-coupon-in-sql` | 1.00 | 3 of 3 — 1,427 of 5,775 | `heap-sql` in 3 of 3 | 7 | $0.16 |

What the two misses were:

- **`two-areas-delegated`, 2 of 3 runs:** `profile-analyst` asked for the allocation export with
  `threadMode: true`, a per-thread split no fixture holds, so it read the aggregated tree for the
  split it asked for — `recorded-call-semantics` failed both, as it should: nothing in the plugin
  asks for `threadMode` there, and a mock cannot answer it. The answers themselves name the right
  hotspots.
- **`lock-contention-not-wall-clock`, 1 run:** `says-it-is-summed-over-threads` missed a correct
  "about 2.6 threads blocked at any moment … each spent about 20 s". It now also matches the
  threads-blocked-at-a-moment and per-thread phrasings; it passes the 3 answers kept from this run
  and still fails the written-wrong ones (a share of the recording, a runtime spent waiting).

What changed since 1.0.16, each measured where it applies:

- **The SQL skills' descriptions say when to load them** — before the first `jfr_executeQuery` or
  `heap_executeQuery`, whichever skill drives the analysis — and name their traps. Two live rounds
  since: `jfr-sql` 5 of 6 runs (1 of 3 before), `heap-sql` 5 of 6 (1 of 3 before); the heap case went
  from 20 tool calls to 7–8, every answer right. Plugin 1.0.17.
- **`flamegraph_list` lists an imported profile's dimensions.** The MCP catalog read the curated JFR
  list for every format, which holds no pprof or OTLP dimension (`pprof.cpu`, …), so an import was
  reported as having nothing to graph while its UI page graphed it. It now reads what that page
  reads. Checked on a pprof file Jeffrey exported from the candidate recording: `flamegraph_list`
  answers OK with its dimension, and its export answers OK.
- **Preparing the thread report alone builds no tree.** Without a current tree its retained sizes
  stay unknown (never read from an obsolete tree), it is stored without a tree version, and the read
  after a tree is built recomputes it over that tree.
- **`build/scripts/DuckDbOrderRepro.java`** reproduces the driver's misordered `ORDER BY` with no
  Jeffrey code (DuckDB v2.0.0: 3 rows of 1,000,000 out of order prepared, 103–217 with
  `jdbc_stream_results`, 0 for a plain statement, one thread or the setting on) — the check to run
  before a new driver is trusted with result order.

**The analyst leaves `threadMode` off** (plugin 1.0.18). The 1.0.17 baseline's one real miss: in 2 of
3 runs of `two-areas-delegated`, `profile-analyst` asked for the allocation export split per thread,
which nothing in the plugin asked for — the split repeats every path once per thread — and which
`recorded-call-semantics` fails, no fixture holding it. The handbook now leaves `threadMode` off
unless the question is about threads. Three runs:

| Case | Score | Per-thread exports | Agents' tokens in | Cost |
|---|---|---|---|---|
| `two-areas-delegated` | 0.92 → 1.00 | 2 of 6 → 0 of 6 | 213k → 164k | $0.37 → $0.35 |

**The recording is kept.** The three files behind the mocks are in jeffrey-recordings
(`plugin-evals/`), and `--load` checks them by checksum, so the live cases run on any machine.

## Plugin 1.0.19: what a real analysis showed

A real session — a UAT service's last hour, pulled off the remote and analysed — surfaced five
defects the mocks could not:

- **The analyst received no flamegraph.** Claude Code hands the model a result's
  `structuredContent` when there is one, not its text, and the exports kept their Markdown in the
  text only: the model got the record (`markdownChars`, the filters, the link) and no tree. The seven
  Markdown exports (`flamegraph_export`, `compare_movements`, `compare_flamegraph`,
  `traces_traceExport`, `traces_operationExport`, `traces_spanFlamegraphExport`,
  `traces_operationFlamegraphExport`) now carry the document in the record's `markdown`, cut so the
  record fits the 120,000-character cap, measured on the serialised JSON. Checked with `claude -p` against a running Microscope: the CPU export reached the model as
  1,558 characters of metadata before and 14,198 characters with the tree after, and the hottest
  frame was named; a `FULL` per-thread allocation export arrived inline at 120,000 characters,
  `truncated: true`. The mocks were re-recorded (`--reuse`) as the model sees them.
- **The download overstated its span.** A part's end was its last chunk's start plus one measured
  chunk length — 19:31–20:31 for a profile of 49 minutes. Once the recording is here, the answer
  reports the span read from the recording itself.
- **A memory warning was interpreted without the heap's size.** `analyze-jfr` and the analyst now
  read `jvm_configuration` (`GC_HEAP_CONFIGURATION`), the container limit and `AlwaysPreTouch` first.
- **Repeated downloads made duplicate profiles** — four copies of one recording. A download whose
  chunks are exactly those of a copy already here, the same part or a session that has not grown, is
  handed back as that copy, from the UI and from an agent alike.
- **The ledger rendered as a table under a blank header.** It is a labelled list now, with an
  **In progress** line for areas an analyst still holds.

Measured on 1.0.19 with the re-recorded mocks, three runs a case, `--threshold 1`:

| | 1.0.17 baseline | 1.0.19 |
|---|---|---|
| Mocked cases at the threshold | 14 of 16 | 15 of 16 |
| Overall score | 0.99 | 1.00 |
| Cost, including the judge | $7.64 | $7.56 |
| Live SQL cases | 0.93 and 1.00 | 1.00 and 1.00 |

The one miss is a run of `lock-contention-not-wall-clock` that wrote, of the 80.5 s blocked across 4
threads, "each thread spent about 66% of the recording waiting" — a per-thread average it labelled as
an assumption, which `not-a-share-of-wall-clock` reads as a share of the recording.

## Plugin 1.0.20: links the user can see, and Traces by name

- **Links are written out.** Claude Code renders a Markdown link as its name alone, the URL hidden
  behind it, so a real session's "GC dashboard" and "summary" read as plain words and were lost when
  copied. Every skill and agent now writes the full URL beside the name. Two new graders hold
  `named-area-cpu` and `gc-pauses-in-proportion` to it — a `http://localhost:…/profiles/` URL in the
  answer, and no `](http`; both passed in all six runs.
- **Traces is an area of its own.** It was folded into Requests and calls, so the menu never said
  "traces". To keep four groups of four, When it happened is no longer an area — every whole-recording
  export already offers `timeline_hotWindows` as its next call — and Lock contention moves into Code
  with the other sample kinds. Waiting is Traces, Requests and calls, Database and I/O.

| | 1.0.19 | 1.0.20 |
|---|---|---|
| Mocked cases at the threshold | 15 of 16 | 15 of 16 |
| Overall score | 1.00 | 1.00 |
| Cost, including the judge | $7.56 | $7.44 |
| Live SQL cases | 1.00 and 1.00 | 1.00 and 1.00 |

The miss this time is a run of `two-areas-delegated` in which `profile-analyst` asked for the CPU
export with `excludeIdle: true`, a call no fixture holds; `lock-contention-not-wall-clock`, the 1.0.19
miss, passed all three runs.
