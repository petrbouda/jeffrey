### Goal

Produce a CPU-optimisation report: which methods are burning on-CPU time, distinguish leaf compute from orchestration, and flag JIT-tier signals that suggest tuning opportunities.

### What to look for first

1. **Heaviest leaves with `self ≈ total`** — these are the actual compute hotspots. A leaf at 15 % `self` is doing 15 % of all on-CPU work.
2. **Orchestration vs leaf** — a frame with `self << total` is just a router/dispatcher; the work is in its children. Don't propose to "optimise" an orchestration frame; walk into it.
3. **JIT tier-mix tags** — the frame's `[…]` tag is the strongest CPU-specific signal:
   - `[C2]` — already fully optimised. Improvements need algorithmic changes, not JIT tuning.
   - `[C1]` only on a hot method — stuck in tier 1. Candidates: `-XX:CompileThreshold`, inlining caps (`MaxInlineLevel`, `MaxInlineSize`), method too large for C2, OSR-only compilation.
   - `[C1: N, C2: M]` — being promoted; if M << N the method is mostly in C1. Same investigations as above.
   - `[INT]` on a hot method — not JITted at all. Almost always a bug: method excluded, deopt churn, or class-init lock.
   - `[INL]` — inlined into caller; no separate runtime frame. Confirms the JIT did the work.
4. **Native / kernel frames** — `[NATIVE]` and `[KERNEL]` tags on heavy frames mean compute is leaving the JVM. Different investigation (syscalls, native libs).
5. **Synthetic frames** — `[SYNTHETIC]` markers (thread names, lambda pseudo-frames) are structural; don't treat them as work.

### How to ground claims

- Cite the **call path** for every finding: the bullet plus its less-indented ancestors back to `[root]`.
- Cite the **numbers shown in the bullet**: total samples, self samples, total%.
- Always mention the **frame type tag** — it is the key CPU-specific signal.
- One CPU sample ≈ one sampling interval on-CPU. Sample counts are proportional to CPU time, not wall time.
- **Do not invent file:line numbers**. The export has none. Refer to frames by their method signatures as printed.

### Expected output shape

Opening table of top compute hotspots (method + type tag + `% of CPU`).

Then numbered findings, **ordered by % of CPU**, biggest first. Each section:

- **Sites** — call path, leaf frame highlighted.
- **What it's doing** — one sentence describing the work based on the method signature and ancestor context.
- **JIT signal** — type tag interpretation: is this method optimised, stuck in C1, mixed?
- **The investigation / fix** — concrete next step: profile this method with `-XX:+PrintInlining`, look at C2 deopt traps, restructure the algorithm, parallelise, cache, etc. Recommend the smallest investigation that would confirm the fix is worth it.
- **Design cost** — one line.
- **Estimated saving** — `% of CPU` (cap optimistically at the frame's `total%`; you can't save more time than the frame uses).

### Smaller items to skip

- Frames under **1 % of CPU**.
- Frames inside third-party hot libraries you cannot patch (e.g. the JDK's `String.indexOf`). Note them once if dominant, then move to your own code.
- Per-call overhead of small reflective / framework dispatch frames (`Method.invoke`, `LambdaForm`, `DirectMethodHandleAccessor`). Usually unavoidable.

### Gate question

End with one closing question that lets the user redirect — for example: "The dominant hotspot is in a third-party library; the next-biggest one in your code is at 6 %. Want me to focus on the library mitigation path, or on the 6 % in-house frame?"

---

Cite call paths by walking from the bullet back to its less-indented ancestors. Do not invent file:line numbers; the export has none.
