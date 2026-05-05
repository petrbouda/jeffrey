# Reading Flamegraphs Like a Pro: CPU, Allocation, and Lock Contention in Jeffrey

Flamegraphs are the single most information-dense visualization in performance profiling. A single image can reveal where your application spends CPU time, which methods allocate the most memory, and where threads block waiting for locks. Yet most developers glance at a flamegraph and walk away unsure what they just saw.

This article — the second in our series on Java profiling with Jeffrey — teaches you how to read flamegraphs effectively. We'll cover CPU, allocation, and lock contention analysis, then move into powerful techniques: differential flamegraphs for before/after comparison, sub-second analysis for time-windowed investigation, and frame collapsing to cut through framework noise.

If you haven't set up Jeffrey yet, check out [Article 1](01-getting-started-with-jeffrey.md) for the recording and installation workflow.

## Anatomy of a Flamegraph

A flamegraph is built from sampled stack traces. During profiling, Async Profiler periodically captures the call stack of every thread. Each stack trace is a snapshot: "at this moment, thread X was in method A, called by method B, called by method C." Thousands of these snapshots are collected, then merged into a single visualization.

Here's how to read it:

- **Each box is a stack frame** — a method on the call stack
- **Width equals sample count** — the wider a frame, the more often it appeared in the collected samples. A frame occupying 40% of the width was on the stack in roughly 40% of all samples
- **The y-axis is stack depth** — the bottom is the entry point (usually `Thread.run()` or `main()`), each layer up is a called method. The topmost frames are the methods that were actually executing when the sample was taken
- **Color indicates frame type** — Jeffrey color-codes frames by their execution mode: green for JIT-compiled code, teal for inlined methods, orange for C1-compiled, red for interpreted, blue for native code, and dark shades for kernel frames

The critical insight: **wide frames at the top are your optimization targets**. A wide frame deep in the stack just means it's a common caller — that's expected for framework entry points. A wide frame at the top means that method itself is consuming significant resources.

![CPU flamegraph showing execution sample distribution](images/feature-screenshots/profile_flamegraph.png)
*A CPU flamegraph in Jeffrey. Width represents sample proportion, color indicates execution mode.*

Click any frame to zoom in. Jeffrey recalculates the view with that frame as the root, revealing the complete call tree beneath it. Use the search bar with regex patterns to highlight specific packages or classes — matched frames turn pink, and Jeffrey shows the percentage of total samples they represent.

![Zoomed-in flamegraph showing frame details](images/feature-screenshots/profile_flamegraph_2.png)
*Clicking a frame zooms in to show the full call tree beneath it.*

## CPU Flamegraphs

CPU flamegraphs answer the question: **where is my application spending processor time?**

Each sample represents a moment when a thread was actively running on a CPU core. The flamegraph aggregates these into a call tree weighted by frequency. Start your investigation from the top:

- **Wide top-level frames in your code** — Direct optimization candidates. This method itself is doing work that consumes CPU. Look for tight loops, inefficient algorithms, or unnecessary computation.
- **Wide top-level frames in framework code** — You might be using the framework inefficiently. For example, a wide frame in Jackson serialization suggests you're serializing large or complex objects frequently. The fix is in your code, not the framework.
- **Wide frames in GC threads** — Garbage collection is consuming significant CPU. Jump to the GC analysis view (covered in [Article 1](01-getting-started-with-jeffrey.md)) to investigate pause times and collection frequency.
- **Many thin frames spread across the top** — No single hotspot dominates. Your CPU usage is distributed across many methods. This is common in well-optimized applications — or it means your recording was too short to capture the real pattern.

**Tip:** Enable "Exclude Non-Java Samples" to filter out JVM internal threads (GC, JIT compiler, VM operations) and focus purely on your application code. Enable "Exclude Idle Samples" to remove parked thread-pool threads that aren't doing useful work.

## Allocation Flamegraphs

Allocation flamegraphs show **where objects are being created** in your application. Every TLAB (Thread-Local Allocation Buffer) allocation event is captured, and the stack trace tells you exactly which method triggered it.

Jeffrey offers two perspectives:

- **Sample count** — How many allocation events occurred at each call site. Useful for finding methods that allocate frequently, even if each allocation is small.
- **Weight mode (bytes)** — Total bytes allocated at each call site. This reveals the methods responsible for the most memory pressure, regardless of how many individual allocations they perform.

Weight mode is enabled by default for allocation flamegraphs and is usually what you want. A method that allocates one 100 MB array is more impactful than one that creates a million 16-byte objects — weight mode surfaces the former.

Jeffrey also distinguishes between TLAB allocations (fast path, allocated from the thread's local buffer) and outside-TLAB allocations (slow path, requiring coordination with the heap). Outside-TLAB allocations are more expensive and worth investigating separately.

**What to look for:** Wide frames near the top where your code (not the JVM) is creating objects. Common culprits include string concatenation in loops, excessive autoboxing, creating temporary collections that are immediately discarded, and unnecessary defensive copies.

## Lock Contention Flamegraphs

Lock contention flamegraphs reveal **where threads are blocked waiting** for access to shared resources. This is where concurrency bugs and performance cliffs hide.

Jeffrey captures several types of blocking events:

- **Monitor Enter** (`synchronized` blocks) — A thread tried to enter a synchronized section but another thread held the lock
- **Thread Park** (`LockSupport.park()`) — Used by `ReentrantLock`, `CountDownLatch`, `Semaphore`, and most `java.util.concurrent` primitives
- **Thread Wait** (`Object.wait()`) — A thread called `wait()` on a monitor, often in producer-consumer patterns

In weight mode (enabled by default), the width represents **total nanoseconds blocked** — not just how often contention occurred, but how long threads waited. A frame that blocked for 5 seconds total is a bigger problem than one that blocked 1,000 times for 1 microsecond each.

**What to look for:** Wide frames indicate high contention. Trace down the stack to find the specific lock or resource causing the blockage. Common causes: a single `synchronized` method protecting a shared data structure accessed by many threads, connection pool exhaustion (all threads waiting for a database connection), or excessive logging to a synchronized appender.

## Differential Flamegraphs: Before vs. After

You optimized something. But did it actually help? Eyeballing two separate flamegraphs is unreliable — the human eye isn't good at comparing proportional differences across complex shapes. Differential flamegraphs solve this by overlaying two profiles in a single visualization.

In Jeffrey, you select a **primary profile** (the "after" version) and a **secondary profile** (the "before" version). The differential flamegraph shows every frame present in either profile, color-coded by change:

- **Red frames** — Consuming **more** time/samples in the primary profile (regression or new code path)
- **Blue frames** — Consuming **less** time/samples in the primary profile (improvement)
- **Gray frames** — Minimal change between profiles

The intensity of the color reflects the magnitude of the difference. A deep red frame is a significant regression; a pale blue frame is a minor improvement.

![Differential flamegraph — red (regression) vs blue (improvement)](images/feature-screenshots/profile_diffgraph.png)
*Differential flamegraph comparing two profiles. Red = more time in primary, blue = less.*

![Differential flamegraph detail view](images/feature-screenshots/profile_diffgraph_2.png)
*Hovering over a frame shows the exact sample counts and percentage difference.*

**Practical workflow:**

1. Record your application with the current code → create a profile in Jeffrey (this becomes the secondary/baseline)
2. Deploy your optimization
3. Record again under the same load → create another profile (this becomes the primary)
4. Generate a differential flamegraph comparing primary vs. secondary

If your optimization worked, you'll see blue frames in the area you changed. If you accidentally introduced a regression elsewhere, red frames will reveal it. This is the most reliable way to validate performance changes.

## Sub-Second Analysis: Zooming into Time Windows

Standard flamegraphs aggregate the entire recording into one view. This is useful for overall analysis, but it hides temporal patterns. If your application had a 2-second CPU spike at the 30-second mark of a 60-second recording, the flamegraph dilutes that spike into the overall average.

Sub-second analysis solves this by segmenting the recording timeline into small time buckets. Jeffrey displays an interactive timeline where each segment can generate its own flamegraph.

![Sub-second analysis timeline](images/feature-screenshots/profile_subsecond.png)
*Sub-second timeline showing sample distribution over time. Click any segment to generate a flamegraph.*

![Sub-second flamegraph for a selected time window](images/feature-screenshots/profile_subsecond_2.png)
*Flamegraph generated for a specific time window selected from the timeline.*

**When to use sub-second analysis:**

- **Startup profiling** — The first few seconds of an application's life look very different from steady-state. Sub-second analysis lets you isolate class loading, initialization, and warmup from normal operation.
- **Spike investigation** — You see a CPU or allocation spike in the correlated timeseries. Select that exact time range to generate a flamegraph showing only what happened during the spike.
- **Periodic patterns** — Some problems occur on a schedule (GC pauses, cron jobs, cache eviction). Sub-second analysis reveals the rhythm and lets you investigate individual occurrences.

## Cutting Through Framework Noise

Modern Java applications run on deep framework stacks. A single HTTP request in Spring Boot can generate stack traces 40-60 frames deep before reaching your code. Most of those frames are Spring dispatcher, filter chain, servlet container, and Netty I/O handler internals. They add visual noise to flamegraphs without providing actionable insight.

Jeffrey's **Collapse Frames** tool solves this. You provide class name patterns (e.g., `org.springframework.*`, `io.netty.*`), and Jeffrey replaces every consecutive sequence of matching frames with a single synthetic frame labeled with a name you choose.

![Flamegraph before frame collapsing — deep framework stacks](images/release-notes/tools/01-flamegraph-before.png)
*Before: deep framework stacks obscure application code.*

![Collapse Frames configuration dialog](images/release-notes/tools/02-collapse-frames.png)
*Configure patterns to match and a label for the synthetic frame.*

![Flamegraph after collapsing — simplified view](images/release-notes/tools/03-flamegraph-after.png)
*After: framework internals collapsed into single frames, application code clearly visible.*

The result is a dramatically cleaner flamegraph where your application's methods stand out. The collapsed frame still accounts for the same total samples — you're simplifying the view, not hiding data.

Jeffrey also offers **Rename Frames** for anonymization. If you need to share a profile externally but can't reveal internal package names, use pattern-based search-and-replace to transform `com.company.internal.*` into `com.example.app.*` before exporting.

Both tools show a preview before applying, so you can verify the transformation. Note that these modifications are permanent on the profile — work on a copy if you want to preserve the original.

## Tips and Tricks

**Thread Mode.** By default, flamegraphs aggregate all threads into a single view. Enable thread mode to see a separate branch for each thread. This reveals thread imbalance — for example, one worker thread consuming 80% of CPU while others are nearly idle, indicating uneven work distribution.

**Timeseries Correlation.** Below every flamegraph, Jeffrey shows a timeseries of sample counts over the recording duration. Use it to spot patterns before diving into the stack data. You can select a time range directly on the timeseries to regenerate the flamegraph for just that interval — a quick alternative to sub-second analysis.

![Flamegraph with correlated timeseries](images/release-notes/quick-analysis/04-flamegraph-timeseries.png)
*The timeseries below the flamegraph shows sample distribution over time. Select a range to zoom in.*

**Search with Regex.** Type a regex pattern in the search bar to highlight matching frames across the entire graph. Matched frames turn pink, and Jeffrey displays the total percentage of samples they represent. Use this to answer questions like "what percentage of CPU time is spent in database calls?" by searching for your JDBC driver's package name.

## What's Next

You now know how to read flamegraphs across three dimensions — CPU time, memory allocation, and lock contention — and you have tools to compare profiles, zoom into time windows, and simplify complex stacks. These techniques cover the majority of performance investigations you'll encounter.

In the next article, we'll tackle the other side of the memory story: **garbage collection analysis and heap dump investigation**. When the flamegraph points to GC pressure or a memory leak, Jeffrey's GC dashboard, heap dump analyzer, dominator trees, and leak suspect detection take the investigation to its conclusion.

You can find the source code at [github.com/petrbouda/jeffrey](https://github.com/petrbouda/jeffrey) and the full documentation, examples, and release notes at [jeffrey-analyst.cafe](https://jeffrey-analyst.cafe). To try it right now:

```bash
docker run -it --network host petrbouda/microscope-examples
```

Open [http://localhost:8080](http://localhost:8080) and explore the pre-loaded flamegraph examples hands-on.
