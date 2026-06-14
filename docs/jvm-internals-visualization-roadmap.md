# JVM Internals — Visualization Roadmap & JFR Event Gap Analysis

> **Purpose.** A deep analysis of every page currently under **JVM Internals** in the
> ProfileDetail page, cross-referenced against the full JFR event catalog in **JDK 26**
> ([sap.github.io/jfrevents/26.html](https://sap.github.io/jfrevents/26.html)). It identifies
> which event types are already visualized, which interesting ones are **not yet covered**, and
> proposes new visualizations that help **diagnose and solve real performance problems**.
>
> This is a roadmap document only — no feature code is implemented here. Priorities will be
> chosen later.

---

## 1. Current JVM Internals coverage (baseline)

Reference points in the codebase:

- **Routes:** `jeffrey-microscope/pages-microscope/src/router/index.ts` (`profileChildRoutes`)
- **Sidebar / page grouping:** `jeffrey-microscope/pages-microscope/src/views/profiles/ProfileDetail.vue`
- **Backend managers:** `jeffrey-microscope/profiles/profile-management/.../manager/*ManagerImpl.java`
- **Event-type source of truth:** `shared/common/.../model/Type.java` + `EventTypeName.java`

The current pages already cover the "classic" JFR surface very thoroughly:

| Area | Pages / tabs | Key JFR events consumed |
|---|---|---|
| Garbage Collection | Overview, Timeseries, Configuration, Tenuring, IHOP | `jdk.GarbageCollection`, `jdk.GCConfiguration`, `jdk.GCHeapSummary`, `jdk.G1HeapSummary`, `jdk.TenuringDistribution`, `jdk.G1AdaptiveIHOP`, `jdk.G1MMU` |
| JIT Compiler | Compilation activity, Long compilations, Code cache, Deoptimizations | `jdk.Compilation`, `jdk.CompilerStatistics`, `jdk.CompilerQueueUtilization`, `jdk.CodeCacheStatistics`, `jdk.CodeCacheFull`, `jdk.Deoptimization` |
| Class Loading | Timeline, Class loaders, Loads, Redefinitions | `jdk.ClassLoad`, `jdk.ClassDefine`, `jdk.ClassUnload`, `jdk.ClassRedefinition`, `jdk.RetransformClasses`, `jdk.ClassLoaderStatistics`, `jdk.ClassLoadingStatistics` |
| VM Operations / Safepoints | Operations, Pauses timeline, Time-to-safepoint | `jdk.ExecuteVMOperation`, `jdk.SafepointBegin`, `jdk.SafepointStateSynchronization`, `jdk.SafepointEnd` |
| Threads | Statistics, Timeline, CPU load | `jdk.ExecutionSample`, `jdk.ThreadStart/End/Park/Sleep`, `jdk.ThreadCPULoad`, `jdk.JavaThreadStatistics`, `jdk.CPULoad`, `jdk.ThreadContextSwitchRate` |
| Heap Memory | Before/After GC, Allocation | `jdk.GCHeapSummary`, `jdk.ObjectAllocationSample` |
| Allocations | Rate, Types | `jdk.ObjectAllocationSample`, `jdk.ObjectAllocationInNewTLAB`, `jdk.ObjectAllocationOutsideTLAB` |
| Leak Candidates | Candidates | `jdk.OldObjectSample` |
| Blocking Operations | Monitors, Waits, Parks, Sleeps, Pinned | `jdk.JavaMonitorEnter`, `jdk.JavaMonitorWait`, `jdk.JavaMonitorInflate`, `jdk.ThreadPark`, `jdk.ThreadSleep`, `jdk.VirtualThreadPinned` |
| Native Memory | RSS, Direct buffers, Libraries | `jdk.ResidentSetSize`, `jdk.PhysicalMemory`, `jdk.DirectBufferStatistics`, `jdk.NativeLibrary` |
| Native Memory Tracking (NMT) | Categories, Totals, RSS vs tracked | `jdk.NativeMemoryUsage`, `jdk.NativeMemoryUsageTotal` |
| Socket I/O / File I/O | Throughput, peers/files | `jdk.SocketRead/Write`, `jdk.FileRead/Write` |
| Exceptions | Rate, Top types | `jdk.JavaExceptionThrow`, `jdk.JavaErrorThrow`, `jdk.ExceptionStatistics` |
| System & Host | CPU, Network, Context switches, Processes | `jdk.CPUInformation`, `jdk.OSInformation`, `jdk.NetworkUtilization`, `jdk.ThreadContextSwitchRate`, `jdk.SystemProcess` |
| Container | Configuration | `jdk.ContainerConfiguration`, `jdk.ContainerCPUThrottling`, `jdk.ContainerMemoryUsage`, `jdk.ContainerIOUsage` |
| Misc | Performance Counters, JVM Flags, Event Types, Event Viewer | `jdk.InitialSystemProperty`, performance counters, all enabled event types |

**Takeaway:** the breadth is impressive. The gaps below are mostly *modern* JFR events
(ZGC generational, virtual threads, CPU-time sampling, security/TLS) and a handful of
high-signal *GC pathology* events that have outsized diagnostic value.

---

## 2. Gap analysis — JDK 26 events present but not visualized

### Tier 1 — direct performance-pathology signals (highest value)

- **`jdk.ZAllocationStall`** (+ `jdk.ZPageAllocation`, `jdk.ZRelocationSet`, `jdk.ZUncommit`,
  and ZGC young/old phase detail) — application threads **stalled waiting for memory** under ZGC.
  ZGC hides pauses from ordinary GC views; allocation stalls are the *one* place app-visible latency
  surfaces. This is the single highest-value low-latency signal and is **entirely missing**.
- **`jdk.SystemGC`** — explicit `System.gc()` invocations, almost always a latent bug or misbehaving
  library. Cheap to surface, very high "aha" value.
- **`jdk.EvacuationFailed` / `jdk.EvacuationInformation`** — G1 to-space exhaustion, the usual cause of
  *surprise* Full GCs and pause spikes.
- **`jdk.GCLocker`** — JNI critical sections delaying GC.
- **`jdk.GCReferenceStatistics`** — Soft/Weak/Phantom/Final reference-processing counts and time per GC.
  The `Type` constant already exists but is not surfaced in any view.

### Tier 2 — modern workloads & footprint

- **Virtual Threads** — `jdk.VirtualThreadStart`, `jdk.VirtualThreadEnd`,
  `jdk.VirtualThreadSubmitFailed` (plus the already-captured `jdk.VirtualThreadPinned`).
  Carrier-pool saturation and pinning sites. Loom is mainstream; pinning is only surfaced inside
  "Blocking" today, with no dedicated dashboard.
- **`jdk.CPUTimeSample`** (JDK 25+ CPU-time profiler) — true CPU-time flamegraphs, immune to the
  wall-clock sampling bias of `jdk.ExecutionSample`.
- **`jdk.StringTableStatistics`, `jdk.SymbolTableStatistics`, `jdk.StringDeduplication(Statistics)`** —
  interned-string / symbol-table bloat and dedup effectiveness.
- **`jdk.FinalizerStatistics`** — finalizer-queue backlog, a classic leak / shutdown-stall source.
- **Security / TLS** — `jdk.TLSHandshake`, `jdk.X509Certificate`, `jdk.X509Validation`,
  `jdk.SecurityProperty`, `jdk.SecurityProviderService`, `jdk.Deserialization`.
- **`jdk.NativeLibraryLoad` / `jdk.NativeLibraryUnload`** (JDK 24+) — load *timeline* with timing;
  today native libraries are shown as a static list only.

### Tier 3 — niche / nice-to-have

- `jdk.FileForce` (fsync latency), `jdk.SocketReceive` / `jdk.SocketSend` (UDP),
  `jdk.ReservedStackActivation` (stack-overflow near-miss), `jdk.GCPhaseParallel` breakdown,
  `jdk.ThreadDump`, `jdk.ModuleRequire` / `jdk.ModuleExport`,
  `jdk.SwapSpace` / `jdk.ProcessStart`.

> **Availability caveat.** A few of the events above (`SwapSpace`, `ProcessStart`, `CPUTimeSample`,
> `NativeLibraryLoad/Unload`, the ZGC family) are version- and GC-sensitive. Each feature must verify
> the event's presence against the profile's `event-types` (already exposed via
> `/api/internal/profiles/{profileId}/information`) and **degrade gracefully** with an `EmptyState`
> when the event is absent from the recording.

---

## 3. Detailed feature proposals

For each: **JFR events → backend → frontend → visualization → why it helps**. Backend follows the
existing `*Manager` / `*ManagerImpl` + controller pattern; frontend reuses `TimeSeriesChart`,
`DataTable`, `StatsTable`, `EmptyState`, and the three-state async pattern.

### 3.1 ZGC Allocation Stalls / Low-Latency GC panel
- **Events:** `jdk.ZAllocationStall`, `jdk.ZPageAllocation`, `jdk.ZRelocationSet`, `jdk.ZUncommit`.
- **Visualization:** stall count and total/max stall-duration timeseries; a table/flamegraph of
  stalling stack traces; page-allocation latency. Suggested route `/garbage-collection/allocation-stalls`.
- **Why:** pinpoints app-visible pauses that ZGC otherwise hides — the key diagnostic for low-latency
  deployments.

### 3.2 GC Anomalies tab *(augment existing GC pages)*
- **Events:** `jdk.SystemGC`, `jdk.EvacuationFailed`, `jdk.EvacuationInformation`, `jdk.GCLocker`.
- **Visualization:** annotated markers on the existing GC timeseries + an anomalies table
  (timestamp, cause, and stack trace for `System.gc()` callers).
- **Why:** explains surprise Full GCs and explicit-GC stalls in one glance.

### 3.3 Reference Processing
- **Event:** `jdk.GCReferenceStatistics`.
- **Visualization:** stacked area of Soft/Weak/Phantom/Final counts + processing time per GC cycle.
- **Why:** reference/finalizer backlog as an under-appreciated pause contributor.

### 3.4 Virtual Threads dashboard *(new page)*
- **Events:** `jdk.VirtualThreadStart`, `jdk.VirtualThreadEnd`, `jdk.VirtualThreadSubmitFailed`,
  `jdk.VirtualThreadPinned`.
- **Visualization:** live virtual-thread count, carrier-pool saturation, submit-failed timeline,
  and a pinning-site timeline with stack traces. Suggested route `/virtual-threads`.
- **Why:** surfaces the top Loom footgun — carrier pinning — and pool exhaustion.

### 3.5 CPU-Time Flamegraph *(JDK 25+)*
- **Event:** `jdk.CPUTimeSample`.
- **Visualization:** reuse the existing flamegraph pipeline with a CPU-time source toggle alongside
  `jdk.ExecutionSample`.
- **Why:** accurate on-CPU profiling, immune to wall-clock sampling bias.

### 3.6 String / Symbol Table footprint
- **Events:** `jdk.StringTableStatistics`, `jdk.SymbolTableStatistics`, `jdk.StringDeduplication(Statistics)`.
- **Visualization:** table size / entry-count timeseries; dedup savings over time.
- **Why:** interned-string and symbol-table bloat, and whether string dedup is paying off.

### 3.7 Finalizers
- **Event:** `jdk.FinalizerStatistics`.
- **Visualization:** per-class pending-finalizer counts + queue-length trend.
- **Why:** finalizer-leak and shutdown-stall detection.

### 3.8 Security & TLS *(new page)*
- **Events:** `jdk.TLSHandshake`, `jdk.X509Certificate`, `jdk.X509Validation`,
  `jdk.SecurityProperty`, `jdk.SecurityProviderService`, `jdk.Deserialization`.
- **Visualization:** handshake-latency histogram; protocol/cipher breakdown; certificate expiry list
  flagging weak key sizes / signature algorithms; deserialization payload-size distribution + filter status.
- **Why:** combined security posture and handshake/deserialization latency view.

### 3.9 Native Library load timeline *(augment Native Memory page)*
- **Events:** `jdk.NativeLibraryLoad`, `jdk.NativeLibraryUnload`.
- **Visualization:** load/unload timeline with durations layered onto the current static library list.

### 3.10 Tier-3 quick wins
- `jdk.FileForce` latency column on the File I/O page.
- `jdk.ReservedStackActivation` markers (stack-overflow near-misses).
- `jdk.GCPhaseParallel` drill-down on GC pauses.
- `jdk.ThreadDump` viewer.

---

## 4. Cross-cutting visualization ideas (highest leverage)

1. **Unified "Stop-The-World" timeline.** A single shared time axis overlaying GC pauses + safepoints +
   VM operations + ZGC allocation stalls + long compilations, with a cumulative **"app-stop budget"**
   band. Jeffrey currently has all these as *separate* pages; correlating them on one axis makes a
   latency spike's root cause visually obvious. This is the flagship idea.
2. **Latency-outlier correlation drawer.** Click any spike on any timeseries → a side panel lists every
   concurrent STW / GC / stall / compilation event in that window.
3. **Carrier-thread pinning timeline** (from §3.4) with pinning-site stacks.
4. **Reference-processing stacked area** (from §3.3).

---

## 5. Implementation notes (patterns to reuse)

**Backend**
- Add the `Type` constant in `shared/common/.../model/Type.java` and the name in `EventTypeName.java`.
- Add a `*Manager` / `*ManagerImpl` in `jeffrey-microscope/profiles/profile-management/.../manager/`.
- Expose via a controller following the existing `@RestController` +
  `@RequestMapping("/api/internal/profiles/{profileId}/...")` convention.
- Register manager beans via `@Bean` in a `@Configuration` class — no stereotype annotations, no `@Autowired`.

**Frontend**
- Add a `BaseProfileClient` subclass for the new endpoint(s).
- Add a view under `jeffrey-microscope/pages-microscope/src/views/profiles/`, a route in
  `src/router/index.ts` (`profileChildRoutes`), and a sidebar entry in `ProfileDetail.vue`.
- Reuse `TimeSeriesChart`, `DataTable`, `SortableTableHeader`, `StatsTable`,
  `LoadingState` / `ErrorState` / `EmptyState`, `Badge`, and design tokens. The three-state async
  pattern is mandatory.

**General**
- Every feature must degrade gracefully (`EmptyState`) when its events are absent from the recording.
- Each shipped feature updates the JVM-internals docs under `jeffrey-pages/src/views/docs/profiles/`.

---

## 6. Suggested priority order

1. **Tier 1** — ZGC allocation stalls, GC anomalies (SystemGC / Evacuation / GCLocker), reference processing.
2. **Virtual Threads dashboard.**
3. **CPU-Time flamegraph.**
4. **String/Symbol tables + Finalizers.**
5. **Security / TLS.**
6. **Tier-3 quick wins** + the **Unified Stop-The-World timeline** as a flagship cross-cutting view.
