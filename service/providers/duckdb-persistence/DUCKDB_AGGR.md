`GROUPING SETS` is a powerful feature in DuckDB that lets you compute multiple aggregations with different grouping levels in a **single query**. It's much more efficient than running separate queries with `UNION ALL`.

## Basic Concept

Instead of writing multiple GROUP BY queries and combining them, you define all grouping combinations at once:

```sql
-- Traditional way (multiple queries)
SELECT profile_id, COUNT(*) FROM frames GROUP BY profile_id
UNION ALL
SELECT NULL, COUNT(*) FROM frames;

-- GROUPING SETS way (single query)
SELECT profile_id, COUNT(*) 
FROM frames 
GROUP BY GROUPING SETS (
    (profile_id),  -- Group by profile_id
    ()             -- Grand total (no grouping)
);
```

## Syntax and Examples

### 1. **Basic GROUPING SETS**

```sql
-- Multiple grouping combinations
SELECT 
    profile_id,
    frame_type,
    COUNT(*) as frame_count
FROM frames
GROUP BY GROUPING SETS (
    (profile_id, frame_type),  -- By profile and type
    (profile_id),              -- By profile only
    (frame_type),              -- By type only
    ()                         -- Grand total
);
```

**Result looks like:**
```
profile_id | frame_type   | frame_count
-----------|--------------|------------
profile1   | JIT          | 1000
profile1   | Interpreted  | 500
profile1   | NULL         | 1500        -- Subtotal for profile1
profile2   | JIT          | 800
profile2   | NULL         | 800         -- Subtotal for profile2
NULL       | JIT          | 1800        -- Subtotal for JIT
NULL       | Interpreted  | 500         -- Subtotal for Interpreted
NULL       | NULL         | 2300        -- Grand total
```

### 2. **ROLLUP - Hierarchical Aggregations**

`ROLLUP` creates a hierarchy from left to right, generating subtotals at each level:

```sql
SELECT 
    profile_id,
    class_name,
    method_name,
    COUNT(*) as count
FROM frames
GROUP BY ROLLUP (profile_id, class_name, method_name);

-- Equivalent to:
GROUP BY GROUPING SETS (
    (profile_id, class_name, method_name),
    (profile_id, class_name),
    (profile_id),
    ()
);
```

**Example with your profiling data:**
```sql
SELECT 
    profile_id,
    frame_type,
    class_name,
    COUNT(*) as frame_count,
    AVG(line_number) as avg_line
FROM frames
GROUP BY ROLLUP (profile_id, frame_type, class_name)
ORDER BY profile_id, frame_type, class_name;
```

**Result:**
```
profile_id | frame_type   | class_name    | frame_count | avg_line
-----------|--------------|---------------|-------------|----------
profile1   | JIT          | MyClass       | 100         | 50
profile1   | JIT          | OtherClass    | 200         | 75
profile1   | JIT          | NULL          | 300         | 67    -- Subtotal: profile1 + JIT
profile1   | Interpreted  | MyClass       | 50          | 40
profile1   | Interpreted  | NULL          | 50          | 40    -- Subtotal: profile1 + Interpreted
profile1   | NULL         | NULL          | 350         | 62    -- Subtotal: profile1
NULL       | NULL         | NULL          | 350         | 62    -- Grand total
```

### 3. **CUBE - All Combinations**

`CUBE` generates all possible grouping combinations:

```sql
SELECT 
    profile_id,
    frame_type,
    COUNT(*) as count
FROM frames
GROUP BY CUBE (profile_id, frame_type);

-- Equivalent to:
GROUP BY GROUPING SETS (
    (profile_id, frame_type),  -- Both
    (profile_id),              -- Profile only
    (frame_type),              -- Type only
    ()                         -- Neither (grand total)
);
```

### 4. **GROUPING Function - Identify Aggregation Level**

Use `GROUPING()` to detect which columns are being aggregated (returns 1 if aggregated, 0 if grouped):

```sql
SELECT 
    profile_id,
    frame_type,
    COUNT(*) as frame_count,
    GROUPING(profile_id) as is_profile_total,
    GROUPING(frame_type) as is_type_total,
    CASE 
        WHEN GROUPING(profile_id) = 0 AND GROUPING(frame_type) = 0 
            THEN 'Detail'
        WHEN GROUPING(profile_id) = 0 AND GROUPING(frame_type) = 1 
            THEN 'Profile Subtotal'
        WHEN GROUPING(profile_id) = 1 AND GROUPING(frame_type) = 0 
            THEN 'Type Subtotal'
        WHEN GROUPING(profile_id) = 1 AND GROUPING(frame_type) = 1 
            THEN 'Grand Total'
    END as aggregation_level
FROM frames
GROUP BY GROUPING SETS (
    (profile_id, frame_type),
    (profile_id),
    (frame_type),
    ()
)
ORDER BY 
    GROUPING(profile_id),
    GROUPING(frame_type),
    profile_id,
    frame_type;
```

**Result:**
```
profile_id | frame_type   | frame_count | is_profile_total | is_type_total | aggregation_level
-----------|--------------|-------------|------------------|---------------|------------------
profile1   | JIT          | 1000        | 0                | 0             | Detail
profile1   | Interpreted  | 500         | 0                | 0             | Detail
profile2   | JIT          | 800         | 0                | 0             | Detail
profile1   | NULL         | 1500        | 0                | 1             | Profile Subtotal
profile2   | NULL         | 800         | 0                | 1             | Profile Subtotal
NULL       | JIT          | 1800        | 1                | 0             | Type Subtotal
NULL       | Interpreted  | 500         | 1                | 0             | Type Subtotal
NULL       | NULL         | 2300        | 1                | 1             | Grand Total
```

## Practical Examples for Your Profiling Schema

### Example 1: Analyze Stacktraces by Type and Profile

```sql
SELECT 
    s.profile_id,
    s.type_id,
    COUNT(*) as stacktrace_count,
    COUNT(DISTINCT UNNEST(s.frame_hashes)) as unique_frames,
    AVG(array_length(s.frame_hashes)) as avg_depth,
    GROUPING(s.profile_id) as profile_total,
    GROUPING(s.type_id) as type_total
FROM stacktraces s
GROUP BY GROUPING SETS (
    (s.profile_id, s.type_id),  -- By profile and type
    (s.profile_id),             -- By profile
    (s.type_id),                -- By type
    ()                          -- Grand total
)
ORDER BY 
    GROUPING(s.profile_id),
    GROUPING(s.type_id),
    s.profile_id,
    s.type_id;
```

### Example 2: Frame Distribution Analysis

```sql
SELECT 
    f.profile_id,
    f.frame_type,
    f.class_name,
    COUNT(*) as frame_count,
    COUNT(DISTINCT f.method_name) as unique_methods,
    MIN(f.line_number) as min_line,
    MAX(f.line_number) as max_line,
    CASE
        WHEN GROUPING(f.profile_id) = 1 THEN 'ALL PROFILES'
        ELSE f.profile_id
    END as display_profile,
    CASE
        WHEN GROUPING(f.frame_type) = 1 THEN 'ALL TYPES'
        ELSE f.frame_type
    END as display_type,
    CASE
        WHEN GROUPING(f.class_name) = 1 THEN 'ALL CLASSES'
        ELSE f.class_name
    END as display_class
FROM frames f
GROUP BY ROLLUP (f.profile_id, f.frame_type, f.class_name)
HAVING COUNT(*) > 10  -- Filter out small groups
ORDER BY 
    GROUPING(f.profile_id),
    GROUPING(f.frame_type),
    GROUPING(f.class_name),
    COUNT(*) DESC;
```

### Example 3: Stacktrace Frames Analysis with Join

```sql
SELECT 
    s.profile_id,
    f.frame_type,
    COUNT(DISTINCT s.stacktrace_hash) as stacktrace_count,
    COUNT(*) as total_frame_occurrences,
    COUNT(DISTINCT f.frame_hash) as unique_frames,
    ROUND(AVG(array_length(s.frame_hashes)), 2) as avg_stack_depth,
    GROUPING(s.profile_id) as is_profile_agg,
    GROUPING(f.frame_type) as is_type_agg
FROM stacktraces s
CROSS JOIN UNNEST(s.frame_hashes) AS frame_hash_value
JOIN frames f 
    ON f.profile_id = s.profile_id 
    AND f.frame_hash = frame_hash_value
GROUP BY CUBE (s.profile_id, f.frame_type)
ORDER BY 
    GROUPING(s.profile_id),
    GROUPING(f.frame_type),
    stacktrace_count DESC;
```

### Example 4: Multi-Level Tag Analysis

```sql
SELECT 
    profile_id,
    type_id,
    UNNEST(tag_ids) as tag_id,
    COUNT(*) as stacktrace_count,
    AVG(array_length(frame_hashes)) as avg_depth
FROM stacktraces
GROUP BY ROLLUP (profile_id, type_id, UNNEST(tag_ids))
ORDER BY profile_id, type_id, tag_id;
```

## Performance Tips

### 1. **GROUPING SETS vs Multiple Queries**

```sql
-- ❌ SLOW: Multiple queries with UNION ALL
SELECT profile_id, NULL as frame_type, COUNT(*) FROM frames GROUP BY profile_id
UNION ALL
SELECT NULL, frame_type, COUNT(*) FROM frames GROUP BY frame_type
UNION ALL
SELECT NULL, NULL, COUNT(*) FROM frames;

-- ✅ FAST: Single pass with GROUPING SETS
SELECT profile_id, frame_type, COUNT(*)
FROM frames
GROUP BY GROUPING SETS ((profile_id), (frame_type), ());
```

**Why faster?** GROUPING SETS scans the table once, while UNION ALL requires multiple scans.

### 2. **Use Indexes**

```sql
-- Create indexes for better performance
CREATE INDEX idx_frames_profile_type ON frames(profile_id, frame_type);
CREATE INDEX idx_frames_type ON frames(frame_type);
```

### 3. **Filter Before Grouping**

```sql
-- ✅ GOOD: Filter first
SELECT profile_id, frame_type, COUNT(*)
FROM frames
WHERE profile_id IN ('profile1', 'profile2')  -- Filter first
GROUP BY ROLLUP (profile_id, frame_type);

-- ❌ BAD: Filter after grouping
SELECT profile_id, frame_type, COUNT(*)
FROM frames
GROUP BY ROLLUP (profile_id, frame_type)
HAVING profile_id IN ('profile1', 'profile2');  -- Too late
```

## Common Use Cases in Profiling

### 1. **Hotspot Analysis at Multiple Levels**

```sql
SELECT 
    COALESCE(profile_id, 'ALL') as profile,
    COALESCE(class_name, 'ALL') as class,
    COALESCE(method_name, 'ALL') as method,
    COUNT(*) as occurrence_count
FROM frames
GROUP BY ROLLUP (profile_id, class_name, method_name)
ORDER BY occurrence_count DESC
LIMIT 100;
```

### 2. **Frame Type Distribution Report**

```sql
SELECT 
    profile_id,
    frame_type,
    COUNT(*) as count,
    ROUND(100.0 * COUNT(*) / SUM(COUNT(*)) OVER (
        PARTITION BY GROUPING(profile_id), GROUPING(frame_type)
    ), 2) as percentage
FROM frames
GROUP BY CUBE (profile_id, frame_type)
ORDER BY GROUPING(profile_id), GROUPING(frame_type), count DESC;
```

### 3. **Time-Series Aggregation** (if you add timestamps)

```sql
-- Assuming you add: timestamp TIMESTAMP to your tables
SELECT 
    DATE_TRUNC('hour', timestamp) as hour,
    profile_id,
    frame_type,
    COUNT(*) as frame_count
FROM frames
GROUP BY ROLLUP (
    DATE_TRUNC('hour', timestamp),
    profile_id,
    frame_type
)
ORDER BY hour, profile_id, frame_type;
```

## Key Differences

| Feature | What it does |
|---------|-------------|
| **GROUPING SETS** | Explicitly list all grouping combinations |
| **ROLLUP** | Hierarchical aggregation (left to right) |
| **CUBE** | All possible combinations (2^n groupings) |
| **GROUPING()** | Function to identify aggregation level |

**Summary:**
- Use **GROUPING SETS** when you need specific combinations
- Use **ROLLUP** for hierarchical totals (like drill-down reports)
- Use **CUBE** when you need all possible combinations (warning: exponential growth!)
- Use **GROUPING()** to format output and identify aggregation levels

This is incredibly useful for profiling dashboards where you want to show totals at different granularity levels in a single efficient query!
