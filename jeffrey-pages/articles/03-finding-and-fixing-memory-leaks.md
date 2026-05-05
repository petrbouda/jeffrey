# Finding and Fixing Memory Leaks in Java: From GC Analysis to Heap Dump Investigation

Your application ran fine for days, then started slowing down. Response times crept up. GC pauses grew longer. Eventually, Kubernetes killed the pod with an OOMKilled status. You restarted it, and the cycle began again.

Memory leaks in Java are insidious. The JVM's garbage collector masks the problem for hours or days until the leak consumes enough heap to degrade performance catastrophically. Finding the root cause requires a systematic approach: first understand the GC behavior, then inspect the heap.

This article — the third in our series on Java profiling with Jeffrey — walks through the complete memory investigation workflow. We'll start with GC analysis from JFR recordings, identify the patterns that signal a leak, then dive into heap dump analysis to find exactly what's holding onto memory and why.

## Recognizing the Symptoms

Before diving into tools, know what to look for in production:

- **Growing GC pause times** — Pauses that were 20ms are now 200ms and climbing
- **Increasing collection frequency** — The GC runs more often as free heap shrinks
- **Rising baseline heap usage** — After each full GC, the heap doesn't drop back to the same level. Each floor is higher than the last
- **OOMKilled or OutOfMemoryError** — The endpoint of an unresolved leak
- **Latency spikes correlating with GC** — Users experience slowdowns that match GC activity

If you see any of these patterns, it's time to profile. Record your application with Async Profiler using `jfrsync=default` (as covered in [Article 1](01-getting-started-with-jeffrey.md)) — this captures GC events, heap metrics, and allocation data in a single JFR file.

## GC Analysis: Your First Stop

Upload the JFR recording to Jeffrey and navigate to the **GC Analysis** dashboard. This is the fastest way to understand your application's garbage collection behavior.

![GC overview with pause statistics and efficiency](images/feature-screenshots/profile_gc.png)
*GC overview showing pause statistics, collection counts, and GC efficiency metrics.*

The dashboard surfaces key metrics at a glance:

- **Pause statistics** — Maximum pause, P95, P99, and average pause duration. If your max pause is in the hundreds of milliseconds or your P99 is climbing, GC is impacting latency.
- **Collection counts** — Total collections broken down by young and old generation. A high ratio of old-generation collections to young-generation collections suggests objects are surviving too long and being promoted unnecessarily.
- **GC efficiency** — Throughput percentage (time spent in application code vs. GC) and overhead percentage. A healthy application should be above 95% throughput. Below 90% means GC is consuming significant compute time.
- **Memory freed** — Total and average bytes freed per collection. If old-generation collections are freeing very little memory, objects are being retained — a strong leak signal.

## GC Timeseries: Spotting the Pattern

Static metrics tell you the summary. Timeseries show you the story over time. Jeffrey provides three GC timeseries views, each revealing different aspects of collector behavior:

![GC timeseries showing collection activity over time](images/feature-screenshots/profile_gc_2.png)
*GC timeseries with collection count, max pause, and sum of pauses per second.*

- **Collection Count** — Number of GC events per second, split by generation. A steady increase over time means the collector is working harder as available heap shrinks.
- **Max Pause** — Longest single pause within each second. Isolated spikes are normal; a rising trend is not.
- **Sum of Pauses** — Total pause time per second. This directly measures the GC tax on your application. If this grows from 50ms/sec to 300ms/sec over an hour, you have a problem.

**The leak pattern in timeseries:** Young GC frequency increases steadily. Old GC events appear more frequently. Pause times grow. The application alternates between brief periods of normal operation and increasingly long GC pauses. Eventually, the GC spends more time collecting than the application spends running.

## Heap Memory Trends

The **Heap Memory** view shows two complementary timeseries:

![Heap memory before/after GC — the sawtooth pattern](images/feature-screenshots/profile_heap_memory.png)
*Heap usage measured before and after each GC event, showing the characteristic sawtooth pattern.*

**Heap Before/After GC** — This is the most important chart for leak detection. In a healthy application, the post-GC heap returns to roughly the same baseline after each collection — a clean sawtooth with a flat floor. In a leaking application, the post-GC floor rises over time. Each GC reclaims less and less memory because more objects are reachable and cannot be collected.

**Allocation Rate** — Shows how fast objects are being created between GC events. A stable allocation rate with a rising heap floor confirms that the problem is retention (leak), not excessive creation (allocation pressure).

If the heap trends confirm a leak, it's time to capture a heap dump and find out what's being retained.

## Heap Dump Analysis in Jeffrey

Capture a heap dump from your running application (using `jcmd <pid> GC.heap_dump /path/to/dump.hprof` or your orchestration tooling) and upload it to Jeffrey. Jeffrey initializes the dump by building indexes for efficient querying — this takes a few seconds for most dumps.

![Heap dump summary dashboard](images/release-notes/heap-dump/02-overview.png)
*Heap dump overview showing total memory, instance count, class count, and GC root count.*

The overview gives you the big picture: total heap size, number of object instances, number of loaded classes, and GC root count. From here, you have several investigation paths.

## Class Histogram: What's Consuming Memory?

The class histogram is the simplest and often most revealing view. It lists every class in the heap with its total instance count and total memory consumption.

![Class histogram sorted by size](images/release-notes/heap-dump/03-class-histogram.png)
*Class histogram sorted by total size — the largest memory consumers at the top.*

**Sort by size** to find what's consuming the most memory. Byte arrays (`byte[]`), char arrays (`char[]`), and strings are always near the top — that's normal. Look for your application's classes or unexpected framework objects appearing high on the list. If a cache object holds 40% of the heap, you've found your lead.

**Sort by count** to find what's proliferating. A million instances of a small DTO suggests a collection that grows without bounds. Even small objects add up when they number in the millions.

The histogram tells you what's big. The dominator tree tells you why.

## Dominator Tree: Who Owns the Memory?

The dominator tree answers the question: **if I removed this object, how much memory would be freed?**

![Dominator tree showing retained memory ownership](images/release-notes/heap-dump/04-dominator-tree.png)
*Dominator tree with retained size showing memory ownership hierarchy.*

Every object has two size metrics:

- **Shallow size** — The object's own memory footprint (its fields, header, alignment padding)
- **Retained size** — The total memory that would be freed if this object were garbage collected, including everything it exclusively references

A `HashMap` might have a shallow size of 48 bytes but a retained size of 500 MB — because it references thousands of entries that reference the actual data. The retained size is what matters for leak investigation.

The dominator tree shows the top objects by retained size and lets you expand each to see what they contain. Navigate down the tree to find the data structure that's growing: is it a cache that never evicts? A list that accumulates results from every request? An event listener registry that never unregisters?

## Leak Suspects: Automated Detection

Jeffrey's **Leak Suspects** runs heuristic analysis to flag the most likely leak sources automatically:

- **Single large object** — One instance retaining more than 10% of the total heap
- **Class-level accumulation** — All instances of a class collectively retaining more than 15% of the heap
- **Large collections** — Collections holding many objects of the same type, suggesting unbounded growth

Each suspect includes a severity ranking, a description of why it was flagged, the retained size as a percentage of the heap, and a reference chain showing the path from the suspect to its root.

This is the fastest way to go from "I have a leak" to "this specific `ConcurrentHashMap` in `SessionManager` is holding 62% of the heap." For well-defined leaks with a single dominant accumulation point, leak suspects often identify the root cause in seconds.

## GC Roots and Reference Chains

When you've identified a suspicious object — from the histogram, dominator tree, or leak suspects — the next question is: **why can't the GC collect it?** The answer is always: because there's a reference chain from a GC root to this object.

Jeffrey traces the path from any object back to its GC roots. A GC root is an object the JVM keeps alive by definition: active thread stacks, static fields, JNI references, and system class loaders. Everything reachable from a GC root stays alive; everything unreachable is garbage collected.

The reference chain reveals the retention path. Common patterns:

- **Static field → collection → your objects** — A static `Map` or `List` that accumulates entries. The fix is adding eviction, using weak references, or clearing the collection appropriately.
- **Thread local → your objects** — Thread-local variables that aren't cleaned up after request processing, especially in thread pools where threads are reused.
- **Listener/callback registry → your objects** — Event listeners registered but never deregistered, keeping the listener and everything it references alive.
- **ClassLoader → classes → static fields → your objects** — Especially common in application servers and OSGi containers where class loaders are leaked.

## Quick Wins: Strings and Collections

Two analyses often reveal easy optimization opportunities even when there isn't a leak:

### String Analysis

![String deduplication analysis](images/release-notes/heap-dump/05-string-analysis.png)
*String deduplication report showing already-deduplicated strings and additional opportunities.*

Java applications are full of duplicate strings — the same content stored in multiple `String` objects with separate backing `byte[]` arrays. Jeffrey's string analysis shows:

- **Already deduplicated** — Strings sharing the same backing array (JVM string deduplication is working)
- **Deduplication opportunities** — Identical strings with separate arrays, showing exactly how much memory could be saved

If you see hundreds of megabytes in deduplication opportunities, enabling `-XX:+UseStringDeduplication` (G1GC) or interning frequently repeated strings can reclaim significant heap space with zero code changes.

### Collection Analysis

Jeffrey examines common collection types (HashMap, ArrayList, HashSet, etc.) and reports:

- **Fill ratio distribution** — How full are your collections? Many applications create collections with default initial capacity that hold only a few elements, wasting the oversized backing array.
- **Wasted bytes** — The concrete memory cost of oversized collections, aggregated by class. If your application wastes 200 MB in half-empty HashMaps, right-sizing the initial capacity is a straightforward fix.

## OQL: Custom Investigation

When the built-in analyses don't answer your specific question, Jeffrey provides **OQL (Object Query Language)** — a SQL-like query language for heap dumps.

![OQL query interface for custom heap investigation](images/release-notes/heap-dump/06-oql-assistant.png)
*OQL query editor with AI assistant for generating queries from natural language.*

Examples of what you can query:

```sql
select s from java.lang.String s where s.value.length > 1000
```
Find all strings longer than 1,000 characters — useful for detecting oversized log messages or serialized data held in memory.

```sql
select o from java.util.HashMap o where o.size > 10000
```
Find all HashMaps with more than 10,000 entries — catches unbounded caches and accumulators.

Jeffrey also includes an AI-powered OQL assistant that generates queries from natural language descriptions, so you don't need to memorize the syntax.

## What's Next

You now have a complete memory investigation toolkit:

1. **GC Analysis** — Understand collector behavior from JFR data (pauses, frequency, efficiency)
2. **Heap Memory Trends** — Spot the rising post-GC floor that signals a leak
3. **Class Histogram** — Find what's consuming memory
4. **Dominator Tree** — Find who owns the memory
5. **Leak Suspects** — Automated root cause identification
6. **GC Roots** — Trace the reference chain keeping objects alive
7. **String & Collection Analysis** — Quick wins for memory efficiency

In the next article, we'll explore **application-level monitoring** — tracking HTTP requests, database queries, connection pools, and gRPC calls using custom JFR events with the Jeffrey Events library.

You can find the source code at [github.com/petrbouda/jeffrey](https://github.com/petrbouda/jeffrey) and the full documentation, examples, and release notes at [jeffrey-analyst.cafe](https://jeffrey-analyst.cafe). To try it right now:

```bash
docker run -it --network host petrbouda/microscope-examples
```

Open [http://localhost:8080](http://localhost:8080) and explore the pre-loaded heap dump examples.
