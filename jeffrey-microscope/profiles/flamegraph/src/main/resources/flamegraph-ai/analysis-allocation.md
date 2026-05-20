### Goal

Produce a concrete allocation-optimisation report for this profile: where the bytes go, why those sites allocate, and the smallest set of changes that would most reduce TLAB pressure.

### What to look for first

1. **Top self-bytes leaves** — frames where `self ≈ total`. These are the actual allocation sites; ancestors are just the call path that reached them.
2. **The leaf type** — a leaf called `int[]`, `byte[]`, `java.lang.Long`, `java.util.HashMap$Node` etc. tells you the *type* being allocated. Cross-reference with its parent frame to learn *why*.
3. **Boxing patterns** — `java.lang.Long` / `java.lang.Integer` directly under a `Map.get` / `Map.put` / `Map.containsKey` call is the signature of `Map<Long,V>` or `Map<Integer,V>` boxing. Almost always a primitive-keyed-map opportunity.
4. **Per-record record/object allocations** — short-lived domain records (e.g. `*Dump`, `*Record`, `*Info`) allocated inside a hot dispatch loop and immediately consumed. Candidates for visitor-pattern flattening.
5. **Per-row arrays** — `int[]` / `byte[]` / `boolean[]` allocations under tight loops. Candidates for CSR (compressed sparse row), buffer reuse, or bulk allocation.
6. **`String.<init>` / `Arrays.copyOfRange`** — usually unavoidable when constructing a String, but check whether the String is built and then dropped (e.g. exceeds a length threshold), which would let you skip the decode.

### How to ground claims

- Cite the **call path** for every finding: the leaf bullet plus its less-indented ancestors back to the synthetic `[root]`.
- Cite the **numbers shown in the bullet**: the total samples, the self samples, and the total% computed for that frame.
- Mention the **frame type tag** (`[C2]`, `[C1: …, C2: …]`, etc.) if it carries signal — JIT-tier mix on an allocation site is rare but possible.
- **Do not invent file:line numbers**. The export has none. Refer to frames by their method signatures as printed.

### Expected output shape

A short opening table of the top allocator *types* (e.g. `int[]`, `Long`, `HprofRecord$InstanceDump`) with `% of total bytes` and a one-line "where it comes from" pointer.

Then a numbered section per fix, **ordered by % of total bytes saved**, biggest first. Each section:

- **Sites** — call paths citing leaf and parent frames.
- **Root cause** — one sentence on what is being allocated and why (boxing? per-row array? throwaway record?).
- **The patch shape** — a concrete code-level suggestion (e.g. "Switch `Map<Long,V>` to Eclipse Collections `LongObjectHashMap`", "Replace `int[][]` adjacency with CSR `int[] edges + int[] offsets`", "Flatten record allocation into a typed visitor passing primitives").
- **Design cost** — one line: `zero`, `small`, `real trade-off — present both options`. Default rule: prefer cleaner OO design over hot-path tricks unless the design-clean option also wins on allocation. When a hot-path trick would cost design clarity, surface both options and ask the user to choose.
- **Estimated saving** — `% of total bytes`.

End with a "Smaller items I'd skip for now" list, an aggregate "% addressable", and a single gate question — see below.

### Smaller items to skip

- Single-call-site allocations under **1 % of total bytes** (noise floor — likely not worth the change).
- Allocations inside third-party library frames you cannot patch (driver internals, JNI byte[] staging). Mention them once with reasoning, then move on.
- Unavoidable structural allocations (e.g. `String.<init>` must copy its `byte[]`). Note once and move on.

### Gate question

End with one closing question that lets the user redirect before they invest in any patch — for example: "The top fix removes ~28 % of allocations and is a mechanical Map-type swap. Want me to start with that, or order by something other than % saved (e.g. blast radius)?"

---

Cite call paths by walking from the bullet back to its less-indented ancestors. Do not invent file:line numbers; the export has none.
