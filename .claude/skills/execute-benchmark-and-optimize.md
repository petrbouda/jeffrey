# Benchmark with Optimizations

This skill guides iterative performance optimization using JMH benchmarks with automatic verification and results tracking. Applicable to any benchmark in the `jmh-tests` module.

## Critical Instructions

**ALWAYS use ultrathink (extended thinking)** when executing this skill. Deep analysis is required to:
- Generate optimization iterations extensively for **every reasonable idea**
- Explore all possible bottlenecks (SQL, schema, Java code, data structures, algorithms, I/O)
- Consider edge cases and non-obvious optimization opportunities
- Reason through trade-offs before implementing changes

**ALWAYS clean results before each test run:**
```bash
rm -rf jmh-tests/target/results
```
This ensures fresh baseline generation and prevents stale verification data from causing false positives/negatives.

## When to Use

Use this skill when you need to:
- Optimize any performance-critical code path
- Try multiple optimization approaches iteratively
- Track before/after results with verification
- Generate documentation of optimization attempts
- Validate that optimizations don't change output correctness

## Quick Start

### 1. Identify Benchmark

Find or create the benchmark class in `jmh-tests/src/main/java/pbouda/jeffrey/jmh/`:
```bash
ls jmh-tests/src/main/java/pbouda/jeffrey/jmh/
```

### 2. Run Baseline

**Requires**: Java 25 (use `sdk use java 25.0.1-amzn` if using SDKMAN)

```bash
# Build from project root
mvn package -pl jmh-tests -am -DskipTests -q

# Create output directory for async-profiler flamegraphs
mkdir -p jmh-tests/target/benchmarks/async-profiler

# Check if async-profiler is available and set flamegraph output
# Default: CPU profiling. Use event=alloc for allocation profiling when explicitly requested
ASYNC_PROF=""
if [ -f "$JAVA_HOME/lib/libasyncProfiler.dylib" ] || [ -f "$JAVA_HOME/lib/libasyncProfiler.so" ]; then
  ASYNC_PROF='-prof "async:output=flamegraph;event=cpu;dir=jmh-tests/target/benchmarks/async-profiler"'
fi

# Run specific benchmark with profiling (from project root)
java -jar jmh-tests/target/benchmarks.jar <BenchmarkClass> -prof gc $ASYNC_PROF

# Or run all benchmarks with profiling
java -jar jmh-tests/target/benchmarks.jar -prof gc $ASYNC_PROF
```

**Note**: Always run with `-prof gc`. Add async-profiler flamegraph output only if async-profiler library is available at `$JAVA_HOME/lib/`.

**Profiling Mode**: Default is CPU (`event=cpu`). For allocation profiling, replace `event=cpu` with `event=alloc` in the ASYNC_PROF variable. Only one async-profiler event type can be used per run.

**Async Profiler Output**: HTML flamegraphs are generated at `jmh-tests/target/benchmarks/async-profiler/<SimpleClassName>.<method>/`

### 3. Make Optimization Change

Apply one optimization at a time. Categories to consider:
- SQL query optimization
- Database schema changes
- Java code changes (algorithms, data structures)
- Caching strategies
- I/O optimizations

### 4. Rebuild and Verify

```bash
mvn package -pl jmh-tests -am -DskipTests -q

java -jar jmh-tests/target/benchmarks.jar <BenchmarkClass> -prof gc $ASYNC_PROF
```

### 5. Decision Matrix

| Verification | Performance | Action |
|--------------|-------------|--------|
| PASS | Improved >5% | **KEEP** |
| PASS | Same/Worse | REVERT |
| FAIL | Any | **REVERT immediately** |

### 6. Rethink After Each Iteration

**IMPORTANT**: After every iteration that contains any changes (kept or reverted), pause and **use ultrathink** to deeply rethink the optimization plan:

1. **Analyze what you learned** - Did the change reveal unexpected bottlenecks? Did profiling data shift?
2. **Re-examine assumptions** - Are the original priorities still valid? Has the hot path changed?
3. **Discover new possibilities** - Use MCP tools (DuckDB, etc.) to run new analysis queries
4. **Generate new ideas extensively** - Brainstorm every reasonable optimization idea based on new insights
5. **Update the plan** - Add newly discovered optimizations, reprioritize existing ones, remove irrelevant items

This iterative rethinking often uncovers optimizations that weren't visible before the previous change was applied.

---

## Optimization Categories

### Category 1: SQL/Database Query Optimization

If the benchmark involves database queries, this is often the biggest opportunity (can be 90%+ of time).

**Analysis tools:**
```sql
-- Check current indexes
SELECT * FROM duckdb_indexes();

-- Explain query plan
EXPLAIN ANALYZE <your query>;

-- Table statistics
SELECT table_name, estimated_size FROM duckdb_tables();

-- Check if bottleneck shifted after changes
SELECT * FROM duckdb_profiling_info();
```

**Common optimizations:**
- Add missing indexes on JOIN and WHERE columns
- Optimize CTEs (Common Table Expressions) - remove if unused, or materialize if reused
- Reduce data scanned with better filtering
- Use covering indexes to avoid table lookups

### Category 2: Schema Experimentation

**The schema itself may be the bottleneck.** Create an experimental copy of the database to test schema modifications without affecting the original.

**Setup experimental database:**
```bash
# Copy the original database
cp jmh-tests/data/<database>.db jmh-tests/data/<database>-experimental.db

# Update benchmark to use experimental DB (or use MCP to connect)
```

**Schema optimization ideas:**
- **Add/Modify Indexes** - Composite indexes for common query patterns
- **Denormalize for Read Performance** - Pre-join frequently accessed data
- **Pre-aggregate Common Queries** - Create cache/summary tables
- **Column Type Optimization** - Use smaller types where possible
- **Partitioning** - Split large tables by common filter columns

**Testing workflow:**
1. Copy database to experimental file
2. Apply schema change via MCP or DuckDB CLI
3. Update benchmark's DATABASE_PATH to experimental DB
4. Run benchmark and compare
5. If improved: plan how to implement in production schema/migrations

### Category 3: Java Code Optimization

**Data structure optimizations:**
- TreeMap → HashMap (if ordering not needed)
- ArrayList → primitive arrays (for hot paths)
- Pre-size collections when size is known
- Object pooling for frequently allocated objects

**Algorithm optimizations:**
- Lazy computation (compute on-demand instead of eagerly)
- Caching/memoization of expensive computations
- Batch processing instead of item-by-item
- Parallel processing with streams or executors

**Memory optimizations:**
- Reduce object allocations in hot paths
- Use primitive types instead of boxed types
- Reuse buffers and builders

### Category 4: I/O Optimization

- Buffer sizes for file/network operations
- Batch database operations
- Connection pooling configuration
- Async I/O where applicable

---

## Verification Modes

Benchmarks support two verification modes via JMH `-p` parameter:

### HASH_ONLY (Default)
Compares SHA-256 hashes of the output. Fast and sufficient for most cases.

```bash
# Default - hash comparison only
java -jar jmh-tests/target/benchmarks.jar <Benchmark> -prof gc $ASYNC_PROF
```

### FULL_OUTPUT
Saves the current output to `jmh-tests/target/verification-output/` when verification fails.
Use this mode when you need to debug differences.

```bash
# Enable full output for debugging
java -jar jmh-tests/target/benchmarks.jar <Benchmark> -prof gc $ASYNC_PROF -p verificationMode=FULL_OUTPUT
```

**Output location**: `jmh-tests/target/verification-output/<BenchmarkName>/`
- `current-frame.json` - The actual output
- `current-frame.sha256` - Hash of the output

---

## Results Folder Structure

```
/jmh-tests/target/results/
└── <timestamp>/                    # e.g., 2025-01-06_09-15-30
    └── <BenchmarkClass>/
        ├── baseline-output.json    # Output for verification
        ├── baseline.sha256         # Hash for quick comparison
        └── optimization-results.md # All iterations documented
```

---

## Final Results Report

**IMPORTANT**: At the end of the optimization session, always generate a final markdown report.

### Output Location
```
jmh-tests/target/benchmarks/<ClassName>-<timestamp>.md
```

Example: `jmh-tests/target/benchmarks/FlamegraphBenchmark-2025-01-06_16-45-00.md`

### Execution Steps

1. Create the report directory: `mkdir -p jmh-tests/target/benchmarks`
2. Generate the markdown file with timestamp in format: `YYYY-MM-DD_HH-MM-SS`
3. Include all optimization iterations, final results, and actionable recommendations

---

## Results Markdown Template

```markdown
# Optimization Results: <ClassName>
**Date**: <timestamp>
**JVM**: JDK <version>
**Duration**: <total time spent on optimization session>

## Executive Summary

| Metric | Before | After | Improvement |
|--------|--------|-------|-------------|
| Best Method | X ms | Y ms | ~Nx faster |

## Baseline Results

| Benchmark Method | Score | Error | Units |
|------------------|-------|-------|-------|
| methodA | X | ±Y | ms/op |
| methodB | X | ±Y | ms/op |

## Optimization Iterations

### Iteration 1: [Optimization Name]

**Change**: Description of what was changed
**Files**: List of modified files
**Hypothesis**: Why this should improve performance

#### Results
| Method | Before | After | Change |
|--------|--------|-------|--------|
| methodA | X ms | Y ms | -Z% |

#### Verification
- Status: PASS/FAIL
- Hash: [SHA-256 if applicable]

#### Decision
KEEP / REVERT (reason)

#### Insights
- New insights discovered: [what did this iteration reveal?]
- Plan adjustments: [new optimizations to try, reprioritized items]

---

### Iteration N: [Optimization Name]
[Repeat structure for each iteration]

---

## Final Results

| Benchmark Method | Score | Error | Units |
|------------------|-------|-------|-------|
| baseline | X | ±Y | ms/op |
| optimized | Z | ±W | ms/op |

### Performance Summary
| Comparison | Before | After | Speedup |
|------------|--------|-------|---------|
| baseline vs optimized | X ms | Y ms | ~Nx |

## Profiling Data

### GC Statistics (Final)
| Method | Allocation Rate | GC Count | GC Time |
|--------|-----------------|----------|---------|
| baseline | X MB/sec | N | Y ms |
| optimized | X MB/sec | N | Y ms |

### Async Profiler Flamegraphs

Generated flamegraphs are located at `jmh-tests/target/benchmarks/async-profiler/`:

| Benchmark | Method | Flamegraph |
|-----------|--------|------------|
| <SimpleClassName> | baseline | [CPU Flamegraph](jmh-tests/target/benchmarks/async-profiler/<SimpleClassName>.baseline/flame-cpu-forward.html) |
| <SimpleClassName> | optimized | [CPU Flamegraph](jmh-tests/target/benchmarks/async-profiler/<SimpleClassName>.optimized/flame-cpu-forward.html) |

**Key Findings**:
- [Hot methods identified from CPU flamegraph]
- [CPU hotspots and optimization opportunities]

## Recommendations

### Implemented Optimizations
1. [Summary of optimization that was kept]
2. [Another kept optimization]

### Rejected Optimizations
1. [Optimization that was reverted and why]

### Future Optimizations to Consider
- [Potential optimization not yet tried]
- [Area that needs further investigation]

### Production Considerations
- [Any caveats or considerations for deploying these optimizations]
- [Memory/CPU trade-offs to be aware of]

## Appendix

<details>
<summary>Full JMH Output - Baseline</summary>

```
[Paste baseline JMH output]
```

</details>

<details>
<summary>Full JMH Output - Final</summary>

```
[Paste final JMH output]
```

</details>
```

---

## Creating a New Benchmark

When creating a new benchmark, include:

1. **JMH annotations** for proper measurement:
```java
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 10, time = 1)
@Fork(value = 1, jvmArgs = {"-Xms2g", "-Xmx2g"})
```

2. **Setup method** to prepare test data:
```java
@Setup(Level.Trial)
public void setup() {
    // Load data, initialize resources
}
```

3. **Verification** to ensure correctness:
```java
@TearDown(Level.Invocation)
public void verify() {
    // Compare output hash or structure
}
```

4. **Separate benchmark methods** for each phase to identify bottlenecks

---

## Profiling Tips

**Check for async-profiler** (optional, at `$JAVA_HOME/lib/libasyncProfiler.dylib` or `.so`):
```bash
mkdir -p jmh-tests/target/benchmarks/async-profiler

# Default: CPU profiling. Use event=alloc for allocation profiling when explicitly requested
ASYNC_PROF=""
if [ -f "$JAVA_HOME/lib/libasyncProfiler.dylib" ] || [ -f "$JAVA_HOME/lib/libasyncProfiler.so" ]; then
  ASYNC_PROF='-prof "async:output=flamegraph;event=cpu;dir=jmh-tests/target/benchmarks/async-profiler"'
fi
```

**Default profiling**:
- `-prof gc` - GC statistics (allocation rate, GC count, GC time) - **always use**
- `-prof "async:output=flamegraph;event=cpu;dir=..."` - CPU flamegraph (default) - **if available**
- `-prof "async:output=flamegraph;event=alloc;dir=..."` - Allocation flamegraph (use when explicitly requested) - **if available**

**Note**: Only one async-profiler event type can be used per JMH run.

```bash
# Standard execution with profiling (recommended)
java -jar jmh-tests/target/benchmarks.jar <Benchmark> -prof gc $ASYNC_PROF

# Without forking (for debugging, uses current JVM)
java -jar jmh-tests/target/benchmarks.jar <Benchmark> -f 0 -prof gc $ASYNC_PROF
```

**Interpreting GC profiler output:**
- `gc.alloc.rate` - Memory allocation rate (MB/sec)
- `gc.count` - Number of GC cycles during measurement
- `gc.time` - Total GC pause time (ms)

**Interpreting async-profiler flamegraphs:**
- **Location**: `jmh-tests/target/benchmarks/async-profiler/<SimpleClassName>.<method>/flame-cpu-forward.html`
- CPU flame graphs show where time is spent (wide bars = CPU hotspots)
- Allocation flame graphs show memory hotspots (wide bars = allocation-heavy code)
- Look for tall stacks indicating deep call chains
- Wide bars indicate hotspots to optimize

---

## Related Skills

- **create-benchmark**: Create new benchmark classes
- **execute-benchmark**: Run a benchmark without optimization workflow
