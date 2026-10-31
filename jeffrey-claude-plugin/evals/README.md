# Microscope plugin evals

Behaviour tests for the skills and agents, run with
[`claude plugin eval`](https://code.claude.com/docs/en/plugin-evals). The structural checks
(`build/scripts/check-plugin-references.py`, `validate-agent-plugin.py`, `render-plugin.py --check`)
say the plugin names real tools and is well formed; these say whether a model that loads it does the
right thing with a real Jeffrey's answers: reaches the right skill, calls what the skill says in the
order it says, finds what the recording holds, and writes it up by the `report` skill's rules.

## Running them

```bash
claude plugin eval ./jeffrey-claude-plugin --ablation none --keep-temp -j 4 --max-cost-usd 20 \
  --json /tmp/microscope-evals.json
python3 build/scripts/summarize-plugin-evals.py /tmp/microscope-evals.json
```

`--ablation none` because a run without the plugin has no Jeffrey to call: the no-plugin arm would
score every case zero and say nothing. With it, the `Skill` graders count toward the score.
`--keep-temp` keeps each run's trace, which the summary reads for its tool-call and token figures.

Each case runs three times by default (`runs` in its `prompt.md`). A full pass costs a few dollars of
model time at list price; `--max-cost-usd` stops it at a ceiling.

## The cases

| Case | The question | What it holds the plugin to |
|---|---|---|
| `open-question-shows-menu` | "What is wrong with this recording?" | `analyze-jfr`, `profiles_summary`, the menu put to the user, no export before a choice |
| `named-area-cpu` | "Where does the CPU go?" | straight to the CPU flamegraph, the dominant frame named, its source and denominator stated |
| `compare-finds-the-regression` | "Did my change make CPU worse?" | `compare_list` before `compare_movements`, `compare_quality` read, the after-run as primary, the regressed method named, a verdict that states its limits |
| `exceptions-to-the-throw-site` | "Why so many exceptions?" | `jvm_exceptions`, `jvm_exceptionSites` with the application's package, the throw site named and classified |
| `heap-dump-what-holds-memory` | "What holds the memory in this .hprof?" | the import, a GC-root path behind the leak claim, one dump read as a state |
| `help-what-is-here` | "What can Jeffrey do, and what is in it?" | `help`, the catalogue listed, nothing analysed unasked |
| `record-exceptions-next-time` | "How do I record the exception types?" | `jdk.JavaExceptionThrow#enabled=true`, and that `settings=profile` does not turn it on |
| `two-areas-delegated` | "CPU and allocation, both" | both exports read, each hotspot named, allocation weighed by bytes — and whether the two are handed to `profile-analyst` |
| `gc-pauses-in-proportion` | "Are GC pauses hurting it?" | `jvm_gc`, the pauses measured against the recording (142 ms of 30.6 s), no tuning from one fired rule |
| `lock-contention-not-wall-clock` | "Which locks hold the threads back?" | `blocking_monitors`, `SessionCache` named, its 80.8 s of blocked time read as thread-time, never as a share of the recording |
| `route-why-slow-no-profile-named`, `route-what-to-change`, `route-did-my-change-regress`, `route-record-first` | four prompts more than one skill's description claims | each reaches the one skill meant for it (`analyze-jfr`, `advise-jfr`, `compare-jfr`, `profile-run`) and makes that skill's first call |

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
the new recording's.

What the mocks cannot do, so the cases stay away from it:

- **State.** A mock answers the same for the same arguments: the heap dump is recorded already
  prepared, and `jvm_autoAnalysis` as not yet computed.
- **Free-text arguments.** A tool whose required argument is a SQL query, a URI or a path other than
  the recorded heap dump has no mock, so `jfr_executeQuery`, `heap_executeQuery`, `heap_oql`,
  `jfr_getProfileInfo`, `http_endpoint` and the like are absent from the runs (85 of the 112 tools
  are mocked).
- **The remote and the IDE.** The recording Jeffrey has no remote and no IntelliJ attached.
- **Arguments the recorder did not reach.** A call with arguments nothing in the recording offered
  answers `no such fixture` as a tool error.

Reading the figures:

- **Tokens and calls are the main conversation's and its subagents' calls, but only the main
  conversation's tokens**: a trace counts a delegated agent's tool calls, not what that agent read.
  A case that delegates — the heap case hands its route to `heap-triage` — reads as cheap on tokens.
- **An LLM judge is noisy at three runs**: one dissenting run in three moves a case's score by about
  0.1. Where a rule can be checked deterministically (a frame named, a denominator stated), a
  `regex` or `tool_used` grader is used instead.

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
