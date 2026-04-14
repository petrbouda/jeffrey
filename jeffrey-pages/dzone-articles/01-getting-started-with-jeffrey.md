# Stop Guessing, Start Profiling: Getting Started with JFR Analysis Using Jeffrey

You optimized that database query. You added caching. You switched to a faster serialization library. But did any of it actually help? Without profiling, you're guessing — and guessing is expensive. You might spend days optimizing code that accounts for 2% of your application's CPU time while the real bottleneck hides in plain sight.

Java gives you one of the best profiling mechanisms available in any runtime: **JDK Flight Recorder (JFR)**. Combined with **Async Profiler** for recording and **Jeffrey** for analysis, you can go from "I think it's slow" to "I know exactly where the time goes" in minutes.

This article walks you through the entire workflow: recording a JFR file with Async Profiler, launching Jeffrey, and uncovering real performance insights from your application.

## Why Async Profiler + JFR?

JDK Flight Recorder is a profiling and event collection framework built directly into the JVM. It captures a wide range of runtime events — garbage collection pauses, thread activity, class loading, memory allocation, I/O operations — with extremely low overhead (typically under 2%). JFR events are written in a compact binary format designed for production use.

**Async Profiler** is an open-source sampling profiler for Java that goes beyond what JFR can do on its own. It uses `perf_events` on Linux to collect CPU samples with accurate stack traces (no safepoint bias), and it adds allocation and lock contention profiling on top. The key flag that ties everything together is `jfrsync=default` — this tells Async Profiler to write its samples into a JFR file alongside the standard JDK events. You get the best of both worlds in a single recording:

- **CPU profiling** (`cpu`) — Where is your application spending CPU cycles? Async Profiler samples the call stack at regular intervals to build a statistical picture of CPU usage.
- **Allocation profiling** (`alloc`) — Where is memory being allocated? Every TLAB (Thread Local Allocation Buffer) allocation event is captured, showing you which methods are creating objects and how much memory they consume.
- **Lock contention profiling** (`lock`) — Where are threads waiting? Lock events reveal contention points where threads block on synchronized sections, ReentrantLocks, or other concurrency primitives.
- **JFR synchronization** (`jfrsync=default`) — Async Profiler merges its data with JFR's standard event stream. This means your recording also contains GC events, thread statistics, JIT compilation data, and dozens of other JVM metrics — all correlated in the same timeline.

## Recording Your Application

There are two common ways to capture a recording with Async Profiler.

### Option 1: Attach to a Running Process

If your application is already running, use the `asprof` command-line tool:

```bash
asprof -e cpu,alloc,lock --jfrsync default -d 60 -f recording.jfr <pid>
```

This attaches to the process with the given PID, profiles for 60 seconds, and writes the output to `recording.jfr`. Adjust `-d` for longer or shorter recording windows — 30 to 120 seconds is usually enough to capture representative behavior.

### Option 2: Start with the Java Agent

To profile from the very beginning (useful for capturing startup behavior), launch your application with Async Profiler as a Java agent:

```bash
java -agentpath:/path/to/libasyncProfiler.so=start,event=cpu,alloc,lock,jfrsync=default,file=recording.jfr \
     -jar myapp.jar
```

This starts profiling immediately when the JVM boots. The recording file is written continuously, so you can stop the application when you have enough data or let it run for a defined duration.

Either way, you end up with a `.jfr` file that contains both Async Profiler's sampling data and JFR's built-in events. This single file is everything Jeffrey needs.

## Getting Jeffrey Running

Jeffrey is a self-hosted analysis tool — no cloud services, no account signup, no data leaving your machine. The fastest way to start is with Docker:

```bash
docker run -it --network host petrbouda/jeffrey
```

Open [http://localhost:8080](http://localhost:8080) in your browser. That's it — Jeffrey is ready to analyze your recordings.

If you want to explore Jeffrey with pre-loaded example data first (recommended for your first time), use the examples image instead:

```bash
docker run -it --network host petrbouda/jeffrey-examples
```

This ships with sample JFR recordings and pre-built profiles so you can explore every feature without generating your own data first.

## Quick Analysis: Upload and Explore

Jeffrey's **Quick Analysis** is the fastest path from a JFR file to actionable insights. Click "Quick Analysis" on the home page, drag and drop your `recording.jfr` file, and click "Analyze."

![Upload your JFR recording to Jeffrey](images/release-notes/quick-analysis/01-upload.png)
*Upload your JFR recording — drag and drop, select the file, and click Analyze.*

Jeffrey parses the recording, stores the data in an embedded DuckDB database (one per profile, fully isolated), and presents you with an analysis dashboard. The entire process takes seconds for typical recordings.

From here, you can navigate to any analysis feature: flamegraphs, GC analysis, thread statistics, heap memory trends, and more. Everything is generated from that single JFR file.

## Reading Your First Flamegraph

Flamegraphs are Jeffrey's primary visualization for profiling data. Navigate to **Flamegraphs** and select the type of analysis — CPU samples, allocation samples, or lock contention.

![Select the flamegraph type for analysis](images/release-notes/quick-analysis/03-flamegraph-selection.png)
*Choose between CPU, allocation, and lock contention flamegraphs.*

A flamegraph is a visualization of sampled stack traces. Here's how to read one:

- **Each box is a method (frame)** on the call stack
- **Width represents time** — the wider a box, the more samples included that method. A method that's 30% of the flamegraph width was on the CPU (or allocating, or contending) roughly 30% of the sampled time
- **Depth represents the call stack** — the bottom is the entry point (usually `main` or a thread's `run` method), and each layer up is a method called by the one below it
- **Color is categorical** — it helps distinguish frames visually but doesn't encode severity

The flamegraph is interactive. Click on any frame to zoom in and see its children in detail. Use the search bar to highlight specific packages or classes across the entire graph.

![Interactive flamegraph with correlated timeseries](images/release-notes/quick-analysis/04-flamegraph-timeseries.png)
*Flamegraph with a correlated timeseries showing sample distribution over time.*

Below the flamegraph, Jeffrey shows a **timeseries** of sample counts over the recording duration. This helps you spot patterns — a CPU spike at a specific time, a burst of allocations during startup, or periodic lock contention. You can select a time range in the timeseries to regenerate the flamegraph for just that interval.

![Drilling into a CPU hotspot](images/release-notes/quick-analysis/06-flamegraph-detail.png)
*Click any frame to zoom in and see the full call tree beneath it.*

**What to look for:** Start with the widest frames near the top of the graph. These are the methods consuming the most resources. Ask yourself: is this expected? A wide frame in your application code is an optimization candidate. A wide frame in a framework's internals might mean you're using it inefficiently. A wide frame in `GCTaskThread` means garbage collection is dominating — jump to the GC analysis.

## Guardian: Automated Issue Detection

Not sure where to start? Jeffrey's **Guardian** runs a set of automated checks against your recording and flags potential issues with color-coded severity levels.

![Guardian automated health checks](images/feature-screenshots/profile_guardian.png)
*Guardian scans your recording and surfaces issues organized by category.*

Guardian traverses the stack traces and evaluates event metrics against known patterns. It checks for common problems like:

- Excessive GC activity or long GC pauses
- High allocation rates in specific code paths
- Thread contention hotspots
- Compilation and deoptimization issues
- Suspicious patterns in framework usage

![Guardian issue breakdown](images/feature-screenshots/profile_guardian_2.png)
*Each issue includes severity, description, and the relevant stack traces.*

Each finding is categorized (OK, Warning, Critical) and includes enough context to understand the issue and where to look next. Think of Guardian as your first triage step — it highlights the areas most likely to reward investigation.

## GC at a Glance

Since you recorded with `jfrsync=default`, your JFR file contains detailed garbage collection events alongside the profiling data. Jeffrey's **GC Analysis** gives you a comprehensive overview without any additional setup.

![GC overview dashboard](images/release-notes/quick-analysis/02-gc-analysis.png)
*GC analysis dashboard showing pause statistics, collection frequency, and heap usage trends.*

The GC dashboard shows you:

- **Pause statistics** — total pause time, average pause, maximum pause, and pause distribution
- **Collection frequency** — how often young and old generation collections run
- **Heap usage trends** — committed vs. used heap over time, showing whether your application's memory footprint is stable, growing, or sawtoothing
- **GC algorithm details** — which collector is active and its configuration

This is often the first place to check when an application feels sluggish but CPU profiling looks normal. Long or frequent GC pauses can dominate latency even when your code is efficient. If you see pauses in the hundreds of milliseconds or a steadily growing heap with increasingly frequent collections, you've found a lead worth investigating.

## What's Next

You've just gone from a running Java application to actionable performance data in minutes:

1. **Record** with Async Profiler (cpu + alloc + lock + jfrsync=default)
2. **Analyze** with Jeffrey (upload, explore, investigate)
3. **Identify** hotspots with flamegraphs, automated checks with Guardian, and GC behavior at a glance

But this is just the beginning. Jeffrey offers significantly more depth than what we covered here. In the next articles in this series, we'll explore:

- **Deep flamegraph techniques** — differential flamegraphs to prove your optimization worked, sub-second analysis to zoom into startup or specific spikes, frame collapsing to cut through framework noise
- **Memory investigation** — from GC trends to heap dump analysis, dominator trees, leak suspects, and reference chain tracing
- **Application-level monitoring** — HTTP traffic, database statements, connection pools, and gRPC analysis using custom JFR events
- **AI-powered analysis** — asking questions about your recording in natural language and getting answers backed by real data

Jeffrey is open source (AGPL-3.0). You can find the source code at [github.com/petrbouda/jeffrey](https://github.com/petrbouda/jeffrey) and the full documentation, examples, and release notes at [jeffrey-analyst.cafe](https://jeffrey-analyst.cafe). To try it right now:

```bash
docker run -it --network host petrbouda/jeffrey-examples
```

Open [http://localhost:8080](http://localhost:8080), explore the pre-loaded examples, and see what your own recordings reveal.
