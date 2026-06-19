# Allocation Optimization Plan

Source: JFR `jdk.ObjectAllocationInNewTLAB` profile, 20683 samples, 14.8 GiB allocated, prune threshold 1.0 %.

## Top allocator types

| Type | % of total | Where it comes from |
|---|---|---|
| `int[]` (per-row) | **21.7 %** | `DominatorTreeBuilder#loadSuccessors` (10.7 %) and `#invert` (11.0 %) — one fresh int[] per graph node/row |
| `java.lang.Long` (boxing) | **20.7 %** | `HprofPassBWalker#emitInstanceRefs` → `Long.valueOf` (18.7 %) + `HprofStringContentWriter#runWorker` → `Long.valueOf` (2.0 %) |
| `HprofRecord$*` records (per-record dispatch) | **18.5 %** | `HprofSubRecordReader#emitInstanceDump` (15.8 %), `#emitPrimitiveArrayDump` (1.5 %), `#emitObjectArrayDump` (1.2 %) |
| `byte[]` / `String` (decode → re-encode) | **9.0 %** | `JavaStringDecoder#decodeContent` (5.5 %) + `DuckDBAppender#append` UTF-8 re-encode (3.5 %) — paid twice for the same content |
| `byte[]` (HprofMappedFile read) | 3.4 % | `HprofMappedFile#readBytes` — structural slab read |
| `BigInteger` + backing `byte[]` | 2.6 % | `DuckDBVector#getHugeint` reached from `getLong` — column declared HUGEINT, only a long is needed |
| `boolean[]` (driver) | 3.3 % | `DuckDBNative#duckdb_jdbc_fetch` — driver internals |

---

## 1. CSR-flatten the dominator graph — ~21.7 %

**Sites**
- `DominatorTreeBuilder#loadSuccessors` self **2218 (10.7 %)** → `int[] [SYNTHETIC] — 2218`
- `DominatorTreeBuilder#invert` self **2277 (11.0 %)** → `int[] [SYNTHETIC] — 2277`

Both reached via `HeapDumpController#runComputeDominator` → `…#doBuild` → `loadSuccessors` / `invert`.

**Root cause** — One `int[]` allocated per node (successors) and again per node during inversion. Classic per-row adjacency.

**Patch shape** — Switch to CSR: a single flat `int[] edges` + `int[] offsets` for the forward graph, populated row-by-row from the DuckDB result set; same shape for the inverted graph in `#invert`. `loadSuccessors` writes ranges into a shared buffer instead of returning a fresh `int[]` each call; `#invert` does a two-pass count-then-fill straight into the inverted CSR arrays.

**Design cost** — *real trade-off, present both options*. CSR is a different data model (range-into-flat-array vs. `int[]` per node) — well-known and standard for static graphs, but the per-row `int[]` shape is the more OO-natural one. CSR also makes downstream traversal allocation-free in the steady state, which usually wins in a dominator-tree build.

**Estimated saving** — ~21.7 % of bytes.

---

## 2. Primitive collections in HPROF walkers — ~20.7 %

**Sites**
- `HprofPassBWalker#emitInstanceRefs` → `java.lang.Long#valueOf` — **3870 (18.7 %)**
- `HprofStringContentWriter$1.runWorker` → `java.lang.Long#valueOf` — **407 (2.0 %)**

**Root cause** — HPROF object IDs are 8-byte primitives, but `emitInstanceRefs` (and the string-content path) is boxing them into `Long` objects, almost certainly to feed a `List<Long>` / `Set<Long>` / `Map<Long, V>`. Every reference in the dump pays one box.

**Patch shape** — Replace the underlying collections with primitive-specialised variants:
- `List<Long>` → `org.eclipse.collections.api.list.primitive.MutableLongList` (`LongArrayList`)
- `Map<Long, V>` → `MutableLongObjectMap<V>` (`LongObjectHashMap`)
- `Set<Long>` → `MutableLongSet` (`LongHashSet`)

The Eclipse Collections / HPPC / fastutil APIs all mirror the JDK shape; the call sites change minimally and the boxing disappears.

**Design cost** — *small*. Adds one dependency (or reuses one if Eclipse Collections is already on the classpath) and one collection-type rename per call site. No design clarity lost — arguably gained, since the types now say "long id" instead of "object that happens to be a Long".

**Estimated saving** — ~20.7 % of bytes.

---

## 3. Visitor-style HPROF sub-record dispatch — ~18.5 %

**Sites**
- `HprofSubRecordReader#emitInstanceDump` — self **3277 (15.8 %)** → `HprofRecord$InstanceDump`
- `HprofSubRecordReader#emitPrimitiveArrayDump` — self **305 (1.5 %)** → `HprofRecord$PrimitiveArrayDump`
- `HprofSubRecordReader#emitObjectArrayDump` — self **251 (1.2 %)** → `HprofRecord$ObjectArrayDump`

All three flow `…#dispatch` → `HprofPassBWalker$1#onRecord` → `…#dispatch` → walker-side handler. The record is built, dispatched once, then thrown away on every sub-record in the dump.

**Root cause** — Sealed `HprofRecord` hierarchy as the dispatch carrier. Materialising a record per sub-record gives clean `switch (record)` matching at the cost of one object per HPROF instance/array.

**Patch shape** — Two viable directions; pick by taste:
1. **Visitor with primitives** — `HprofSubRecordReader.read(HprofRecordVisitor v)` with `v.onInstanceDump(id, classId, refsBuf, refsLen)`, `v.onPrimitiveArrayDump(id, elemType, data)`, `v.onObjectArrayDump(id, elemClassId, refsBuf, refsLen)`. `HprofPassBWalker$1` becomes the visitor; no record allocations on the hot path.
2. **Thread-local reusable record** — keep the sealed type but `emit*` mutates a `ThreadLocal` instance and dispatches it; the consumer must copy if it wants to retain.

**Design cost** — *real trade-off, present both options*.
- Visitor (option 1) is the structurally cleaner version: each sub-record type gets its own typed entry point, no sealed hierarchy needed for dispatch, and the allocation goes away by construction. Cost: handlers grow from one `switch` to three overrides.
- Reusable record (option 2) keeps the existing sealed-type design and dispatch shape — minimal blast radius — but introduces a mutable singleton with a "don't escape" contract, which is exactly the kind of foot-gun the current sealed-immutable design avoids.

Leaning toward option 1 for long-term design clarity.

**Estimated saving** — ~18.5 % of bytes.

---

## 4. Skip the String round-trip when writing dump strings to DuckDB — ~7.0 %

**Sites**
- `JavaStringDecoder#decodeContent` → `String#<init>` → `Arrays#copyOfRange` — **712 (3.4 %)** + `String [SYNTHETIC] 435 (2.1 %)` = 5.5 %
- `DuckDBAppender#append` → `String#getBytes` → `encodeUTF8` — **734 (3.5 %)**

Both reached from `HprofStringContentWriter#runWorker` → `…#decodeStringForIndex`.

**Root cause** — Bytes are read from the HPROF mmap, decoded into a Java `String` (one `byte[]` + one `String` object), then immediately re-encoded back to UTF-8 (`byte[]`) inside the DuckDB appender for the write. The String never escapes the worker.

**Patch shape** — Two cases:
- If the HPROF source bytes are already UTF-8 (modern dumps), append them directly to the DuckDB `VARCHAR` column via the bytes-accepting overload — skip `String` entirely.
- If HPROF stores modified UTF-8, do a single in-place fix-up on a reused `byte[]` buffer, then append. Still one allocation instead of three.

**Design cost** — *small*. The worker stops materialising a `String` for content it doesn't need; the API stays the same shape.

**Estimated saving** — ~7 % (the `byte[]`-readBytes 3.4 % is structural and stays).

---

## 5. Column-type fix: HUGEINT → BIGINT in StringAnalyzer — ~2.6 %

**Site** — `StringAnalyzer#analyze` → `DuckDBResultSet#getLong` → `DuckDBVector#getLong` → `getObject` → `getHugeint` → `BigInteger` **283 (1.4 %)** + backing `byte[]` **246 (1.2 %)** = 529 samples.

**Root cause** — `getLong` is going through `getHugeint`, which means the DuckDB column for that value is declared `HUGEINT` (128-bit). Returning it constructs a `BigInteger` even though the caller only reads a `long`.

**Patch shape** — Cast / declare the column as `BIGINT` (or `UBIGINT` if it must be unsigned) in the SQL feeding `StringAnalyzer`. `getLong` then takes the direct long-vector path with no boxing.

**Design cost** — *zero*. Schema/SQL change at one query site.

**Estimated saving** — ~2.6 % of bytes.

---

## Smaller items skipped for now

- `DuckDBNative#duckdb_jdbc_fetch boolean[]` — **689 samples (3.3 %)** across two call sites. Driver-owned null-mask staging per chunk; not patchable from your code.
- `HprofMappedFile#readBytes` `byte[]` — **708 samples (3.4 %)**. Structural mmap slab read; reusable only if `readBytes` becomes a buffer-fill method, which is a large API change for a single-digit win.
- `HashMap$Node` under `appendInstanceFromPrimitiveArray` — **226 samples (1.1 %)**. Borderline noise floor; the parent `HashMap#put` has `+pruned 188` below it, so the real footprint is mixed.
- `HprofIndex#lambda$measureSql$0` → `HashMap#putAll`/`putMapEntries`/`putVal` — **398 samples (1.9 %)** with `+pruned 398` underneath. Probably a single resize/rehash from a one-shot index-merge; sizing the destination map up front would close it, but it's one initialisation event, not a steady-state cost.
- Spring bean-factory paths under `JarLauncher#main` — **~5 %** of the profile. Startup only; not user-facing.
- `Arrays.copyOfRange` inside `String.<init>` — unavoidable structural copy from `String`'s contract.

---

## Aggregate

Items #1–#5 together cover **~70 %** of TLAB bytes. Remaining ~30 % is driver internals, structural reads, and one-shot startup costs.

## Open question

Start with #1 (biggest single win, ~21.7 %, but a real data-model change in `DominatorTreeBuilder`), or sequence #2 → #3 → #1 to land easier wins first?
``
