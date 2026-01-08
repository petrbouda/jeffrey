# OpenJDK Heap Dump Compression Guide

## Overview

Heap dumps compress exceptionally well due to their repetitive structure (object references, repeated class metadata, string pools). Compression ratios of 3-15x are typical, making compression essential for large heaps in containerized environments.

---

## Compression Efficiency

| Algorithm | Compression Ratio | Speed | Notes |
|-----------|-------------------|-------|-------|
| gzip (JVM native) | 3-5x | Fast (parallel) | Built into JVM since JDK 15/17 |
| zstd | 10-15x | Fast | External tool, best overall |
| xz/lzma | 12-20x | Slow | Best ratio for archival |
| lz4 | 5-8x | Very Fast | Low CPU overhead |

**Recommendation:** Use JVM's native gzip compression (`-XX:HeapDumpGzipLevel=1`) for simplicity and tool compatibility. Eclipse MAT, VisualVM, and other analyzers read `.hprof.gz` directly.

---

## JVM Command-Line Options

### Core Heap Dump Flags

| Flag | JDK | Description |
|------|-----|-------------|
| `-XX:+HeapDumpOnOutOfMemoryError` | 6+ | Generate heap dump on OOM |
| `-XX:HeapDumpPath=<path>` | 6+ | Directory or file path for dumps |
| `-XX:HeapDumpGzipLevel=<0-9>` | **17+** | Compression level (0=off, 1=fast, 9=max) |
| `-XX:+HeapDumpBeforeFullGC` | 6+ | Dump before each Full GC |
| `-XX:+HeapDumpAfterFullGC` | 6+ | Dump after each Full GC |
| `-XX:FullGCHeapDumpLimit=<n>` | **23+** | Limit GC-triggered dump count (0=unlimited) |

### Recommended Production Configuration

```bash
java -XX:+HeapDumpOnOutOfMemoryError \
     -XX:HeapDumpPath=/var/dumps/ \
     -XX:HeapDumpGzipLevel=1 \
     -Xmx4g \
     -jar myapp.jar
```

### Configuration with Full GC Dumps (Debug)

```bash
java -XX:+HeapDumpOnOutOfMemoryError \
     -XX:+HeapDumpBeforeFullGC \
     -XX:HeapDumpPath=/var/dumps/ \
     -XX:HeapDumpGzipLevel=1 \
     -XX:FullGCHeapDumpLimit=2 \
     -jar myapp.jar
```

---

## JDK Version Evolution

| JDK Version | Feature |
|-------------|---------|
| JDK 6 | `-XX:+HeapDumpOnOutOfMemoryError` introduced |
| JDK 15 | `jcmd GC.heap_dump -gz=N` (parallel gzip compression) |
| JDK 17 | **`-XX:HeapDumpGzipLevel=N`** for OOM/FullGC triggers |
| JDK 23 | `-XX:FullGCHeapDumpLimit=N` to limit dump count |
| JDK 25 | Multi-threaded heap dump with parallel compression |

### JDK 25 Enhancements

JDK 25 introduces parallel heap dumping — both the dump process and compression run in multiple threads automatically.

**Key points:**
- **Enabled by default** — no explicit flag needed
- VM automatically determines optimal thread count
- Each parallel thread uses ~20MB for compression buffer
- Supports `%p` placeholder in `HeapDumpPath` for PID

```bash
# JDK 25+ with PID placeholder
java -XX:+HeapDumpOnOutOfMemoryError \
     -XX:HeapDumpPath=/var/dumps/heap_%p.hprof.gz \
     -XX:HeapDumpGzipLevel=1 \
     -jar myapp.jar
```

---

## Naming Conventions

### JVM Default Pattern

```
java_pid<PID>.hprof
java_pid<PID>.hprof.gz    # when compression enabled
```

### Recommended Production Pattern

Include context for later identification:

```
<app>_<host>_<timestamp>_<pid>_<trigger>.hprof.gz
```

**Examples:**
```
myapp_prod-web-01_20260107-143052_12587_oom.hprof.gz
myapp_prod-web-01_20260107-150000_12587_beforegc.hprof.gz
```

### HeapDumpPath Behavior

| Configuration | Result |
|---------------|--------|
| `-XX:HeapDumpPath=/var/dumps/` | `/var/dumps/java_pid12587.hprof.gz` |
| `-XX:HeapDumpPath=/var/dumps/heap.hprof` | `/var/dumps/heap.hprof.gz` (overwrites!) |
| `-XX:HeapDumpPath=/var/dumps/heap_%p.hprof` | `/var/dumps/heap_12587.hprof.gz` (JDK 25+) |

**Note:** When using a fixed filename, subsequent dumps append sequence numbers: `heap.hprof.gz.1`, `heap.hprof.gz.2`, etc.

---

## Compression Level Guidelines

| Level | Use Case | Trade-off |
|-------|----------|-----------|
| `0` | Disabled | No compression, fastest dump, largest file |
| `1` | **Production (recommended)** | Fast compression, good ratio (~3x) |
| `5` | Balanced | Moderate speed, better ratio |
| `9` | Archival | Slowest, best compression |

**Recommendation:** Always use `-XX:HeapDumpGzipLevel=1` in production. The speed difference between levels is significant, while compression ratio improvement is marginal.

---

## Tool Compatibility

| Tool | `.hprof` | `.hprof.gz` | Notes |
|------|----------|-------------|-------|
| Eclipse MAT | ✅ | ✅ | Full support since MAT 1.12 |
| VisualVM | ✅ | ✅ | Native support |
| jhat | ✅ | ❌ | Deprecated, decompress first |
| YourKit | ✅ | ✅ | Full support |
| HeapHero | ✅ | ✅ | Cloud-based analyzer |

---

## Production Considerations

### Disk Space

Ensure sufficient disk space for the dump. Compressed dumps are typically 20-33% of heap size:

| Heap Size | Uncompressed | Compressed (gz=1) |
|-----------|--------------|-------------------|
| 4 GB | ~4 GB | ~1.0-1.3 GB |
| 8 GB | ~8 GB | ~2.0-2.7 GB |
| 16 GB | ~16 GB | ~4.0-5.3 GB |
| 32 GB | ~32 GB | ~8.0-10.7 GB |

### Performance Impact

- **Dump time:** Depends on heap size and I/O speed
- **Compression overhead:** Minimal with `-gz=1`, parallelized since JDK 15
- **JDK 25+:** Parallel dump significantly reduces total time for large heaps

### OOM Handling Order

When `HeapDumpOnOutOfMemoryError` is enabled:
1. OOM occurs
2. Heap dump is generated (blocks other handlers)
3. `-XX:OnOutOfMemoryError` script executes
4. `-XX:+ExitOnOutOfMemoryError` or `-XX:+CrashOnOutOfMemoryError` triggers

**Warning:** Large heap dumps can delay service deregistration. Consider using `-XX:+ExitOnOutOfMemoryError` with container orchestration for faster recovery.

---

## Quick Reference

### Minimal Production Setup (JDK 17+)

```bash
java -XX:+HeapDumpOnOutOfMemoryError \
     -XX:HeapDumpPath=/var/dumps/ \
     -XX:HeapDumpGzipLevel=1 \
     -jar myapp.jar
```

### Full Production Setup (JDK 25+)

```bash
java -XX:+HeapDumpOnOutOfMemoryError \
     -XX:HeapDumpPath=/var/dumps/heap_%p.hprof.gz \
     -XX:HeapDumpGzipLevel=1 \
     -XX:+ExitOnOutOfMemoryError \
     -XX:ErrorFile=/var/logs/hs_err_%p.log \
     -jar myapp.jar
```

---

## Summary

| Requirement | Recommendation |
|-------------|----------------|
| Compression | `-XX:HeapDumpGzipLevel=1` |
| Location | `-XX:HeapDumpPath=/var/dumps/` (directory) |
| OOM trigger | `-XX:+HeapDumpOnOutOfMemoryError` |
| Parallel dump | Automatic in JDK 25+ (no flag needed) |
| Fast recovery | Add `-XX:+ExitOnOutOfMemoryError` |
