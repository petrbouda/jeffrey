# Additional JVM Artifacts for Jeffrey-CLI

This document outlines additional files and diagnostics from HotSpot JVM / OpenJDK that could be collected by Jeffrey-CLI to enhance investigation capabilities.

## Currently Collected

- JFR recordings (alloc, lock, CPU events)
- Heap dumps (on OOM)
- Perf counters (hsperfdata)
- JVM error logs (hs-jvm-err.log)
- Custom JVM logging

---

## Additional Artifacts to Consider

### 1. Native Memory Tracking (NMT)

```
-XX:NativeMemoryTracking=detail
```

Then capture via `jcmd <pid> VM.native_memory detail`. Extremely useful for tracking native memory leaks, JNI allocations, and understanding where memory goes outside the Java heap.

### 2. GC Logs (separate from JFR)

```
-Xlog:gc*,gc+heap=debug,gc+phases=debug:file=${CURRENT_SESSION}/gc.log::filecount=5,filesize=50m
```

While JFR captures GC events, detailed GC logs can provide more granular timing, cause analysis, and are easier to parse with existing tools (GCViewer, GCEasy).

### 3. Safepoint Logs

```
-Xlog:safepoint*=debug:file=${CURRENT_SESSION}/safepoint.log
```

Critical for diagnosing JVM pauses not caused by GC - time-to-safepoint issues, biased locking revocation, etc.

### 4. JIT Compilation Logs

```
-XX:+LogCompilation -XX:LogFile=${CURRENT_SESSION}/hotspot_compilation.log
```

XML output of JIT compiler decisions - useful for understanding deoptimizations, inlining decisions, and performance investigations.

### 5. Class Loading/Unloading Logs

```
-Xlog:class+load=info,class+unload=info:file=${CURRENT_SESSION}/classloading.log
```

Helpful for classloader leak detection and understanding startup behavior.

### 6. Code Cache Statistics

```
-Xlog:codecache*=debug:file=${CURRENT_SESSION}/codecache.log
```

Tracks JIT compiled code memory usage - important for long-running applications.

### 7. Thread Dumps on Exit

Could capture final thread dump before shutdown using a shutdown hook or JVMTI agent.

### 8. VM Flags Snapshot

Capture actual resolved JVM flags at startup:

```
-XX:+PrintFlagsFinal > ${CURRENT_SESSION}/vm-flags.txt
```

Or via `jcmd <pid> VM.flags -all`

### 9. System Properties Capture

Snapshot of `System.getProperties()` at startup for reproducibility.

### 10. Metaspace Statistics

```
-Xlog:metaspace*=debug:file=${CURRENT_SESSION}/metaspace.log
```

Important for applications with many classes or heavy use of reflection/proxies.

### 11. Deoptimization Events

```
-Xlog:deoptimization*=debug:file=${CURRENT_SESSION}/deopt.log
```

Tracks when JIT-compiled code is invalidated - can explain sudden performance drops.

### 12. Core Dump on Crash

```
-XX:+CreateCoredumpOnCrash
```

In addition to hs-jvm-err.log, a core dump allows post-mortem debugging with gdb/lldb.

### 13. TLAB Statistics

```
-Xlog:gc+tlab=trace:file=${CURRENT_SESSION}/tlab.log
```

Thread-local allocation buffer stats for understanding allocation patterns.

### 14. Biased Locking / Lock Statistics

```
-Xlog:biasedlocking*=debug:file=${CURRENT_SESSION}/biasedlocking.log
```

Note: Biased locking is deprecated in JDK 15+ and disabled by default in JDK 18+.

### 15. Container/cgroup Info (for containerized apps)

Capture `/proc/self/cgroup`, `/sys/fs/cgroup/` metrics to understand resource limits.

---

## On-Demand Captures (via jcmd)

These could be triggered via a companion script or integrated into Jeffrey-CLI:

| Command | Artifact |
|---------|----------|
| `jcmd <pid> VM.native_memory detail` | Native memory breakdown |
| `jcmd <pid> GC.class_histogram` | Class memory histogram |
| `jcmd <pid> Thread.print` | Thread dump |
| `jcmd <pid> VM.classloader_stats` | Classloader statistics |
| `jcmd <pid> Compiler.queue` | Pending JIT compilations |
| `jcmd <pid> VM.stringtable` | String table statistics |
| `jcmd <pid> VM.symboltable` | Symbol table statistics |
| `jcmd <pid> GC.heap_info` | Heap regions information |
| `jcmd <pid> VM.metaspace` | Metaspace statistics |
| `jcmd <pid> VM.system_properties` | System properties dump |

---

## Implementation Suggestions

These artifacts could be added as new `--enable-*` flags following the existing pattern (similar to `--enable-perf-counters`):

- `--enable-gc-logging` - Enable detailed GC logs
- `--enable-safepoint-logging` - Enable safepoint diagnostics
- `--enable-compilation-logging` - Enable JIT compilation logs
- `--enable-classloading-logging` - Enable class load/unload tracking
- `--enable-nmt` - Enable Native Memory Tracking
- `--enable-core-dump` - Enable core dump on crash

Alternatively, a single `--enable-diagnostics` flag could enable a curated set of commonly useful diagnostics.