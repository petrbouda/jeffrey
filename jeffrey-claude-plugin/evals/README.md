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
