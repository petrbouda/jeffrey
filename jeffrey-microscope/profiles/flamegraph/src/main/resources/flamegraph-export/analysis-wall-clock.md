### Goal

Produce a latency report: where the total elapsed nanoseconds went, distinguishing on-CPU compute work from off-CPU work (IO, blocking, parking), and pointing at the right kind of fix for each.

**Critical framing:** wall-clock samples include both on-CPU and off-CPU time. A frame heavy in this profile is **not necessarily** burning CPU — it might be parked, waiting on a socket, sleeping, holding a JDBC roundtrip open. The fix kind differs entirely. The same applies to `METHOD_TRACE` events, which measure end-to-end method duration: the duration includes whatever the method waited on.

### What to look for first

1. **Heaviest leaves with `self ≈ total`** — but read the leaf method name carefully before classifying.
2. **Classify each heavy frame as on-CPU vs off-CPU by name**:
   - **Off-CPU signatures** — `Socket.read` / `Socket.write`, `SocketInputStream.read`, `Selector.select`, `LockSupport.park` / `Unsafe.park`, `Object.wait`, `Thread.sleep`, `FileInputStream.read` on a blocking FS, JDBC `executeQuery` / `next` (waits for the DB), HTTP client `send` / `execute`.
   - **On-CPU signatures** — explicit business logic (`com.yourorg.foo.calculate`, `java.util.HashMap.get`, tight loops, string parsing, JSON serialisation, cryptography).
   - **Ambiguous** — framework dispatch frames (`Servlet.doFilter`, Spring filters). Walk deeper.
3. **JIT tier tags** — same meaning as CPU profile, but **only useful for on-CPU frames**. A `[C2]` tag on `Socket.read` doesn't mean it's optimised; it means the wrapper is — the wait still costs time.
4. **Long blocking-IO calls** — if the heaviest leaf is `Socket.read`, you are IO-bound, not compute-bound. Optimising the method won't help; reducing call rate, batching, or caching will.

### How to ground claims

- Cite the **call path** for every finding.
- Cite the **numbers shown in the bullet**: total samples, self samples, total%.
- **Explicitly classify** each finding as on-CPU work, off-CPU work, or mixed/ambiguous — name the basis (the leaf method signature).
- If the profile is dominated by off-CPU work, **recommend re-running with a CPU-only profile** (`EXECUTION_SAMPLE`) if the user's goal is compute optimisation. Wall is the wrong tool for that.
- **Do not invent file:line numbers**. The export has none. Refer to frames by their method signatures as printed.

### Expected output shape

Opening table of top latency contributors (method + classification + `% of wall`).

Then numbered findings, **ordered by % of wall**, biggest first. Each section:

- **Sites** — call path, leaf highlighted.
- **Classification** — on-CPU / off-CPU / ambiguous, with the reasoning (the leaf's signature).
- **What it's doing** — one sentence.
- **The investigation / fix** — *match the fix kind to the classification*:
  - On-CPU → optimise the method (same as CPU recipe).
  - Off-CPU → reduce call rate, batch, cache, parallelise, async, increase the resource on the other side.
  - Ambiguous → walk deeper or sample on-CPU to confirm.
- **Design cost** — one line.
- **Estimated saving** — `% of wall`.

### Smaller items to skip

- Frames under **1 % of wall**.
- Synthetic / framework dispatch frames that just route into work below them.
- Background / housekeeping thread paths if the user's concern is request latency. State that you're skipping them and why.

### Gate question

End with one closing question that lets the user redirect — for example: "Roughly 70 % of the wall time is in `Socket.read` waiting for the backend service. Want me to focus on the IO side (call-rate, batching, caching) or on the residual on-CPU work in your code?"

---

Cite call paths by walking from the bullet back to its less-indented ancestors. Do not invent file:line numbers; the export has none.
