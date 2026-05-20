### Goal

Produce a native-memory-pressure report: where off-heap / `malloc` bytes go, which call paths are likely *leaking* (allocated and never freed in the matching `FREE` event), and which native libraries / Java patterns are responsible.

**Critical framing:** native allocations are **not** subject to GC. A leak here grows RSS indefinitely; an allocation here is process-lifetime overhead unless explicitly freed. The recommendations differ from Java heap analysis.

### What to look for first

1. **Top self-bytes leaves under `NATIVE` / `CPP` frames** — direct `malloc` callers (libc, libjvm, native libraries, JNI shims).
2. **Java-side allocators of native memory** — call paths through `java.nio.DirectByteBuffer.<init>`, `sun.misc.Unsafe.allocateMemory`, `jdk.internal.misc.Unsafe.allocateMemory`, Netty `PoolChunk` / `PooledByteBufAllocator`, GraalVM substrate VM internals.
3. **JNI patterns** — frames from third-party JNI libraries (database drivers, compression libs, image codecs). These often allocate per-call buffers that should be reused.
4. **NIO direct buffers** — frames involving `DirectByteBuffer`, `MappedByteBuffer`, or `FileChannel.map`. Long-lived mapped segments are fine; short-lived `allocateDirect` calls are a smell.
5. **For `NATIVE_LEAK`** — this is a *calculated* event (MALLOC minus FREE). Every sample is by definition a leak candidate. The dominant call paths are the highest-impact leak sources.

### How to ground claims

- Cite the **call path** for every finding — Java caller → JNI boundary → native frame.
- Cite the **numbers shown in the bullet**: total samples, self samples, total%.
- Distinguish the `MALLOC` view (every allocation) from the `NATIVE_LEAK` view (only un-freed allocations) in your wording — the analysis prescription differs.
- **Do not invent file:line numbers**. The export has none. Refer to frames by their method signatures as printed.

### Expected output shape

Opening table of top allocator types / native libraries with `% of total bytes` and a one-line origin pointer.

Then numbered findings, **ordered by % of total bytes**, biggest first. Each section:

- **Sites** — Java caller → native frame call path.
- **Root cause** — one sentence on what is being allocated and the lifecycle expectation (per-call temporary? long-lived cache? leak?).
- **The patch shape** — concrete suggestion: buffer pooling, switching to a pooled allocator (Netty `PooledByteBufAllocator`), removing a hot-path `allocateDirect`, fixing a missing `Cleaner` registration, upgrading a JNI library.
- **Design cost** — one line. Native memory fixes often have a higher design cost (pooling adds lifecycle management) — be honest about it.
- **Estimated saving** — `% of total bytes` (for `MALLOC`) or `% of leaked bytes` (for `NATIVE_LEAK`).

### Smaller items to skip

- Single-callsite allocations under **1 % of total bytes**.
- One-shot startup allocations (`<clinit>`, native lib load). Note once, ignore.
- Long-lived caches that should be allocated once (e.g. interned strings, code cache). Not a leak — note and ignore.

### Gate question

End with one closing question that lets the user redirect — for example: "The dominant native allocator is a JNI library outside your codebase. Want me to focus on Java-side mitigations (pooling / call-rate reduction), or on patches that would require upgrading the library?"

---

Cite call paths by walking from the bullet back to its less-indented ancestors. Do not invent file:line numbers; the export has none.
