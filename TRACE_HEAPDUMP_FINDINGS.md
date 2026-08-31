# Heap-dump init trace — findings (2026-08-31)

What one exported `heap-dump-init` trace says about both the heap-dump pipeline and the tracing
feature that recorded it. Source: a 259.7s / 673-span trace of `heap-dump-init`, its AI markdown
export, and the trace modal's own waterfall for the half the export truncated.

Every timing below is from that one trace. A trace is one sample of one request; none of these
numbers establish that anything is *systematically* slow, only that it was slow here.

---

## Corrections to the first reading of this trace

Recorded because both were stated before the evidence supported them.

1. **The bulk-load index cost was estimated at ~15s. It is ~75s.** The first pass saw only
   `bulk_load_instance` and `bulk_load_string_content`, because the export truncated the stage that
   carries the other two. See §1.
2. **The 553 `microscope.jar` reads were called class loading. That is not established.** All 553
   are *exactly* 10240 bytes, which is the fixed-buffer shape of resource or native-library
   extraction, not the varying sizes of class reads. Measuring a JVM with `jdk.FileRead` at 0ms and
   stack traces on gave 2534 of 2628 jar reads to `DuckDBNative.unpackAndLoad` — a library
   unpacking its own `.so` — and only ~68 to a real classloader. §4 is what will settle which these
   are.

---

## 1. Bulk loads run against indexed tables — ~75s, 29% of the trace

| load | table | indexes maintained during load | time |
|---|---|---|---|
| `bulk_load_retained_size` | `retained_size` | `instance_id` PK | **28.8s** |
| `bulk_load_dominator` | `dominator` | `instance_id` PK **+ `idx_dominator_parent`** | **27.7s** |
| `bulk_load_instance` | `instance` | `instance_id` PK | **13.6s** |
| `bulk_load_string_content` | `string_content` | `instance_id` PK | **4.6s** |
| `bulk_load_outbound_ref` | `outbound_ref` | none | 1.4s |
| `bulk_load_gc_root` | `gc_root` | none | 0.2s |

The split is exact: every table with a primary key is slow, every table without one is fast.
`outbound_ref` has more rows than `instance` and loads 10× faster.

`HprofNonPkIndexes` exists precisely to avoid this — it drops every non-PK index before the bulk
writes so per-row inserts skip ART-tree updates, then recreates them in bulk. It cannot help here,
and the first reading of this finding proposed two fixes that the evidence then killed.

**The secondary index is not the cost.** `retained_size` carries *only* a PK and took **28.8s**;
`dominator` carries a PK *plus* `idx_dominator_parent` and took **27.7s**. The table with the extra
index was the faster of the two. Both hold one row per reachable instance, so the row counts should
be near-identical and this is close to a controlled comparison. Adding `idx_dominator_parent` to
`DROP_DDL` and dropping it around the load — the "tractable half" — would therefore buy roughly
nothing.

**The PK cannot be dropped at all.** Measured against DuckDB 1.5.3.0:

```
DROP INDEX idx_dominator_parent           -> OK
ALTER TABLE dominator DROP CONSTRAINT ... -> Not implemented Error:
                                             No support for that ALTER TABLE option yet!
```

So this is not a drop-and-recreate that trades a guarantee for time. The only route is to remove the
`PRIMARY KEY` from the schema outright and add a unique index after the load — giving up uniqueness
enforcement *during* it, in the phase most likely to be interrupted, and only if DuckDB's
`CREATE UNIQUE INDEX` performs better than PK maintenance, which nothing here has measured.

Status: **closed, not fixed.** The cheap half buys nothing and the valuable half is not implementable
as scoped. Reopening it needs a real heap dump to measure against, not this trace: everything above
is one sample, and the PK/non-PK correlation across six loads is suggestive rather than a controlled
experiment. Worth revisiting if DuckDB gains `ALTER TABLE ... DROP CONSTRAINT`.

## 2. `create_indexes` is a black box — 50.5s, 19% of the trace

50.5s of self time with zero child spans. `HprofNonPkIndexes.createAll`'s parallel path opens raw
`DriverManager` connections and calls `Statement.execute` directly, bypassing
`HeapDumpDatabaseClient.execute`, so no `JdbcExecuteEvent` is emitted. `drop_indexes` immediately
above it goes through the client and shows all 8 statements as CLIENT spans — the cheapest phase is
fully instrumented and the most expensive one is not, so there is no way to tell which of the 8
indexes cost the time.

The 5 table-groups are also unbalanced: `outbound_ref` gets two sequential `CREATE INDEX` over the
largest table while `stack_trace_frame` gets one over a tiny one, so wall clock is roughly the
`outbound_ref` group alone and four workers finish early.

Minor, same file: `CREATE_DDL_BY_TABLE` was documented as "Iteration order is preserved via
`LinkedHashMap`" but assigned `Map.copyOf(m)`, whose iteration order is unspecified. Harmless — the
groups run in parallel — but the comment claimed something the code did not do.

Status: **fixed.** Both worker paths now issue their DDL through `HeapDumpDatabaseClient.execute`,
so each `CREATE INDEX` emits a `JdbcExecuteEvent`, and each table-group's task is wrapped with
`Tracer.fork` so those events land under the phase's span. `CREATE_DDL_BY_TABLE` became an ordered
`List<IndexGroup>`, which makes the ordering claim true by construction and supplies the table name
each worker span is called after (`create_indexes_outbound_ref`, …).

The measurement behind it is worth keeping, because the obvious half of the fix is not the one that
mattered. Routing through the client alone *does* emit all eight events — but with
`traceId=0, spanId=0`, because a span lives in a `ScopedValue` and a plain executor does not inherit
one. The derivation drops an untraced event, so the phase would have stayed a single bar while
committing eight events nobody could see. `HprofNonPkIndexesTest.nestsEveryWorkerUnderThePhaseSpan`
pins exactly that, via `SpansAssert.hasNoUntracedSpans()`, and fails on the traceId=0 shape.

The same pattern was then applied to the other two fanned-out phases, `walk_pass_b` and
`write_string_content`, via `Tracer.forkCallable` — the `Callable` form that fits
`executor.submit` directly. Their workers now open a `walk_pass_b_worker` /
`write_string_content_worker` span each, which is another 66s of the index build that used to read
as coordinator self time. Every worker of a phase shares one span name deliberately: they are
instances of one operation, and a name carrying a worker index would put one string per worker into
JFR's per-chunk pool, on a count that varies with the machine's CPUs.
`HprofIndexTracingTest` runs a whole synthetic index build and pins both properties.

This is also as far as §3 can be taken from inside Jeffrey — see there.

## 3. A virtual thread's park is attributed to its carrier

The ranking reads `OWN_WORK 258.6s (100%)` with no `PARKED` anywhere, despite `jdk.ThreadPark`
being enabled at 1ms. But three phases — `walk_pass_b`, `write_string_content`, `create_indexes`,
together 116.5s or 91% of the index build — fan out to `Executors.newVirtualThreadPerTaskExecutor()`
and the coordinator blocks in `close()`.

Measured, with a coordinator doing exactly that fan-out:

```
coordinator = platform thread -> jdk.ThreadPark  eventThread = "platform-coordinator"     601 ms
coordinator = virtual thread  -> jdk.ThreadPark  eventThread = "ForkJoinPool-1-worker-1"  298 ms
                                                 eventThread = "ForkJoinPool-1-worker-2"  302 ms
```

A virtual thread unmounts when it parks, so the park lands on the carrier. Span attribution is by
thread and no span is open on a carrier, so the wait is recorded and then dropped.

This is not specific to heap dumps: the pipeline runs on `Schedulers.sharedVirtual()`, so **every**
Jeffrey pipeline on that scheduler reads as 100% `OWN_WORK`.

Status: **closed as not fixable here**, with a partial substitute already in place.

Correlating a carrier's park back to the virtual thread mounted on it would be wrong more often than
right: when a virtual thread parks it *unmounts*, and the carrier then parks waiting for **other**
work. Those are two different waits, and attributing the carrier's idle to the virtual thread would
invent a wait the virtual thread did not have. The virtual thread's own wait is simply not recorded
as `jdk.ThreadPark` — that is a JFR limitation, not something the derivation can repair.

What *can* be done, and now is: instrument the fan-out so the workers the coordinator was waiting for
are visible as spans (§2). The coordinator's time still reads as own work — self time only subtracts
same-thread children — but a reader can now see what it was waiting on, which is the question the
missing `PARKED` was being asked to answer.

Reopen only if JFR gains a mount-aware park event.

## 4. The span cap truncates the half of the trace that matters

273 of 673 spans were omitted from the export, and the omitted half was everything after the index
build — the ten analysis stages, ~131s, just over half the trace's wall clock. What consumed the
budget: 553 promoted `jdk.FileRead` leaves against `microscope.jar` worth **3ms in total**, 82% of
the spans for 0.001% of the time.

Status: **in progress.** Classifying class-loading file I/O and giving the waterfall an overlay
switch for it is the first step; the AI export should then be able to leave that family out by
default. Whether it actually catches these 553 reads is the open question — see the second
correction above.

## 5. `File write <unknown>` double-counts `jeffrey.log`

```
File write .../logs/jeffrey.log — 12 ops · 2598 B, mean 216 B/op · time 827us
File write <unknown>            — 12 ops · 2598 B, mean 216 B/op · time 129us
```

Identical op count and identical byte total, different durations: the same 12 log writes recorded at
two layers, one of which carries no `path`. It inflates the section's 606-operation total by 12, and
the I/O summary has no way to tell that one target is another target's shadow.

Coincidence is not a plausible reading here — this recording ran with `TRACING_IO_THRESHOLD=0ms` and
the throttle lifted, so every operation was recorded and the byte totals are complete rather than
sampled.

Status: **open.** Same class of bug as the `Error` triple that `DERIVE_TRACE_EXCEPTIONS` now
collapses: JFR emitting more than one event per logical operation.

---

## Two framings this trace invites and does not support

**`FILE_IO 7ms (<0.1%)` does not mean the pipeline is not I/O bound.** The `.hprof` is read through
`FileChannel.map`, so page faults, not `read()` — no `jdk.FileRead` at all. The index DB is written
by DuckDB's native layer, so no Java file events either. The run's two heaviest I/O consumers are
invisible by construction. Because the threshold was 0ms, the 606 recorded operations really are
the complete Java-level file I/O — which is what makes the absence meaningful rather than sampling.

**GC is not a lever here.** `SAFEPOINT` 556ms + `GC_PAUSE` 547ms over 259.7s is 0.21%, largest pause
178ms, for a workload building multi-million-element primitive arrays. The CSR/primitive-array
design is doing its job. The `PrintThreads` / `FindDeadlocks` safepoint pairs are JFR's own periodic
`jdk.ThreadDump` at ~60s cadence, not application code.
