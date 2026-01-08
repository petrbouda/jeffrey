# JMH Performance Tests

This module contains JMH (Java Microbenchmark Harness) benchmarks for testing flamegraph query performance optimizations.

## Prerequisites

- Java 25
- Maven

## Quick Start

### 1. Initialize the Database

Before running benchmarks, you need to initialize the DuckDB database from the JFR file:

```bash
# From project root directory
mvn -pl jmh-tests compile exec:java -Dexec.mainClass="pbouda.jeffrey.jmh.JmhDatabaseInitializer"
```

Or run `JmhDatabaseInitializer.main()` directly from your IDE.

This parses the compressed JFR file from `jfr/` and creates the database in `data/profile-data.db`.

### 2. Generate Baselines

After initializing the database, generate baseline files for benchmark verification:

```bash
# From project root directory
mvn -pl jmh-tests exec:java -Dexec.mainClass="pbouda.jeffrey.jmh.JmhBaselineGenerator"
```

Or run `JmhBaselineGenerator.main()` directly from your IDE.

This creates baseline hash files in `data/baseline/` for each benchmark. These baselines are used to verify that benchmark results remain consistent across code changes.

### 3. Build the Benchmark JAR

```bash
mvn -pl jmh-tests package
```

### 4. Run Benchmarks

```bash
# Run all benchmarks
java -jar jmh-tests/target/benchmarks.jar

# Run a specific benchmark
java -jar jmh-tests/target/benchmarks.jar FlamegraphSimpleBenchmark
```

Or run benchmark main methods directly from your IDE.

## Full Initialization (One Command)

To initialize everything from scratch:

```bash
# From project root directory
mvn -pl jmh-tests compile exec:java -Dexec.mainClass="pbouda.jeffrey.jmh.JmhDatabaseInitializer" && \
mvn -pl jmh-tests exec:java -Dexec.mainClass="pbouda.jeffrey.jmh.JmhBaselineGenerator"
```

## Directory Structure

```
jmh-tests/
├── jfr/                          # JFR recording files (tracked in Git)
│   └── jeffrey-persons-direct-serde-cpu.jfr.lz4
├── data/                         # Generated files (NOT in Git)
│   ├── profile-data.db           # DuckDB database
│   └── baseline/                 # Baseline verification files
│       ├── SimpleFlamegraphBenchmark/
│       ├── FlamegraphBenchmark/
│       ├── ByThreadFlamegraphBenchmark/
│       └── ByThreadAndWeightFlamegraphBenchmark/
├── src/main/java/
│   └── pbouda/jeffrey/jmh/
│       ├── JmhDatabaseInitializer.java   # Database initialization utility
│       ├── JmhBaselineGenerator.java     # Baseline generation utility
│       └── flamegraph/                   # Flamegraph benchmarks
└── pom.xml
```

## Available Benchmarks

- **FlamegraphSimpleBenchmark** - Simple flamegraph query aggregating all events per stacktrace
- **FlamegraphByWeightBenchmark** - Flamegraph with weight entity grouping
- **FlamegraphByThreadBenchmark** - Flamegraph with thread grouping
- **FlamegraphByThreadAndWeightBenchmark** - Flamegraph with both thread and weight entity grouping

## Benchmark Verification

Each benchmark invocation is verified against baseline hashes to ensure consistent results. If the output differs from the baseline, the benchmark will fail with details about the mismatch.

Baseline files:
- `baseline-frame.murmur3` - Hash of the frame tree for quick verification
- `baseline-frame.json` - JSON representation for debugging

## Notes

- The `data/` directory is not tracked in Git - regenerate locally after cloning
- The JFR file is LZ4 compressed (~16 MB)
- Database initialization takes a few seconds
- Baseline generation takes about 1-2 seconds
