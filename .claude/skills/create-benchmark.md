# JMH Benchmark Creation Guide

This skill provides guidelines for creating JMH (Java Microbenchmark Harness) benchmarks for any code area in the Jeffrey project.

## When to Use This Guide

Use this guide when you need to:
- Measure performance of a specific code path
- Compare performance before/after optimization
- Identify performance bottlenecks
- Validate performance improvements

## Quick Start

### 1. Create Benchmark Class

Location: `/jmh-tests/src/main/java/pbouda/jeffrey/jmh/`

```java
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 10, time = 1)
@Fork(value = 1, jvmArgs = {"-Xms2g", "-Xmx2g"})
public class YourBenchmark {

    // For verification (loads baseline from repository)
    private final YourBenchmarkVerification verification = new YourBenchmarkVerification();
    private YourResult lastResult;

    @TearDown(Level.Invocation)
    public void verifyInvocation() {
        verification.verify(lastResult);
        lastResult = null;
    }

    @Benchmark
    public List<YourRecord> dataLoadPhase() {
        return loadData();
    }

    @Benchmark
    public YourResult processingPhase() {
        lastResult = processData(cachedRecords);
        return lastResult;
    }

    @Benchmark
    public YourOutput fullPipeline(Blackhole blackhole) {
        List<YourRecord> records = loadData();
        blackhole.consume(records.size());

        lastResult = processData(records);
        blackhole.consume(lastResult);

        return buildOutput(lastResult);
    }

    /**
     * Run from jmh-tests directory:
     * java -cp target/benchmarks.jar pbouda.jeffrey.jmh.YourBenchmark
     */
    static void main() throws RunnerException {
        Options options = new OptionsBuilder()
                .include(YourBenchmark.class.getSimpleName())
                .build();
        new Runner(options).run();
    }
}
```

### 2. Create Verification Class (Recommended)

Verification classes load baselines from the repository at `data/baseline/<ClassName>/`:

```java
/**
 * Verifies benchmark output against a baseline stored in the repository.
 * Baselines are located at: data/baseline/{BenchmarkClassName}/
 */
public class YourBenchmarkVerification {

    private static final Path BASELINE_DIR = Path.of("data/baseline/YourBenchmark");
    private static final Path BASELINE_HASH_FILE = BASELINE_DIR.resolve("baseline.sha256");

    private final String baselineHash;

    public YourBenchmarkVerification() {
        this.baselineHash = loadBaseline();
    }

    private static String loadBaseline() {
        try {
            if (!Files.exists(BASELINE_HASH_FILE)) {
                throw new IllegalStateException(
                        "Baseline not found at: " + BASELINE_HASH_FILE.toAbsolutePath());
            }
            return Files.readString(BASELINE_HASH_FILE).trim();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load baseline", e);
        }
    }

    public void verify(YourResult result) {
        if (result == null) {
            return;
        }

        String currentHash = sha256(serializeToJson(result));
        if (!baselineHash.equals(currentHash)) {
            throw new IllegalStateException(
                    "VERIFICATION FAILED! Output differs from baseline.\n" +
                            "Baseline: " + baselineHash + "\n" +
                            "Current:  " + currentHash);
        }
    }

    private static String sha256(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}
```

### 3. Generate Baseline Data

Create a utility class to generate baseline data for the repository:

```java
/**
 * Generates baseline data for YourBenchmark verification.
 * Run once to create baseline files in data/baseline/YourBenchmark/
 */
public class YourBenchmarkBaselineGenerator {

    private static final Path BASELINE_DIR = Path.of("data/baseline/YourBenchmark");

    public static void main(String[] args) throws IOException {
        // Generate your result using the same logic as the benchmark
        YourResult result = generateResult();

        // Serialize and hash
        byte[] jsonBytes = serializeToJson(result);
        String hash = sha256(jsonBytes);

        // Write baseline files
        Files.createDirectories(BASELINE_DIR);
        Files.write(BASELINE_DIR.resolve("baseline.json"), jsonBytes);
        Files.writeString(BASELINE_DIR.resolve("baseline.sha256"), hash);

        System.out.println("[Baseline] Generated:");
        System.out.println("  Path: " + BASELINE_DIR.toAbsolutePath());
        System.out.println("  SHA-256: " + hash);
    }

    private static String sha256(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}
```

Run the generator from jmh-tests directory:
```bash
java -cp target/benchmarks.jar pbouda.jeffrey.jmh.YourBenchmarkBaselineGenerator
git add data/baseline/YourBenchmark/
```

### 4. Add Dependencies

If the benchmark needs additional dependencies, add them to `/jmh-tests/pom.xml`:

```xml
<dependency>
    <groupId>pbouda.jeffrey</groupId>
    <artifactId>your-module</artifactId>
    <version>${project.version}</version>
</dependency>
```

### 5. Build and Run

**Requires**: Java 25 (use `sdk use java 25.0.1-amzn` if using SDKMAN)

```bash
# From project root - build the benchmark jar
mvn package -pl jmh-tests -am -DskipTests -q

# Run from jmh-tests directory (important for relative paths)
cd jmh-tests
java -jar target/benchmarks.jar YourBenchmark
```

Or run via main method:
```bash
java -cp target/benchmarks.jar pbouda.jeffrey.jmh.YourBenchmark
```

---

## Benchmark Patterns

### Database Query Pattern

Use a `Supplier` for repeatable data loading:

```java
private static final Path DATABASE_PATH = Path.of("data/your-database.db");

private static final Supplier<List<YourRecord>> DATA_SUPPLIER = () -> {
    MapSqlParameterSource queryParams = new MapSqlParameterSource()
            .addValue("param1", "value1")
            .addValue("param2", null);

    SimpleJdbcDataSource datasource = new SimpleJdbcDataSource(
            "jdbc:duckdb:" + DATABASE_PATH.toAbsolutePath());
    NamedParameterJdbcTemplate jdbcTemplate = new NamedParameterJdbcTemplate(datasource);

    return jdbcTemplate.query(YOUR_SQL, queryParams, new YourRowMapper());
};
```

### Multi-Phase Pipeline Pattern

Separate benchmarks for each phase plus full pipeline:

```java
// Phase 1: Data loading
@Benchmark
public List<Record> queryPhase() {
    return DATA_SUPPLIER.get();
}

// Phase 2: Processing (uses cached data from setup)
@Benchmark
public Result processingPhase() {
    lastResult = process(cachedRecords);
    return lastResult;
}

// Phase 3: Output building (uses cached result from setup)
@Benchmark
public Output outputPhase() {
    return buildOutput(cachedResult);
}

// Full pipeline
@Benchmark
public Output fullPipeline(Blackhole blackhole) {
    List<Record> records = DATA_SUPPLIER.get();
    blackhole.consume(records.size());

    lastResult = process(records);
    blackhole.consume(lastResult);

    return buildOutput(lastResult);
}
```

---

## Benchmark Configuration

### JMH Annotations

| Annotation | Purpose | Recommended Value |
|------------|---------|-------------------|
| `@BenchmarkMode` | What to measure | `Mode.AverageTime` for latency |
| `@OutputTimeUnit` | Result time unit | `TimeUnit.MILLISECONDS` |
| `@Warmup` | JIT warmup iterations | `iterations = 5, time = 1` |
| `@Measurement` | Measurement iterations | `iterations = 10, time = 1` |
| `@Fork` | Separate JVM processes | `value = 1` |
| `@State` | State sharing scope | `Scope.Benchmark` |

### State Levels

| Level | When Called |
|-------|-------------|
| `Level.Trial` | Once per benchmark run |
| `Level.Iteration` | Once per iteration |
| `Level.Invocation` | Once per method call (expensive!) |

---

## Baseline Data Structure

Baselines are stored in the repository for reproducible verification:

```
/jmh-tests/data/baseline/
└── YourBenchmark/
    ├── baseline.json       # Serialized output for debugging
    └── baseline.sha256     # Hash for verification
```

Each benchmark class has its own baseline directory. The `.sha256` file contains just the hash, while `.json` provides human-readable output for debugging verification failures.

---

## Command Line Options

```bash
# Run specific benchmark
java -jar target/benchmarks.jar YourBenchmark

# Run specific method
java -jar target/benchmarks.jar "YourBenchmark.specificMethod"

# More warmup/measurement
java -jar target/benchmarks.jar -wi 10 -i 20

# JSON output
java -jar target/benchmarks.jar -rf json -rff results.json

# Profile with async-profiler
java -jar target/benchmarks.jar -prof async

# List available benchmarks
java -jar target/benchmarks.jar -l
```

---

## Interpreting Results

JMH output format:
```
Benchmark                          Mode  Cnt    Score   Error  Units
YourBenchmark.queryPhase          avgt   10  2127.123 ± 51.234  ms/op
YourBenchmark.processingPhase     avgt   10    42.401 ±  0.388  ms/op
YourBenchmark.fullPipeline        avgt   10  2171.234 ± 52.000  ms/op
```

- **Score**: Average time per operation
- **Error**: 99.9% confidence interval
- **Cnt**: Number of measurement iterations

### Calculating Improvement

```
Improvement = (baseline - optimized) / baseline * 100%
```

Example: baseline=2127ms, optimized=1500ms
```
Improvement = (2127 - 1500) / 2127 * 100% = 29.5% faster
```

---

## Troubleshooting

### "Database not found"
Run benchmark from `/jmh-tests` directory, not project root.

### Results too variable
Increase warmup iterations or fix heap size with `-Xms` and `-Xmx`.

### Java version mismatch
Ensure Java 25 is active:
```bash
sdk use java 25.0.1-amzn
java -jar target/benchmarks.jar
```

### Native library warnings
Add JVM arg: `--enable-native-access=ALL-UNNAMED`

---

## Reference Implementation

See `FlamegraphBenchmark.java` and `FlamegraphBenchmarkVerification.java` for a complete example with:
- Baseline vs optimized comparison benchmarks
- Verification using repository-stored baselines
- Database query pattern with DuckDB
- Simplified main method for execution

---

## Related Skills

- **execute-benchmark**: Run a benchmark and see results
- **execute-benchmark-and-optimize**: Run optimization iterations with verification
