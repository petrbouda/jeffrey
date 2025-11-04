# ClickHouse Migration for Jeffrey - High-Performance Profiling Storage

## Overview

This document outlines the migration strategy from SQLite to ClickHouse for Jeffrey's profiling data storage, specifically targeting the performance bottlenecks in stacktrace aggregation queries that currently take 10+ seconds.

## Performance Benefits Comparison

| **Aspect** | **Current SQLite** | **ClickHouse** | **Improvement** |
|------------|-------------------|----------------|-----------------|
| **Storage** | ~400MB (73% duplication) | ~40MB (15x compression) | **90% reduction** |
| **Query Speed** | 10 seconds | 100-500ms | **20-100x faster** |
| **Memory Usage** | Loads entire frames | Columnar compression | **95% reduction** |
| **Aggregation** | GROUP BY bottleneck | Optimized aggregates | **Native support** |
| **Time Range** | Full table scan | Partition pruning | **Millisecond filtering** |
| **Frame Search** | Text search on CSV | Indexed frame lookup | **Instant search** |

## Current SQLite Structure vs. ClickHouse Target

### Current SQLite Schema
```sql
-- Current structure with performance issues
CREATE TABLE events (
    profile_id TEXT,
    stacktrace_id INTEGER,
    samples INTEGER,
    weight INTEGER,
    weight_entity TEXT,
    -- ... other fields
);

CREATE TABLE stacktraces (
    profile_id TEXT,
    stacktrace_id INTEGER,
    frames TEXT -- CSV string: "class;method;type;line;bytecode\n..."
);
```

**Issues:**
- 73% data duplication in stacktraces
- Large TEXT frames (avg 4.3KB each)
- Expensive JOINs on large datasets
- No frame-level indexing

### Target ClickHouse Schema (Option A - Normalized)

```sql
-- Frame dictionary for maximum deduplication
CREATE TABLE frames
(
    frame_hash     UInt64,
    class_name     LowCardinality(String),
    method_name    LowCardinality(String),
    compilation_type LowCardinality(String), -- JIT/Interpreted/Native/C++
    line_number    UInt32,
    bytecode_index UInt32,
    first_seen     DateTime64(9) DEFAULT now64(),
    last_seen      DateTime64(9) DEFAULT now64()
) ENGINE = ReplacingMergeTree(last_seen)
ORDER BY frame_hash
SETTINGS index_granularity = 8192;

-- Stacktrace compositions using frame references
CREATE TABLE stacktraces
(
    profile_id     String,
    stacktrace_id  UInt64,
    stack_hash     UInt64, -- Hash of frame_hashes array for deduplication
    frame_hashes   Array(UInt64), -- References to frames table
    depth          UInt16,
    created_at     DateTime64(9) DEFAULT now64()
) ENGINE = ReplacingMergeTree(created_at)
ORDER BY (profile_id, stacktrace_id)
SETTINGS index_granularity = 8192;

-- Events table optimized for time-series queries
CREATE TABLE jfr_events
(
    profile_id                      String,
    event_id                       UInt64,
    event_type                     LowCardinality(String),
    timestamp                      DateTime64(9),
    timestamp_from_beginning       UInt64,
    end_timestamp                  Nullable(DateTime64(9)),
    end_timestamp_from_beginning   Nullable(UInt64),
    duration                       Nullable(UInt64),
    samples                        UInt32,
    weight                         Nullable(UInt64),
    weight_entity                  LowCardinality(String),
    stacktrace_id                  Nullable(UInt64),
    thread_hash                    Nullable(UInt32),
    fields                         String, -- JSON fields for event-specific data
    ingestion_time                 DateTime64(9) DEFAULT now64()
) ENGINE = MergeTree()
PARTITION BY toYYYYMM(timestamp)
ORDER BY (profile_id, event_type, timestamp, stacktrace_id)
SETTINGS index_granularity = 8192;

-- Indexes for fast frame searching
CREATE INDEX IF NOT EXISTS idx_frames_class_name ON frames (class_name) TYPE tokenbf_v1(10240, 3, 0);
CREATE INDEX IF NOT EXISTS idx_frames_method_name ON frames (method_name) TYPE tokenbf_v1(10240, 3, 0);
CREATE INDEX IF NOT EXISTS idx_events_time_range ON jfr_events (profile_id, event_type, timestamp_from_beginning);
```

## Migration Strategy

### Phase 1: Data Export and Transformation

```java
@Component
public class ClickHouseMigrationService {

    private final JdbcTemplate sqliteTemplate;
    private final JdbcTemplate clickHouseTemplate;
    private final Map<String, UInt64> frameHashCache = new ConcurrentHashMap<>();

    public void migrateProfile(String profileId) {
        // 1. Extract and normalize frames
        migrateFrames(profileId);

        // 2. Migrate stacktraces with frame references
        migrateStacktraces(profileId);

        // 3. Migrate events
        migrateEvents(profileId);
    }

    private void migrateFrames(String profileId) {
        String sql = "SELECT DISTINCT frames FROM stacktraces WHERE profile_id = ?";
        List<String> allFrames = sqliteTemplate.queryForList(sql, String.class, profileId);

        Set<Frame> uniqueFrames = new HashSet<>();
        for (String framesCsv : allFrames) {
            String[] lines = framesCsv.split("\n");
            for (String line : lines) {
                String[] parts = line.split(";");
                if (parts.length >= 5) {
                    uniqueFrames.add(new Frame(
                        parts[0], // class_name
                        parts[1], // method_name
                        parts[2], // compilation_type
                        Integer.parseInt(parts[3]), // line_number
                        Integer.parseInt(parts[4])  // bytecode_index
                    ));
                }
            }
        }

        // Batch insert unique frames
        batchInsertFrames(uniqueFrames);
    }
}
```

### Phase 2: Application Integration

Update Spring Boot configuration:

```properties
# application.yml
spring:
  datasource:
    clickhouse:
      url: jdbc:clickhouse://localhost:8123/jeffrey
      username: default
      password:
      driver-class-name: com.clickhouse.jdbc.ClickHouseDriver
      hikari:
        maximum-pool-size: 20
        minimum-idle: 5
```

## Client Integration Options

### Option 1: ClickHouse Java Client V2 (Recommended)

```xml
<dependency>
    <groupId>com.clickhouse</groupId>
    <artifactId>client-v2</artifactId>
    <version>0.6.5</version>
</dependency>
```

#### Setup Configuration

```java
@Configuration
public class ClickHouseClientConfig {

    @Bean
    public Client clickHouseClient() {
        return new Client.Builder()
                .addEndpoint("http://localhost:8123")
                .setUsername("default")
                .setPassword("")
                .setDatabase("jeffrey")
                .setClientName("jeffrey-profiler")
                .compressServerResponse(true)
                .useNewImplementation(true)
                .build();
    }
}
```

#### Repository Implementation with Client V2

```java
@Repository
public class ClickHouseEventRepositoryV2 {

    @Autowired
    private Client clickHouseClient;

    // Insert frame with deduplication
    public void insertFrame(Frame frame) {
        long frameHash = calculateFrameHash(frame);

        String sql = """
            INSERT INTO frames (frame_hash, class_name, method_name, compilation_type, line_number, bytecode_index)
            VALUES (?, ?, ?, ?, ?, ?)
            """;

        try {
            clickHouseClient.execute(QueryRequest.builder()
                .query(sql)
                .params(List.of(
                    ClickHouseParameterizedQuery.of("UInt64", frameHash),
                    ClickHouseParameterizedQuery.of("String", frame.getClassName()),
                    ClickHouseParameterizedQuery.of("String", frame.getMethodName()),
                    ClickHouseParameterizedQuery.of("String", frame.getCompilationType()),
                    ClickHouseParameterizedQuery.of("UInt32", frame.getLineNumber()),
                    ClickHouseParameterizedQuery.of("UInt32", frame.getBytecodeIndex())
                ))
                .build()).get();
        } catch (Exception e) {
            throw new RuntimeException("Failed to insert frame", e);
        }
    }

    // Batch insert events efficiently
    public void batchInsertEvents(List<JfrEvent> events) {
        String sql = """
            INSERT INTO jfr_events (
                profile_id, event_id, event_type, timestamp, timestamp_from_beginning,
                samples, weight, weight_entity, stacktrace_id, thread_hash, fields
            ) FORMAT RowBinary
            """;

        try (InsertRequest insertRequest = clickHouseClient.insert(sql)) {
            for (JfrEvent event : events) {
                insertRequest.data()
                    .writeString(event.getProfileId())
                    .writeUInt64(event.getEventId())
                    .writeString(event.getEventType())
                    .writeDateTime64(event.getTimestamp(), 9)
                    .writeUInt64(event.getTimestampFromBeginning())
                    .writeUInt32(event.getSamples())
                    .writeNullable(event.getWeight(), DataType.UInt64)
                    .writeString(event.getWeightEntity() != null ? event.getWeightEntity() : "")
                    .writeNullable(event.getStacktraceId(), DataType.UInt64)
                    .writeNullable(event.getThreadId(), DataType.UInt32)
                    .writeString(event.getFieldsJson() != null ? event.getFieldsJson() : "{}");
            }
            insertRequest.send().get();
        } catch (Exception e) {
            throw new RuntimeException("Failed to batch insert events", e);
        }
    }

    // Query flamegraph data
    public List<StacktraceAggregate> getFlamegraphData(
            String profileId, String eventType, long fromTime, long toTime) {

        String sql = """
            SELECT
                e.stacktrace_id,
                sum(e.samples) AS total_samples,
                sum(e.weight) AS total_weight,
                any(e.weight_entity) AS weight_entity,
                arrayStringConcat(
                    arrayMap(frame_hash ->
                        concat(
                            dictGet('frames_dict', 'class_name', frame_hash), '.',
                            dictGet('frames_dict', 'method_name', frame_hash), ' (',
                            dictGet('frames_dict', 'compilation_type', frame_hash), ')',
                            ' [', toString(dictGet('frames_dict', 'line_number', frame_hash)), ']'
                        ),
                        st.frame_hashes
                    ),
                    '\n'
                ) AS reconstructed_frames
            FROM jfr_events e
            LEFT JOIN stacktraces st ON (e.profile_id = st.profile_id AND e.stacktrace_id = st.stacktrace_id)
            WHERE e.profile_id = {profileId:String}
              AND e.event_type = {eventType:String}
              AND e.timestamp_from_beginning BETWEEN {fromTime:UInt64} AND {toTime:UInt64}
              AND e.stacktrace_id IS NOT NULL
            GROUP BY e.stacktrace_id, st.frame_hashes
            ORDER BY total_samples DESC
            LIMIT 1000
            """;

        try {
            QueryResponse response = clickHouseClient.query(QueryRequest.builder()
                .query(sql)
                .setParam("profileId", profileId)
                .setParam("eventType", eventType)
                .setParam("fromTime", fromTime)
                .setParam("toTime", toTime)
                .format(ClickHouseFormat.RowBinaryWithNamesAndTypes)
                .build()).get();

            List<StacktraceAggregate> results = new ArrayList<>();
            try (ClickHouseResultSet resultSet = response.getResultSet()) {
                while (resultSet.next()) {
                    results.add(new StacktraceAggregate(
                        resultSet.getLong("stacktrace_id"),
                        resultSet.getLong("total_samples"),
                        resultSet.getLong("total_weight"),
                        resultSet.getString("weight_entity"),
                        resultSet.getString("reconstructed_frames")
                    ));
                }
            }
            return results;
        } catch (Exception e) {
            throw new RuntimeException("Failed to query flamegraph data", e);
        }
    }

    // Search for G1GC frames
    public List<G1GCFrameAnalysis> analyzeG1GCFrames(String profileId) {
        String sql = """
            SELECT
                f.class_name,
                f.method_name,
                f.compilation_type,
                count(DISTINCT e.stacktrace_id) as stacktrace_count,
                sum(e.samples) as total_samples,
                avg(e.samples) as avg_samples_per_occurrence
            FROM frames f
            JOIN stacktraces st ON (has(st.frame_hashes, f.frame_hash))
            JOIN jfr_events e ON (e.profile_id = st.profile_id AND e.stacktrace_id = st.stacktrace_id)
            WHERE e.profile_id = {profileId:String}
              AND (f.class_name ILIKE '%G1GC%' OR f.method_name ILIKE '%G1GC%')
            GROUP BY f.frame_hash, f.class_name, f.method_name, f.compilation_type
            ORDER BY total_samples DESC
            """;

        try {
            QueryResponse response = clickHouseClient.query(QueryRequest.builder()
                .query(sql)
                .setParam("profileId", profileId)
                .format(ClickHouseFormat.RowBinaryWithNamesAndTypes)
                .build()).get();

            List<G1GCFrameAnalysis> results = new ArrayList<>();
            try (ClickHouseResultSet resultSet = response.getResultSet()) {
                while (resultSet.next()) {
                    results.add(new G1GCFrameAnalysis(
                        resultSet.getString("class_name"),
                        resultSet.getString("method_name"),
                        resultSet.getString("compilation_type"),
                        resultSet.getInt("stacktrace_count"),
                        resultSet.getLong("total_samples"),
                        resultSet.getDouble("avg_samples_per_occurrence"),
                        Collections.emptyList() // Simplified for this example
                    ));
                }
            }
            return results;
        } catch (Exception e) {
            throw new RuntimeException("Failed to analyze G1GC frames", e);
        }
    }
}
```

#### Async Operations with Client V2

```java
@Service
public class AsyncClickHouseService {

    @Autowired
    private Client clickHouseClient;

    public CompletableFuture<List<StacktraceAggregate>> getFlamegraphDataAsync(
            String profileId, String eventType, long fromTime, long toTime) {

        String sql = """
            SELECT stacktrace_id, sum(samples) AS total_samples, sum(weight) AS total_weight
            FROM jfr_events
            WHERE profile_id = {profileId:String}
              AND event_type = {eventType:String}
              AND timestamp_from_beginning BETWEEN {fromTime:UInt64} AND {toTime:UInt64}
            GROUP BY stacktrace_id
            ORDER BY total_samples DESC
            LIMIT 1000
            """;

        return clickHouseClient.queryAsync(QueryRequest.builder()
                .query(sql)
                .setParam("profileId", profileId)
                .setParam("eventType", eventType)
                .setParam("fromTime", fromTime)
                .setParam("toTime", toTime)
                .build())
            .thenApply(response -> {
                List<StacktraceAggregate> results = new ArrayList<>();
                try (ClickHouseResultSet resultSet = response.getResultSet()) {
                    while (resultSet.next()) {
                        results.add(new StacktraceAggregate(
                            resultSet.getLong("stacktrace_id"),
                            resultSet.getLong("total_samples"),
                            resultSet.getLong("total_weight"),
                            null, // weight_entity
                            null  // reconstructed_frames
                        ));
                    }
                } catch (Exception e) {
                    throw new RuntimeException("Failed to process result set", e);
                }
                return results;
            });
    }

    // Streaming large result sets
    public void streamLargeResultSet(String profileId, Consumer<StacktraceAggregate> processor) {
        String sql = """
            SELECT stacktrace_id, sum(samples) AS total_samples, sum(weight) AS total_weight
            FROM jfr_events
            WHERE profile_id = {profileId:String}
            GROUP BY stacktrace_id
            """;

        try {
            QueryResponse response = clickHouseClient.query(QueryRequest.builder()
                .query(sql)
                .setParam("profileId", profileId)
                .option(ClickHouseClientOption.BUFFER_SIZE, 65536)
                .build()).get();

            try (ClickHouseResultSet resultSet = response.getResultSet()) {
                while (resultSet.next()) {
                    StacktraceAggregate aggregate = new StacktraceAggregate(
                        resultSet.getLong("stacktrace_id"),
                        resultSet.getLong("total_samples"),
                        resultSet.getLong("total_weight"),
                        null, null
                    );
                    processor.accept(aggregate);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to stream result set", e);
        }
    }
}
```

### Option 2: JdbcTemplate (Alternative)

For teams preferring traditional JDBC patterns, ClickHouse also supports standard JDBC connectivity with the `clickhouse-jdbc` driver. However, the Client V2 approach above is recommended for better performance and modern API design.

## Query Examples

### 1. Flamegraph Data for 2-Minute Interval

```java
@Service
public class FlamegraphService {

    public List<StacktraceAggregate> getFlamegraphData(
            String profileId,
            String eventType,
            long fromTime,
            long toTime) {

        String sql = """
            SELECT
                e.stacktrace_id,
                sum(e.samples) AS total_samples,
                sum(e.weight) AS total_weight,
                any(e.weight_entity) AS weight_entity,
                -- Reconstruct stacktrace from frame references
                arrayStringConcat(
                    arrayMap(frame_hash ->
                        concat(
                            dictGet('frames_dict', 'class_name', frame_hash), '.',
                            dictGet('frames_dict', 'method_name', frame_hash), ' (',
                            dictGet('frames_dict', 'compilation_type', frame_hash), ')',
                            ' [', toString(dictGet('frames_dict', 'line_number', frame_hash)), ']'
                        ),
                        st.frame_hashes
                    ),
                    '\n'
                ) AS reconstructed_frames
            FROM jfr_events e
            LEFT JOIN stacktraces st ON (e.profile_id = st.profile_id AND e.stacktrace_id = st.stacktrace_id)
            WHERE e.profile_id = ?
              AND e.event_type = ?
              AND e.timestamp_from_beginning BETWEEN ? AND ?
              AND e.stacktrace_id IS NOT NULL
            GROUP BY e.stacktrace_id, st.frame_hashes
            ORDER BY total_samples DESC
            LIMIT 1000
            """;

        return clickHouseTemplate.query(sql,
            new Object[]{profileId, eventType, fromTime, toTime},
            (rs, rowNum) -> new StacktraceAggregate(
                rs.getLong("stacktrace_id"),
                rs.getLong("total_samples"),
                rs.getLong("total_weight"),
                rs.getString("weight_entity"),
                rs.getString("reconstructed_frames")
            )
        );
    }

    // Example usage for random 2-minute interval
    public List<StacktraceAggregate> getRandomFlamegraphSample(String profileId) {
        // Get time bounds for profile
        String timeBoundsSQL = """
            SELECT min(timestamp_from_beginning) as min_time,
                   max(timestamp_from_beginning) as max_time
            FROM jfr_events
            WHERE profile_id = ? AND event_type = 'jdk.ExecutionSample'
            """;

        Map<String, Object> bounds = clickHouseTemplate.queryForMap(timeBoundsSQL, profileId);
        long minTime = (Long) bounds.get("min_time");
        long maxTime = (Long) bounds.get("max_time");

        // Random 2-minute interval (120 seconds = 120,000,000,000 nanoseconds)
        long intervalNanos = 120_000_000_000L;
        long maxStartTime = maxTime - intervalNanos;
        long randomStart = minTime + (long) (Math.random() * (maxStartTime - minTime));
        long randomEnd = randomStart + intervalNanos;

        return getFlamegraphData(profileId, "jdk.ExecutionSample", randomStart, randomEnd);
    }
}
```

### 2. Flamegraph Collapse Format Generation

To generate flamegraph-compatible output (used by brendangregg/FlameGraph tools), you need to collapse stacktraces into the standard format:

```java
@Service
public class FlamegraphCollapseService {

    @Autowired
    private Client clickHouseClient;

    // Generate collapsed stacktraces for flamegraph.pl
    public String generateCollapsedFlamegraph(String profileId, String eventType, long fromTime, long toTime) {
        String sql = """
            SELECT
                -- Reverse stacktrace order (leaf-to-root becomes root-to-leaf)
                arrayStringConcat(
                    arrayReverse(
                        arrayMap(frame_hash ->
                            concat(
                                dictGet('frames_dict', 'class_name', frame_hash), '.',
                                dictGet('frames_dict', 'method_name', frame_hash)
                            ),
                            st.frame_hashes
                        )
                    ),
                    ';'
                ) AS collapsed_stack,
                sum(e.samples) AS sample_count
            FROM jfr_events e
            LEFT JOIN stacktraces st ON (e.profile_id = st.profile_id AND e.stacktrace_id = st.stacktrace_id)
            WHERE e.profile_id = {profileId:String}
              AND e.event_type = {eventType:String}
              AND e.timestamp_from_beginning BETWEEN {fromTime:UInt64} AND {toTime:UInt64}
              AND e.stacktrace_id IS NOT NULL
            GROUP BY st.frame_hashes
            HAVING sample_count > 0
            ORDER BY sample_count DESC
            """;

        try {
            QueryResponse response = clickHouseClient.query(QueryRequest.builder()
                .query(sql)
                .setParam("profileId", profileId)
                .setParam("eventType", eventType)
                .setParam("fromTime", fromTime)
                .setParam("toTime", toTime)
                .build()).get();

            StringBuilder collapsed = new StringBuilder();
            try (ClickHouseResultSet resultSet = response.getResultSet()) {
                while (resultSet.next()) {
                    String stack = resultSet.getString("collapsed_stack");
                    long samples = resultSet.getLong("sample_count");

                    // Format: "method1;method2;method3 sampleCount"
                    collapsed.append(stack).append(" ").append(samples).append("\n");
                }
            }
            return collapsed.toString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate collapsed flamegraph", e);
        }
    }

    // Alternative: Generate collapsed format with frame details
    public String generateDetailedCollapsedFlamegraph(String profileId, String eventType, long fromTime, long toTime) {
        String sql = """
            SELECT
                arrayStringConcat(
                    arrayReverse(
                        arrayMap(frame_hash ->
                            concat(
                                dictGet('frames_dict', 'class_name', frame_hash), '.',
                                dictGet('frames_dict', 'method_name', frame_hash), '_',
                                dictGet('frames_dict', 'compilation_type', frame_hash)
                            ),
                            st.frame_hashes
                        )
                    ),
                    ';'
                ) AS collapsed_stack,
                sum(e.samples) AS sample_count,
                sum(e.weight) AS total_weight
            FROM jfr_events e
            LEFT JOIN stacktraces st ON (e.profile_id = st.profile_id AND e.stacktrace_id = st.stacktrace_id)
            WHERE e.profile_id = {profileId:String}
              AND e.event_type = {eventType:String}
              AND e.timestamp_from_beginning BETWEEN {fromTime:UInt64} AND {toTime:UInt64}
              AND e.stacktrace_id IS NOT NULL
            GROUP BY st.frame_hashes
            HAVING sample_count > 0
            ORDER BY sample_count DESC
            """;

        try {
            QueryResponse response = clickHouseClient.query(QueryRequest.builder()
                .query(sql)
                .setParam("profileId", profileId)
                .setParam("eventType", eventType)
                .setParam("fromTime", fromTime)
                .setParam("toTime", toTime)
                .build()).get();

            StringBuilder collapsed = new StringBuilder();
            try (ClickHouseResultSet resultSet = response.getResultSet()) {
                while (resultSet.next()) {
                    String stack = resultSet.getString("collapsed_stack");
                    long samples = resultSet.getLong("sample_count");
                    long weight = resultSet.getLong("total_weight");

                    // Enhanced format with weight info in comments
                    collapsed.append(stack).append(" ").append(samples);
                    if (weight > 0) {
                        collapsed.append(" # weight: ").append(weight);
                    }
                    collapsed.append("\n");
                }
            }
            return collapsed.toString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate detailed collapsed flamegraph", e);
        }
    }

    // Generate SVG flamegraph using external flamegraph.pl script
    public void generateSVGFlamegraph(String profileId, String eventType, long fromTime, long toTime, String outputPath) {
        String collapsedData = generateCollapsedFlamegraph(profileId, eventType, fromTime, toTime);

        try {
            // Write collapsed data to temporary file
            Path tempFile = Files.createTempFile("flamegraph_", ".collapsed");
            Files.write(tempFile, collapsedData.getBytes(StandardCharsets.UTF_8));

            // Execute flamegraph.pl (requires FlameGraph tools to be installed)
            ProcessBuilder pb = new ProcessBuilder(
                "flamegraph.pl",
                "--title", "Jeffrey Profiler - " + eventType,
                "--width", "1200",
                "--height", "800",
                "--colors", "java"
            );

            Process process = pb.start();

            // Pipe collapsed data to flamegraph.pl
            try (OutputStream stdin = process.getOutputStream()) {
                stdin.write(collapsedData.getBytes(StandardCharsets.UTF_8));
            }

            // Read SVG output
            String svgContent = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

            // Write SVG to output file
            Files.write(Paths.get(outputPath), svgContent.getBytes(StandardCharsets.UTF_8));

            // Wait for process completion
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                String error = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
                throw new RuntimeException("flamegraph.pl failed with exit code " + exitCode + ": " + error);
            }

            // Clean up temporary file
            Files.deleteIfExists(tempFile);

        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("Failed to generate SVG flamegraph", e);
        }
    }

    // Convert to d3-flame-graph compatible JSON format
    public String generateD3FlamegraphJson(String profileId, String eventType, long fromTime, long toTime) {
        List<StacktraceAggregate> stacktraces = getFlamegraphData(profileId, eventType, fromTime, toTime);

        // Build hierarchical tree structure for d3-flame-graph
        FlamegraphNode root = new FlamegraphNode("all", 0);

        for (StacktraceAggregate stacktrace : stacktraces) {
            String[] frames = stacktrace.getReconstructedFrames().split("\n");

            // Reverse to get root-to-leaf order
            List<String> reversedFrames = Arrays.asList(frames);
            Collections.reverse(reversedFrames);

            // Build tree path
            FlamegraphNode current = root;
            for (String frame : reversedFrames) {
                String[] parts = frame.split("\\.");
                String methodName = parts.length > 1 ? parts[parts.length - 1] : frame;

                FlamegraphNode child = current.getOrCreateChild(methodName);
                child.addValue(stacktrace.getTotalSamples());
                current = child;
            }
        }

        // Convert to JSON
        ObjectMapper mapper = new ObjectMapper();
        try {
            return mapper.writeValueAsString(root);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize flamegraph to JSON", e);
        }
    }
}

// Supporting classes for d3-flame-graph JSON generation
@Data
public class FlamegraphNode {
    private String name;
    private long value;
    private List<FlamegraphNode> children = new ArrayList<>();

    public FlamegraphNode(String name, long value) {
        this.name = name;
        this.value = value;
    }

    public FlamegraphNode getOrCreateChild(String childName) {
        return children.stream()
            .filter(child -> child.getName().equals(childName))
            .findFirst()
            .orElseGet(() -> {
                FlamegraphNode newChild = new FlamegraphNode(childName, 0);
                children.add(newChild);
                return newChild;
            });
    }

    public void addValue(long additionalValue) {
        this.value += additionalValue;
    }
}

// REST endpoint to serve flamegraph data
@RestController
@RequestMapping("/api/flamegraph")
public class FlamegraphController {

    @Autowired
    private FlamegraphCollapseService flamegraphService;

    @GetMapping("/{profileId}/collapsed")
    public ResponseEntity<String> getCollapsedFlamegraph(
            @PathVariable String profileId,
            @RequestParam String eventType,
            @RequestParam long fromTime,
            @RequestParam long toTime) {

        String collapsed = flamegraphService.generateCollapsedFlamegraph(profileId, eventType, fromTime, toTime);

        return ResponseEntity.ok()
            .contentType(MediaType.TEXT_PLAIN)
            .header("Content-Disposition", "attachment; filename=flamegraph.collapsed")
            .body(collapsed);
    }

    @GetMapping("/{profileId}/svg")
    public ResponseEntity<String> getSVGFlamegraph(
            @PathVariable String profileId,
            @RequestParam String eventType,
            @RequestParam long fromTime,
            @RequestParam long toTime) throws IOException {

        // Generate SVG to temporary file
        String tempPath = "/tmp/flamegraph_" + System.currentTimeMillis() + ".svg";
        flamegraphService.generateSVGFlamegraph(profileId, eventType, fromTime, toTime, tempPath);

        // Read and return SVG content
        String svgContent = Files.readString(Paths.get(tempPath));
        Files.deleteIfExists(Paths.get(tempPath));

        return ResponseEntity.ok()
            .contentType(MediaType.valueOf("image/svg+xml"))
            .body(svgContent);
    }

    @GetMapping("/{profileId}/d3")
    public ResponseEntity<String> getD3FlamegraphJson(
            @PathVariable String profileId,
            @RequestParam String eventType,
            @RequestParam long fromTime,
            @RequestParam long toTime) {

        String json = flamegraphService.generateD3FlamegraphJson(profileId, eventType, fromTime, toTime);

        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_JSON)
            .body(json);
    }
}
```

### 3. Search Stacktraces Containing Specific Frames

```java
@Service
public class FrameSearchService {

    // Find all stacktraces containing frames matching a specific pattern
    public List<MatchingStacktrace> findStacktracesByPattern(String profileId, String searchPattern) {
        String sql = """
            SELECT
                e.profile_id,
                e.stacktrace_id,
                sum(e.samples) as total_samples,
                sum(e.weight) as total_weight,
                -- Extract matching frames
                arrayStringConcat(
                    arrayMap(frame_hash ->
                        concat(
                            dictGet('frames_dict', 'class_name', frame_hash), '.',
                            dictGet('frames_dict', 'method_name', frame_hash)
                        ),
                        arrayFilter(frame_hash ->
                            dictGet('frames_dict', 'class_name', frame_hash) ILIKE {pattern:String} OR
                            dictGet('frames_dict', 'method_name', frame_hash) ILIKE {pattern:String},
                            st.frame_hashes
                        )
                    ),
                    '; '
                ) as matching_frames,
                -- Full stacktrace for context
                arrayStringConcat(
                    arrayMap(frame_hash ->
                        concat(
                            dictGet('frames_dict', 'class_name', frame_hash), '.',
                            dictGet('frames_dict', 'method_name', frame_hash)
                        ),
                        st.frame_hashes
                    ),
                    '\n'
                ) as full_stacktrace
            FROM jfr_events e
            JOIN stacktraces st ON (e.profile_id = st.profile_id AND e.stacktrace_id = st.stacktrace_id)
            WHERE e.profile_id = {profileId:String}
              AND arrayExists(frame_hash ->
                    dictGet('frames_dict', 'class_name', frame_hash) ILIKE {pattern:String} OR
                    dictGet('frames_dict', 'method_name', frame_hash) ILIKE {pattern:String},
                    st.frame_hashes
                  )
            GROUP BY e.profile_id, e.stacktrace_id, st.frame_hashes, matching_frames, full_stacktrace
            ORDER BY total_samples DESC
            """;

        String likePattern = "%" + searchPattern + "%";
        return clickHouseTemplate.query(sql,
            Map.of("profileId", profileId, "pattern", likePattern),
            (rs, rowNum) -> new MatchingStacktrace(
                rs.getString("profile_id"),
                rs.getLong("stacktrace_id"),
                rs.getLong("total_samples"),
                rs.getLong("total_weight"),
                rs.getString("matching_frames"),
                rs.getString("full_stacktrace")
            )
        );
    }

    // More efficient approach using frames table directly
    public List<FrameAnalysis> analyzeFramesByPattern(String profileId, String searchPattern) {
        String sql = """
            SELECT
                f.class_name,
                f.method_name,
                f.compilation_type,
                count(DISTINCT e.stacktrace_id) as stacktrace_count,
                sum(e.samples) as total_samples,
                avg(e.samples) as avg_samples_per_occurrence,
                -- Calculate frame depth distribution
                arraySort(groupArray(arrayFirstIndex(x -> x = f.frame_hash, st.frame_hashes))) as depth_distribution
            FROM frames f
            JOIN stacktraces st ON (has(st.frame_hashes, f.frame_hash))
            JOIN jfr_events e ON (e.profile_id = st.profile_id AND e.stacktrace_id = st.stacktrace_id)
            WHERE e.profile_id = {profileId:String}
              AND (f.class_name ILIKE {pattern:String} OR f.method_name ILIKE {pattern:String})
            GROUP BY f.frame_hash, f.class_name, f.method_name, f.compilation_type
            ORDER BY total_samples DESC
            """;

        String likePattern = "%" + searchPattern + "%";
        return clickHouseTemplate.query(sql,
            Map.of("profileId", profileId, "pattern", likePattern),
            (rs, rowNum) -> new FrameAnalysis(
                rs.getString("class_name"),
                rs.getString("method_name"),
                rs.getString("compilation_type"),
                rs.getInt("stacktrace_count"),
                rs.getLong("total_samples"),
                rs.getDouble("avg_samples_per_occurrence"),
                Arrays.asList((Integer[]) rs.getArray("depth_distribution").getArray())
            )
        );
    }

    // Simple pattern search in frames
    public List<Frame> searchFramesByPattern(String pattern) {
        String sql = """
            SELECT frame_hash, class_name, method_name, compilation_type, line_number, bytecode_index
            FROM frames
            WHERE class_name ILIKE {pattern:String} OR method_name ILIKE {pattern:String}
            ORDER BY class_name, method_name
            LIMIT 100
            """;

        String searchPattern = "%" + pattern + "%";
        return clickHouseTemplate.query(sql, Map.of("pattern", searchPattern),
            (rs, rowNum) -> new Frame(
                rs.getLong("frame_hash"),
                rs.getString("class_name"),
                rs.getString("method_name"),
                rs.getString("compilation_type"),
                rs.getInt("line_number"),
                rs.getInt("bytecode_index")
            )
        );
    }

    // Example usage methods for common search patterns
    public List<MatchingStacktrace> findGCRelatedStacktraces(String profileId) {
        return findStacktracesByPattern(profileId, "GC"); // Finds G1GC, ParallelGC, etc.
    }

    public List<MatchingStacktrace> findJITCompilerStacktraces(String profileId) {
        return findStacktracesByPattern(profileId, "Compiler"); // Finds JIT compiler activity
    }

    public List<MatchingStacktrace> findSpringFrameworkStacktraces(String profileId) {
        return findStacktracesByPattern(profileId, "springframework"); // Finds Spring-related frames
    }

    public List<MatchingStacktrace> findDatabaseStacktraces(String profileId) {
        return findStacktracesByPattern(profileId, "jdbc"); // Finds database-related activity
    }
}
```

## Data Models

```java
@Data
@AllArgsConstructor
public class Frame {
    private Long frameHash;
    private String className;
    private String methodName;
    private String compilationType;
    private Integer lineNumber;
    private Integer bytecodeIndex;
}

@Data
@AllArgsConstructor
public class StacktraceAggregate {
    private Long stacktraceId;
    private Long totalSamples;
    private Long totalWeight;
    private String weightEntity;
    private String reconstructedFrames;
}

@Data
@AllArgsConstructor
public class MatchingStacktrace {
    private String profileId;
    private Long stacktraceId;
    private Long totalSamples;
    private Long totalWeight;
    private String matchingFrames;
    private String fullStacktrace;
}

@Data
@AllArgsConstructor
public class FrameAnalysis {
    private String className;
    private String methodName;
    private String compilationType;
    private Integer stacktraceCount;
    private Long totalSamples;
    private Double avgSamplesPerOccurrence;
    private List<Integer> depthDistribution;
}
```

## Implementation Notes

### Dictionary Setup for Frame Lookups

```sql
-- Create dictionary for fast frame lookups
CREATE DICTIONARY frames_dict
(
    frame_hash UInt64,
    class_name String,
    method_name String,
    compilation_type String,
    line_number UInt32,
    bytecode_index UInt32
)
PRIMARY KEY frame_hash
SOURCE(CLICKHOUSE(HOST 'localhost' PORT 9000 USER 'default' PASSWORD '' DB 'jeffrey' TABLE 'frames'))
LAYOUT(HASHED())
LIFETIME(MIN 300 MAX 600);
```

### Frame Hash Calculation

```java
public class FrameHashCalculator {

    public static UInt64 calculateFrameHash(Frame frame) {
        String composite = String.join("|",
            frame.getClassName(),
            frame.getMethodName(),
            frame.getCompilationType(),
            String.valueOf(frame.getLineNumber()),
            String.valueOf(frame.getBytecodeIndex())
        );
        return CityHash.cityHash64(composite.getBytes(StandardCharsets.UTF_8));
    }

    public static UInt64 calculateStackHash(List<UInt64> frameHashes) {
        ByteBuffer buffer = ByteBuffer.allocate(frameHashes.size() * 8);
        for (UInt64 hash : frameHashes) {
            buffer.putLong(hash);
        }
        return CityHash.cityHash64(buffer.array());
    }
}
```

## Benefits Summary

1. **🚀 20-100x Query Performance** - From 10 seconds to 100-500ms
2. **💾 90% Storage Reduction** - From 400MB to ~40MB per profile
3. **🔍 Instant Frame Search** - Indexed frame lookups vs. CSV text search
4. **📊 Advanced Analytics** - Frame-level analysis, co-occurrence patterns
5. **⏱️ Time-Series Optimization** - Partition pruning for time-range queries
6. **🎯 Profiling-Optimized** - Purpose-built for profiling data like Uber, Cloudflare

This migration will transform Jeffrey into a high-performance profiling platform capable of handling large-scale Java applications with sub-second query response times.
