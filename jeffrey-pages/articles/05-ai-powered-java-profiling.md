# AI-Powered Java Profiling: Let Claude Analyze Your JFR Recordings and Heap Dumps

A JFR recording from a 60-second profiling session can contain millions of events — CPU samples, allocation events, GC pauses, thread activity, I/O operations, and dozens of other JVM metrics. A heap dump from a production JVM can hold millions of objects across thousands of classes. The data is there, but finding the signal in that volume takes experience and time.

What if you could ask questions in plain English and get answers backed by real queries against the profiling data?

Jeffrey integrates with Claude and ChatGPT to provide three AI-powered analysis assistants: one for JFR recordings, one for heap dumps, and one for generating OQL queries. Each assistant has access to specific tools that let it query the underlying data, so answers are grounded in facts — not hallucinated from training data.

This article — the fifth in our series on Java profiling with Jeffrey — shows how AI-assisted analysis works and when it adds the most value.

## Setting Up AI in Jeffrey

AI analysis is disabled by default. To enable it, open **Settings** in Jeffrey and configure:

1. **Provider** — Choose between Claude (Anthropic) or ChatGPT (OpenAI)
2. **Model** — Select a model (e.g., `claude-sonnet-4-6`, `gpt-4o`)
3. **API Key** — Your provider API key, encrypted at rest with a machine-bound key

That's it. Once configured, AI features appear automatically in the profile analysis views. No additional infrastructure or services are required — Jeffrey calls the AI provider's API directly.

## JFR Analysis Assistant

The JFR Analysis Assistant lets you ask questions about your recording in natural language. Behind the scenes, it translates your questions into SQL queries against the DuckDB database where Jeffrey stores all parsed JFR events.

![JFR AI Analysis chat interface](images/release-notes/ai-analysis/01-jfr-analysis.png)
*Ask questions about your JFR recording in natural language.*

### How It Works

Jeffrey uses a **tool-calling architecture** (similar to MCP). The AI model doesn't see the raw data directly. Instead, it has access to a set of tools:

- **List event types** — Discover what events are available in the recording with counts
- **Query events** — Fetch specific event types with optional filtering
- **Execute SQL** — Run read-only SELECT queries against the DuckDB database
- **Get profile info** — Retrieve profile metadata

When you ask a question, the AI decides which tools to call, formulates the appropriate queries, interprets the results, and presents findings in plain language. Jeffrey injects the actual database schema into the AI's context, so it generates valid SQL against real column names — not guessed ones.

### What to Ask

The assistant is most effective for questions that would otherwise require you to write SQL or navigate multiple views:

**CPU profiling:**
- "What are the hottest methods by sample count?"
- "Which packages consume the most CPU time?"
- "Show me the top 10 methods that appear most frequently in stack traces"

**Memory allocation:**
- "What are the top allocation sites by total bytes?"
- "Which methods allocate the most objects outside TLABs?"
- "Show allocation patterns over time — are there any spikes?"

**Garbage collection:**
- "Summarize the GC behavior — how long are pauses, how frequent are collections?"
- "Compare young gen vs. old gen collection efficiency"
- "Are there any GC pauses longer than 200ms?"

![AI GC pause time analysis](images/release-notes/ai-analysis/03-gc-analysis.png)
*The assistant queries GC events and presents findings with context.*

![AI GC detailed breakdown](images/release-notes/ai-analysis/09-gc-detailed.png)
*Detailed GC analysis with pause distribution and recommendations.*

**Threading:**
- "Which threads are consuming the most CPU?"
- "Are there any lock contention hotspots?"
- "Show me thread state distribution over time"

**I/O and compilation:**
- "Are there any unusually long file I/O operations?"
- "Which methods were deoptimized by the JIT compiler?"
- "Show me the longest JIT compilations"

The assistant also generates **follow-up suggestions** based on your conversation — if you ask about GC pauses, it might suggest investigating allocation rates or checking heap memory trends next.

![AI findings and recommendations](images/release-notes/ai-analysis/04-gc-recommendations.png)
*Actionable recommendations with follow-up suggestions for deeper investigation.*

### Safety: Read-Only by Default

All SQL queries executed by the AI are read-only SELECT statements. Results are capped at 1,000 rows and 50,000 characters. The AI cannot modify your data unless you explicitly enable the "Allow Modifications" option — which is useful for data cleanup tasks but disabled by default.

## Heap Dump AI Analysis

The Heap Dump AI Assistant takes a different approach. Instead of answering ad-hoc questions, it follows a **structured investigation strategy** designed to systematically identify memory issues.

![Heap Dump AI Analysis](images/release-notes/ai-analysis/05-heap-dump-ai.png)
*Heap dump AI analysis follows a structured investigation approach.*

### Available Tools

The assistant has access to 15 heap-specific tools:

- **Heap summary** — Total bytes, instance count, class count, GC root count
- **Class histogram** — Top classes by size or count
- **Biggest objects** — Individual objects with largest retained size
- **Leak suspects** — Automated heuristic leak detection
- **Dominator tree** — Memory ownership hierarchy (roots and children)
- **Instance detail** — All fields and values for a specific object
- **Path to GC root** — Reference chains explaining why an object is retained
- **Referrers / reachables** — Incoming and outgoing references for any object
- **String analysis** — Deduplication opportunities
- **Collection analysis** — Fill ratios and waste detection
- **Thread enumeration** — Thread objects with stack traces
- **GC root summary** — Root types and counts
- **OQL execution** — Custom Object Query Language queries

### Investigation Flow

The AI follows a recommended analysis order:

1. **Overview** — Get the heap summary to understand overall state
2. **Memory hotspots** — Check the class histogram for dominant consumers
3. **Leak detection** — Run leak suspects and review biggest objects
4. **Drill down** — Inspect suspicious instances, examining their fields and values
5. **Reference tracing** — Follow the path to GC root to understand retention
6. **Efficiency analysis** — Check string deduplication and collection fill ratios

![AI heap dump overview](images/release-notes/ai-analysis/06-heap-dump-overview.png)
*The assistant summarizes heap state and identifies memory hotspots.*

![AI heap dump findings](images/release-notes/ai-analysis/07-heap-dump-findings.png)
*Detailed findings with object inspection and retention analysis.*

This structured approach means you can start an investigation with a single click and get a comprehensive report — especially useful when you don't know where to begin or want a second opinion on your manual analysis.

## OQL Assistant

The OQL (Object Query Language) Assistant is a focused tool for a specific task: generating heap dump queries from natural language descriptions.

![OQL Assistant query generation](images/release-notes/ai-analysis/02-oql-assistant.png)
*Describe what you're looking for, and the assistant generates the OQL query.*

OQL is powerful but has its own syntax that differs from SQL — it uses `&&` instead of `AND`, has specific functions like `sizeof()` and `rsizeof()` for memory sizes, and navigates object graphs through reference chains. The assistant knows the syntax and generates valid queries:

- "Find all strings longer than 10,000 characters" → `select s from java.lang.String s where s.value.length > 10000`
- "Show me HashMaps with more than 1,000 entries" → `select m from java.util.HashMap m where m.size > 1000`
- "Find all Thread objects that are in BLOCKED state" → `select t from java.lang.Thread t where t.threadStatus == 1025`

The assistant generates the query, you review it, and execute it in Jeffrey's OQL editor. It also suggests follow-up queries based on the results.

## When AI Adds the Most Value

AI analysis isn't a replacement for understanding flamegraphs, GC behavior, or heap dump navigation. It's most valuable in specific scenarios:

**Exploration.** You have a recording and don't know where to start. The AI can quickly survey the data across multiple dimensions — CPU, memory, GC, threads — and highlight the most interesting areas.

**Complex queries.** Answering "which methods allocate the most memory in the first 5 seconds of the recording" requires a SQL query with time filtering, aggregation, and sorting. The AI writes it for you.

**Second opinion.** You've done manual analysis and think you found the problem. Ask the AI to investigate independently — it might confirm your finding or surface something you missed.

**Heap dump investigation.** Navigating a large heap dump is tedious. The AI can systematically work through the dominator tree, follow reference chains, and summarize findings faster than manual clicking.

**Knowledge gap.** Not everyone knows what "TLAB allocation" means or why C1-compiled frames matter. The AI explains concepts in context as it analyzes your data.

For straightforward investigations — "the flamegraph shows method X is 40% of CPU" — the visual tools in Jeffrey are faster and more direct. Use AI when the question is complex, the data is large, or you want automated exploration.

## What's Next

You now have the complete Jeffrey analysis toolkit:

1. **Flamegraphs** — CPU, allocation, lock contention, differential, sub-second
2. **GC and memory** — Pause analysis, heap trends, heap dump investigation
3. **Application monitoring** — HTTP, database, gRPC, connection pools
4. **AI-powered analysis** — Natural language queries backed by real data

In the final article, we'll cover **production profiling at scale** — deploying Jeffrey Server in Kubernetes for continuous JFR recording collection, with Jeffrey CLI for container initialization and Jeffrey Local for analysis on your developer machine.

You can find the source code at [github.com/petrbouda/jeffrey](https://github.com/petrbouda/jeffrey) and the full documentation at [jeffrey-analyst.cafe](https://jeffrey-analyst.cafe). To try it right now:

```bash
docker run -it --network host petrbouda/microscope-examples
```

Open [http://localhost:8080](http://localhost:8080), configure your AI provider in Settings, and start asking questions about the pre-loaded profiles.
