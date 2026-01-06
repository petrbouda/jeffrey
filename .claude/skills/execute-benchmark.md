# Execute Benchmark

Run a JMH benchmark and display results. Use this skill to quickly measure performance without optimization workflow.

**Requires**: Java 25 (use `sdk use java 25.0.1-amzn` if using SDKMAN)

## Usage

```bash
# Build the benchmark jar (from project root)
mvn package -pl jmh-tests -am -DskipTests -q

# Create output directory for async-profiler flamegraphs
mkdir -p jmh-tests/target/benchmarks/async-profiler

# Check if async-profiler is available and set flamegraph output
# Default: CPU profiling. Use event=alloc for allocation profiling when explicitly requested
ASYNC_PROF=""
if [ -f "$JAVA_HOME/lib/libasyncProfiler.dylib" ] || [ -f "$JAVA_HOME/lib/libasyncProfiler.so" ]; then
  ASYNC_PROF='-prof "async:output=flamegraph;event=cpu;dir=jmh-tests/target/benchmarks/async-profiler"'
fi

# Run benchmark with profiling (from project root)
java -jar jmh-tests/target/benchmarks.jar <BenchmarkClass> -prof gc $ASYNC_PROF
```

**Note**: Always run with `-prof gc`. Add async-profiler flamegraph output only if async-profiler library is available at `$JAVA_HOME/lib/`.

**Profiling Mode**: Default is CPU (`event=cpu`). For allocation profiling, replace `event=cpu` with `event=alloc` in the ASYNC_PROF variable. Only one async-profiler event type can be used per run.

**Async Profiler Output**: HTML flamegraphs are generated at `jmh-tests/target/benchmarks/async-profiler/<SimpleClassName>.<method>/`

## Available Benchmarks

List all benchmarks:
```bash
java -jar target/benchmarks.jar -l
```

## Reading Results

```
Benchmark                      Mode  Cnt     Score    Error  Units
BenchmarkClass.baseline        avgt   10  2137.951 ± 20.705  ms/op
BenchmarkClass.optimized       avgt   10   130.051 ±  2.890  ms/op
```

- **Score**: Average time per operation
- **Error**: 99.9% confidence interval
- **Cnt**: Number of measurement iterations

## Generate Results Report

**IMPORTANT**: After running benchmarks, always generate a markdown report with results and recommendations.

### Output Location
```
jmh-tests/target/benchmarks/<ClassName>-<timestamp>.md
```

Example: `jmh-tests/target/benchmarks/FlamegraphBenchmark-2025-01-06_16-45-00.md`

### Report Template

```markdown
# Benchmark Results: <ClassName>
**Date**: <timestamp>
**JVM**: JDK <version>
**Hardware**: <brief description if relevant>

## Results Summary

| Benchmark Method | Score | Error | Units |
|------------------|-------|-------|-------|
| baseline | X | ±Y | ms/op |
| optimized | Z | ±W | ms/op |

## Performance Analysis

### Key Findings
- [List main observations from the benchmark results]
- [Note any significant performance differences between methods]

### Speedup Summary
| Comparison | Baseline | Optimized | Speedup |
|------------|----------|-----------|---------|
| methodA vs methodB | X ms | Y ms | ~Nx |

## Profiling Data

### GC Statistics
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

### Immediate Actions
1. [Actionable recommendation based on results]
2. [Another recommendation]

### Further Investigation
- [Areas that need more profiling or analysis]
- [Potential optimizations to explore]

## Raw Output

<details>
<summary>Full JMH Output</summary>

```
[Paste relevant JMH output here]
```

</details>
```

### Execution Steps

1. Run the benchmark and capture output
2. Create the report directory: `mkdir -p jmh-tests/target/benchmarks`
3. Generate the markdown file with timestamp
4. Include analysis and actionable recommendations

## Related Skills

- **create-benchmark**: Create new benchmark classes
- **execute-benchmark-and-optimize**: Run optimization iterations with verification
