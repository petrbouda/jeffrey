# Refresh the Agent Plugin page

Bring the docs site's Agent Plugin page (`/agent-plugin`, data in `jeffrey-pages/src/data/plugin-usage.json`) up to
date after a change to the MCP server or the `microscope` plugin, and say what the new runs show. Paid model runs are
involved: nothing is run before the user agrees to the plan and its cost.

`$ARGUMENTS` may name cases (`fix-it slow-endpoints`), `all`, or say what changed; empty means "whatever is stale".

## 1. Prerequisites

- `JAVA_HOME` points to a JDK 25 (the build and the recording need it); `mvn` and `claude` are on the `PATH`.
- If the change touched `build/plugin/` (agents, shared fragments), run `python3 build/scripts/render-plugin.py`
  first, so the rendered skills and agents are what the runs read.
- The change is in the working tree or committed; the runs read the files as they are now.

## 2. Plan — free

```bash
python3 build/scripts/plugin-usage-map.py --stale
python3 build/scripts/refresh-plugin-evals.py [--cases <names> | --all] [--rerecord auto|yes|no]
```

The first lists each case whose replay no longer describes the plugin and what moved under it (`skills`, `agents`,
`routing` — any skill's description —, `case`, `answers` — the mocks). The second prints the plan: the cases, why each
one, whether the mocks are re-recorded (automatic when the server's main sources or the recorder changed since they
were last recorded — that re-runs every case, since every profile id changes), and the estimated cost. Show the user
the plan and the cost and ask before going on. A change that needs no new runs (only the documented wiring) is
`python3 build/scripts/plugin-usage-map.py --wiring-only`, which is free.

## 3. Run — paid, only after the user agreed

```bash
python3 build/scripts/refresh-plugin-evals.py --run [same arguments] --max-cost-usd <the agreed ceiling>
```

It re-records when planned (builds the jar, boots an empty Jeffrey, regenerates the tools snapshot from it if it
drifted, records into the heap cases' directory), runs the cases with `--keep-temp --no-publish`, regenerates the map
with the version in `build/plugin/manifests/identity.json`, prints each case's runs and failed graders, removes the
harness's run directories and runs the offline checks. It refuses to regenerate the map when the plugin changed while
the cases ran — do not edit `jeffrey-agent-plugin/` while it runs.

## 4. Read the misses

For each case below 3 of 3, read the failed run's reply and its graders' reasons on the page data
(`plugin-usage.json` → the case's replay → `runs[].reply`, `graders[].explanation`) and decide which it is:

- **The plugin** — a skill or agent did not say, or said ambiguously, what the run needed: propose the text change.
- **The mocks** — the run made a reasonable call no fixture holds (a `recorded-call-semantics` failure, a tool error
  from a missing fixture): the fix is in `build/scripts/record-plugin-eval-mocks.py` (what it seeds or expands), then
  a re-recording — never a hand-edited fixture.
- **The grader** — the reply does what the case asks and the grader's wording missed it: widen it and test the
  new alternatives against the reply and against replies that must still fail.
- **Noise** — a judge split on a reply that meets the rubric: say so, and do not change anything for it.

Fix what the user agrees to, then go back to step 2: the fixed cases are stale again.

## 5. Finish

- Update the plugin version in `build/plugin/manifests/identity.json` (and re-render) when skills or agents changed.
- Record the round in `jeffrey-agent-plugin/evals/README.md`: what missed and why, what changed, the results table
  against the previous version, and the spend.
- `python3 build/scripts/plugin-usage-map.py --stale` should list only the live cases (`evals-live/`), which this does
  not run; then `cd jeffrey-pages && npm run build`.
- Commit and push only when the user asks.
