# Final DuckDB Macros Solution - Complete Implementation Guide

## Table Structure Overview

- **events**: Contains profiling events with `profile_id`, `event_type`, `start_from_beginning` (millis), `thread_hash`, `stacktrace_hash`, `samples`, `weight`
- **stacktraces**: Contains `stacktrace_hash`, `profile_id`, `frame_hashes` (array)
- **frames**: Contains `frame_hash`, `class_name`, `method_name`, `line`
- **threads**: Contains `thread_hash`, `java_name`, `os_name`, `java_id`, `os_id`

## Required Indexes

```sql
CREATE INDEX idx_events_profile_filters ON events(profile_id, event_type, start_from_beginning, thread_hash, stacktrace_hash);
CREATE INDEX idx_stacktraces_profile ON stacktraces(profile_id);
CREATE INDEX idx_frames_hash ON frames(frame_hash);
CREATE INDEX idx_threads_hash ON threads(thread_hash);
```

## Macro 1: Stacktraces Without Thread Grouping

**Purpose**: Aggregate all events for each stacktrace across all threads. Returns one row per stacktrace with aggregated samples and weight.

```sql
CREATE MACRO stacktrace_details(profile_id_param, event_type_param, from_time_param := NULL, to_time_param := NULL) AS TABLE
SELECT 
    s.stacktrace_hash,
    LIST(STRUCT_PACK(
        class_name := f.class_name,
        method_name := f.method_name,
        line := f.line,
        frame_hash := f.frame_hash
    ) ORDER BY idx) AS frames,
    SUM(e.samples) AS total_samples,
    SUM(e.weight) AS total_weight
FROM events e
INNER JOIN stacktraces s ON s.stacktrace_hash = e.stacktrace_hash 
    AND s.profile_id = e.profile_id
CROSS JOIN UNNEST(s.frame_hashes) WITH ORDINALITY AS t_unnest(frame_hash, idx)
INNER JOIN frames f ON f.frame_hash = t_unnest.frame_hash
WHERE e.profile_id = profile_id_param
    AND e.event_type = event_type_param
    AND (from_time_param IS NULL OR e.start_from_beginning >= from_time_param)
    AND (to_time_param IS NULL OR e.start_from_beginning <= to_time_param)
GROUP BY s.stacktrace_hash, s.frame_hashes;
```

## Macro 2: Stacktraces With Thread Grouping

**Purpose**: Group events by thread first, then by stacktrace. Returns one row per (thread, stacktrace) combination with thread details and aggregated metrics.

```sql
CREATE MACRO stacktrace_details_by_thread(profile_id_param, event_type_param, from_time_param := NULL, to_time_param := NULL, thread_hash_param := NULL) AS TABLE
SELECT 
    e.thread_hash,
    STRUCT_PACK(
        java_name := ANY_VALUE(t.java_name),
        os_name := ANY_VALUE(t.os_name),
        java_id := ANY_VALUE(t.java_id),
        os_id := ANY_VALUE(t.os_id)
    ) AS thread,
    s.stacktrace_hash,
    LIST(STRUCT_PACK(
        class_name := f.class_name,
        method_name := f.method_name,
        line := f.line,
        frame_hash := f.frame_hash
    ) ORDER BY idx) AS frames,
    SUM(e.samples) AS total_samples,
    SUM(e.weight) AS total_weight
FROM events e
INNER JOIN threads t ON t.thread_hash = e.thread_hash
INNER JOIN stacktraces s ON s.stacktrace_hash = e.stacktrace_hash 
    AND s.profile_id = e.profile_id
CROSS JOIN UNNEST(s.frame_hashes) WITH ORDINALITY AS t_unnest(frame_hash, idx)
INNER JOIN frames f ON f.frame_hash = t_unnest.frame_hash
WHERE e.profile_id = profile_id_param
    AND e.event_type = event_type_param
    AND (from_time_param IS NULL OR e.start_from_beginning >= from_time_param)
    AND (to_time_param IS NULL OR e.start_from_beginning <= to_time_param)
    AND (thread_hash_param IS NULL OR e.thread_hash = thread_hash_param)
GROUP BY e.thread_hash, e.stacktrace_hash, s.frame_hashes;
```

## Java RowMappers

```java
// RowMapper for Macro 1 - stacktrace_details
public class StacktraceRowMapper implements RowMapper<StacktraceResult> {
    
    @Override
    public StacktraceResult mapRow(ResultSet rs, int rowNum) throws SQLException {
        StacktraceResult result = new StacktraceResult();
        result.setStacktraceHash(rs.getString("stacktrace_hash"));
        result.setTotalSamples(rs.getLong("total_samples"));
        result.setTotalWeight(rs.getLong("total_weight"));
        
        // Map frames array
        Array framesArray = rs.getArray("frames");
        if (framesArray != null) {
            Struct[] structs = (Struct[]) framesArray.getArray();
            List<FrameData> frames = new ArrayList<>(structs.length);
            
            for (Struct struct : structs) {
                Object[] attrs = struct.getAttributes();
                frames.add(new FrameData(
                    (String) attrs[0],  // class_name
                    (String) attrs[1],  // method_name
                    (Integer) attrs[2], // line
                    (String) attrs[3]   // frame_hash
                ));
            }
            result.setFrames(frames);
        }
        
        return result;
    }
}

// RowMapper for Macro 2 - stacktrace_details_by_thread
public class StacktraceByThreadRowMapper implements RowMapper<StacktraceByThreadResult> {
    
    @Override
    public StacktraceByThreadResult mapRow(ResultSet rs, int rowNum) throws SQLException {
        StacktraceByThreadResult result = new StacktraceByThreadResult();
        result.setThreadHash(rs.getLong("thread_hash"));
        result.setStacktraceHash(rs.getString("stacktrace_hash"));
        result.setTotalSamples(rs.getLong("total_samples"));
        result.setTotalWeight(rs.getLong("total_weight"));
        
        // Map thread struct
        Struct threadStruct = (Struct) rs.getObject("thread");
        if (threadStruct != null) {
            Object[] attrs = threadStruct.getAttributes();
            ThreadInfo threadInfo = new ThreadInfo(
                (String) attrs[0],  // java_name
                (String) attrs[1],  // os_name
                (Long) attrs[2],    // java_id
                (Long) attrs[3]     // os_id
            );
            result.setThread(threadInfo);
        }
        
        // Map frames array
        Array framesArray = rs.getArray("frames");
        if (framesArray != null) {
            Struct[] structs = (Struct[]) framesArray.getArray();
            List<FrameData> frames = new ArrayList<>(structs.length);
            
            for (Struct struct : structs) {
                Object[] attrs = struct.getAttributes();
                frames.add(new FrameData(
                    (String) attrs[0],  // class_name
                    (String) attrs[1],  // method_name
                    (Integer) attrs[2], // line
                    (String) attrs[3]   // frame_hash
                ));
            }
            result.setFrames(frames);
        }
        
        return result;
    }
}
```

## Java Service Layer

```java
@Service
public class StacktraceService {
    
    private final JdbcTemplate jdbcTemplate;
    private final StacktraceRowMapper stacktraceMapper = new StacktraceRowMapper();
    private final StacktraceByThreadRowMapper byThreadMapper = new StacktraceByThreadRowMapper();
    
    public StacktraceService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }
    
    // ==================== Macro 1 Usage ====================
    
    /**
     * Get stacktraces aggregated across all threads for a time range.
     * Returns one row per stacktrace with total samples and weight.
     */
    public List<StacktraceResult> getStacktraces(
            String profileId,
            String eventType,
            Long fromTime,
            Long toTime) {
        
        String sql = "SELECT * FROM stacktrace_details(?, ?, ?, ?)";
        return jdbcTemplate.query(sql, 
            new Object[]{profileId, eventType, fromTime, toTime}, 
            stacktraceMapper);
    }
    
    /**
     * Get all stacktraces for a profile and event type (no time filter).
     */
    public List<StacktraceResult> getAllStacktraces(
            String profileId,
            String eventType) {
        
        String sql = "SELECT * FROM stacktrace_details(?, ?)";
        return jdbcTemplate.query(sql, 
            new Object[]{profileId, eventType}, 
            stacktraceMapper);
    }
    
    // ==================== Macro 2 Usage ====================
    
    /**
     * Get stacktraces grouped by thread for all threads in a time range.
     * Returns one row per (thread, stacktrace) combination.
     */
    public List<StacktraceByThreadResult> getStacktracesGroupedByThread(
            String profileId,
            String eventType,
            Long fromTime,
            Long toTime) {
        
        String sql = "SELECT * FROM stacktrace_details_by_thread(?, ?, ?, ?)";
        return jdbcTemplate.query(sql, 
            new Object[]{profileId, eventType, fromTime, toTime}, 
            byThreadMapper);
    }
    
    /**
     * Get stacktraces for a specific thread in a time range.
     * Returns one row per stacktrace for that thread only.
     */
    public List<StacktraceByThreadResult> getStacktracesForThread(
            String profileId,
            String eventType,
            Long fromTime,
            Long toTime,
            Long threadHash) {
        
        String sql = "SELECT * FROM stacktrace_details_by_thread(?, ?, ?, ?, ?)";
        return jdbcTemplate.query(sql, 
            new Object[]{profileId, eventType, fromTime, toTime, threadHash}, 
            byThreadMapper);
    }
    
    /**
     * Get all stacktraces grouped by thread (no time filter).
     */
    public List<StacktraceByThreadResult> getAllStacktracesGroupedByThread(
            String profileId,
            String eventType) {
        
        String sql = "SELECT * FROM stacktrace_details_by_thread(?, ?)";
        return jdbcTemplate.query(sql, 
            new Object[]{profileId, eventType}, 
            byThreadMapper);
    }
    
    /**
     * Get stacktraces for a specific thread (no time filter).
     */
    public List<StacktraceByThreadResult> getAllStacktracesForThread(
            String profileId,
            String eventType,
            Long threadHash) {
        
        String sql = "SELECT * FROM stacktrace_details_by_thread(?, ?, NULL, NULL, ?)";
        return jdbcTemplate.query(sql, 
            new Object[]{profileId, eventType, threadHash}, 
            byThreadMapper);
    }
    
    // ==================== Helper Methods ====================
    
    /**
     * Get stacktraces using Instant objects for time range.
     */
    public List<StacktraceResult> getStacktraces(
            String profileId,
            String eventType,
            Instant fromInstant,
            Instant toInstant) {
        
        return getStacktraces(
            profileId, 
            eventType, 
            fromInstant.toEpochMilli(), 
            toInstant.toEpochMilli()
        );
    }
    
    /**
     * Get stacktraces grouped by thread using Instant objects.
     */
    public List<StacktraceByThreadResult> getStacktracesGroupedByThread(
            String profileId,
            String eventType,
            Instant fromInstant,
            Instant toInstant) {
        
        return getStacktracesGroupedByThread(
            profileId, 
            eventType, 
            fromInstant.toEpochMilli(), 
            toInstant.toEpochMilli()
        );
    }
}
```

## Usage Examples

```java
@RestController
@RequestMapping("/api/profiling")
public class ProfilingController {
    
    private final StacktraceService stacktraceService;
    
    public ProfilingController(StacktraceService stacktraceService) {
        this.stacktraceService = stacktraceService;
    }
    
    /**
     * Example 1: Get all CPU stacktraces for a profile in the last hour
     */
    @GetMapping("/stacktraces/simple")
    public List<StacktraceResult> getRecentStacktraces(
            @RequestParam String profileId) {
        
        Instant oneHourAgo = Instant.now().minus(1, ChronoUnit.HOURS);
        Instant now = Instant.now();
        
        return stacktraceService.getStacktraces(
            profileId,
            "cpu",
            oneHourAgo,
            now
        );
    }
    
    /**
     * Example 2: Get all stacktraces without time filter
     */
    @GetMapping("/stacktraces/all")
    public List<StacktraceResult> getAllStacktraces(
            @RequestParam String profileId,
            @RequestParam String eventType) {
        
        return stacktraceService.getAllStacktraces(profileId, eventType);
    }
    
    /**
     * Example 3: Get stacktraces grouped by thread for analysis
     */
    @GetMapping("/stacktraces/by-thread")
    public List<StacktraceByThreadResult> getStacktracesByThread(
            @RequestParam String profileId,
            @RequestParam String eventType,
            @RequestParam Long fromTimeMillis,
            @RequestParam Long toTimeMillis) {
        
        return stacktraceService.getStacktracesGroupedByThread(
            profileId,
            eventType,
            fromTimeMillis,
            toTimeMillis
        );
    }
    
    /**
     * Example 4: Get stacktraces for a specific thread
     */
    @GetMapping("/stacktraces/thread/{threadHash}")
    public List<StacktraceByThreadResult> getStacktracesForSpecificThread(
            @RequestParam String profileId,
            @RequestParam String eventType,
            @PathVariable Long threadHash,
            @RequestParam Long fromTimeMillis,
            @RequestParam Long toTimeMillis) {
        
        return stacktraceService.getStacktracesForThread(
            profileId,
            eventType,
            fromTimeMillis,
            toTimeMillis,
            threadHash
        );
    }
    
    /**
     * Example 5: Process and display stacktrace results
     */
    @GetMapping("/stacktraces/report")
    public Map<String, Object> getStacktraceReport(
            @RequestParam String profileId) {
        
        // Get simple aggregated view
        List<StacktraceResult> simpleResults = stacktraceService.getAllStacktraces(
            profileId, 
            "cpu"
        );
        
        // Get thread-grouped view
        List<StacktraceByThreadResult> threadResults = 
            stacktraceService.getAllStacktracesGroupedByThread(profileId, "cpu");
        
        // Process results
        long totalSamples = simpleResults.stream()
            .mapToLong(StacktraceResult::getTotalSamples)
            .sum();
        
        long totalWeight = simpleResults.stream()
            .mapToLong(StacktraceResult::getTotalWeight)
            .sum();
        
        Map<Long, Long> samplesByThread = threadResults.stream()
            .collect(Collectors.groupingBy(
                StacktraceByThreadResult::getThreadHash,
                Collectors.summingLong(StacktraceByThreadResult::getTotalSamples)
            ));
        
        Map<Long, Long> weightByThread = threadResults.stream()
            .collect(Collectors.groupingBy(
                StacktraceByThreadResult::getThreadHash,
                Collectors.summingLong(StacktraceByThreadResult::getTotalWeight)
            ));
        
        // Build report
        Map<String, Object> report = new HashMap<>();
        report.put("totalSamples", totalSamples);
        report.put("totalWeight", totalWeight);
        report.put("uniqueStacktraces", simpleResults.size());
        report.put("threadsAnalyzed", samplesByThread.size());
        report.put("samplesByThread", samplesByThread);
        report.put("weightByThread", weightByThread);
        report.put("topStacktraces", simpleResults.stream()
            .sorted(Comparator.comparing(StacktraceResult::getTotalSamples).reversed())
            .limit(10)
            .collect(Collectors.toList())
        );
        
        return report;
    }
    
    /**
     * Example 6: Get detailed frame information for a specific stacktrace
     */
    @GetMapping("/stacktrace/{hash}/frames")
    public List<FrameData> getStacktraceFrames(
            @RequestParam String profileId,
            @PathVariable String hash) {
        
        List<StacktraceResult> results = stacktraceService.getAllStacktraces(
            profileId, 
            "cpu"
        );
        
        return results.stream()
            .filter(s -> s.getStacktraceHash().equals(hash))
            .findFirst()
            .map(StacktraceResult::getFrames)
            .orElse(Collections.emptyList());
    }
}
```

## Common Use Cases

### Use Case 1: Flame Graph Data
```java
public FlameGraphData buildFlameGraph(String profileId, String eventType) {
    List<StacktraceResult> stacktraces = stacktraceService.getAllStacktraces(
        profileId, 
        eventType
    );
    
    FlameGraphData graph = new FlameGraphData();
    for (StacktraceResult st : stacktraces) {
        FlameGraphNode node = graph.getRoot();
        for (FrameData frame : st.getFrames()) {
            String label = frame.getClassName() + "." + frame.getMethodName();
            node = node.getOrCreateChild(label);
            node.addSamples(st.getTotalSamples());
            node.addWeight(st.getTotalWeight());
        }
    }
    return graph;
}
```

### Use Case 2: Thread Comparison
```java
public Map<String, ComparisonData> compareThreads(
        String profileId, 
        Long thread1, 
        Long thread2) {
    
    List<StacktraceByThreadResult> thread1Data = 
        stacktraceService.getAllStacktracesForThread(profileId, "cpu", thread1);
    
    List<StacktraceByThreadResult> thread2Data = 
        stacktraceService.getAllStacktracesForThread(profileId, "cpu", thread2);
    
    long thread1Samples = thread1Data.stream()
        .mapToLong(StacktraceByThreadResult::getTotalSamples)
        .sum();
    
    long thread2Samples = thread2Data.stream()
        .mapToLong(StacktraceByThreadResult::getTotalSamples)
        .sum();
    
    long thread1Weight = thread1Data.stream()
        .mapToLong(StacktraceByThreadResult::getTotalWeight)
        .sum();
    
    long thread2Weight = thread2Data.stream()
        .mapToLong(StacktraceByThreadResult::getTotalWeight)
        .sum();
    
    Map<String, ComparisonData> comparison = new HashMap<>();
    comparison.put("thread1", new ComparisonData(thread1Samples, thread1Weight));
    comparison.put("thread2", new ComparisonData(thread2Samples, thread2Weight));
    
    return comparison;
}
```

### Use Case 3: Time-Based Analysis
```java
public TimeSeriesData analyzeOverTime(String profileId, String eventType) {
    Instant now = Instant.now();
    TimeSeriesData data = new TimeSeriesData();
    
    // Get data for each hour in the last 24 hours
    for (int i = 0; i < 24; i++) {
        Instant end = now.minus(i, ChronoUnit.HOURS);
        Instant start = end.minus(1, ChronoUnit.HOURS);
        
        List<StacktraceResult> hourData = stacktraceService.getStacktraces(
            profileId,
            eventType,
            start,
            end
        );
        
        long hourSamples = hourData.stream()
            .mapToLong(StacktraceResult::getTotalSamples)
            .sum();
        
        long hourWeight = hourData.stream()
            .mapToLong(StacktraceResult::getTotalWeight)
            .sum();
        
        data.addDataPoint(start, hourSamples, hourWeight);
    }
    
    return data;
}
```

## Performance Notes

1. **Both macros use the same optimization strategy**: Start from `events` table with filtered index scan
2. **Time filtering is efficient**: Uses `start_from_beginning` index for fast range scans
3. **Frame ordering is preserved**: `WITH ORDINALITY` maintains stacktrace frame sequence
4. **Thread filtering is optional**: Can filter specific threads or get all threads
5. **Aggregation happens after joins**: Minimizes data movement
6. **Both samples and weight are Long types**: Consistent numeric handling

## Summary

- **Macro 1** (`stacktrace_details`): Simple aggregation across all threads - best for overall profiling analysis
- **Macro 2** (`stacktrace_details_by_thread`): Thread-aware grouping - best for comparing thread behaviors or analyzing specific threads
- Both macros support optional time filtering and use consistent `STRUCT_PACK` for structured data
- Java integration uses standard JDBC ResultSet mapping with type-safe data classes
- Service layer provides multiple convenience methods for common profiling scenarios
- **All numeric metrics (samples and weight) are Long types** for consistency

This solution provides maximum flexibility and performance for profiling data analysis! 🎯
