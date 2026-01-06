# Execute Benchmark

Run a JMH benchmark and display results. Use this skill to quickly measure performance without optimization workflow.

**Requires**: Java 25 (use `sdk use java 25.0.1-amzn` if using SDKMAN)

## Mandatory Requirements

**ALWAYS** follow these requirements when executing benchmarks:

1. **ALWAYS use `-prof gc`** - GC profiler for allocation and GC statistics
2. **ALWAYS use `-prof async`** - Async-profiler for CPU flamegraphs (when available at `$JAVA_HOME/lib/`)
3. **ALWAYS generate the final report** - Markdown report at `jmh-tests/target/benchmarks/<ClassName>-<timestamp>.md`

## Usage

```bash
# Build the benchmark jar (from project root)
mvn package -pl jmh-tests -am -DskipTests -q

# Create output directory for async-profiler flamegraphs
mkdir -p jmh-tests/target/benchmarks/async-profiler

# Run benchmark with profiling (from project root)
# Always include -prof gc for GC statistics
# Include -prof async when libasyncProfiler.dylib exists in $JAVA_HOME/lib/
java -jar jmh-tests/target/benchmarks.jar <BenchmarkClass> \
  -prof gc \
  -prof "async:output=flamegraph;event=cpu;dir=jmh-tests/target/benchmarks/async-profiler"
```

**Note**: The async-profiler is available in Amazon Corretto JDK 25 at `$JAVA_HOME/lib/libasyncProfiler.dylib`. If using a different JDK without async-profiler, omit the `-prof async` option.

**MANDATORY**: Always run with `-prof gc` AND `-prof async` (when async-profiler is available at `$JAVA_HOME/lib/`). Never skip profiling.

**Profiling Mode**: Default is CPU (`event=cpu`). For allocation profiling, replace `event=cpu` with `event=alloc`. Only one async-profiler event type can be used per run.

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

## Results Folder Structure

```
jmh-tests/target/benchmarks/
├── <ClassName>-<timestamp>.md              # Benchmark results report
└── async-profiler/
    └── <FullClassName>.<method>-AverageTime/
        ├── flame-cpu-forward.html          # CPU flamegraph (forward)
        └── flame-cpu-reverse.html          # CPU flamegraph (reverse)
```

## Generate Results Report

**MANDATORY**: After running benchmarks, ALWAYS generate a markdown report with results and recommendations. This is NOT optional.

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
