# Window Functions in DuckDB (with Output Examples)

Window functions perform calculations across a set of rows related to the current row, **without collapsing rows** like GROUP BY does. They're essential for ranking, running totals, comparisons, and analytical queries.

## Basic Concept

```sql
-- GROUP BY: Collapses rows
SELECT profile_id, COUNT(*)
FROM frames
GROUP BY profile_id;
```

**Result:**
```
profile_id | count
-----------|------
profile1   | 1500
profile2   | 800
```

```sql
-- Window Function: Keeps all rows
SELECT
    profile_id,
    frame_hash,
    COUNT(*) OVER (PARTITION BY profile_id) as frames_in_profile
FROM frames
         LIMIT 5;
```

**Result:**
```
profile_id | frame_hash | frames_in_profile
-----------|------------|------------------
profile1   | 12345      | 1500
profile1   | 12346      | 1500
profile1   | 12347      | 1500
profile2   | 22345      | 800
profile2   | 22346      | 800
```

## Syntax

```sql
function_name([arguments]) OVER (
    [PARTITION BY partition_expression]
    [ORDER BY sort_expression [ASC|DESC]]
    [frame_clause]
)
```

## Core Components

### 1. **PARTITION BY** - Group Data

Divides rows into partitions (like GROUP BY, but doesn't collapse):

```sql
SELECT
    profile_id,
    frame_hash,
    class_name,
    frame_type,
    COUNT(*) OVER (PARTITION BY profile_id) as total_frames_in_profile,
    COUNT(*) OVER (PARTITION BY profile_id, frame_type) as frames_of_same_type
FROM frames
         LIMIT 8;
```

**Result:**
```
profile_id | frame_hash | class_name  | frame_type  | total_frames_in_profile | frames_of_same_type
-----------|------------|-------------|-------------|------------------------|--------------------
profile1   | 12345      | MyClass     | JIT         | 1500                   | 900
profile1   | 12346      | OtherClass  | JIT         | 1500                   | 900
profile1   | 12347      | MyClass     | Interpreted | 1500                   | 600
profile1   | 12348      | ThirdClass  | Interpreted | 1500                   | 600
profile2   | 22345      | ClassA      | JIT         | 800                    | 500
profile2   | 22346      | ClassB      | JIT         | 800                    | 500
profile2   | 22347      | ClassC      | Native      | 800                    | 300
profile2   | 22348      | ClassD      | Native      | 800                    | 300
```

### 2. **ORDER BY** - Define Row Order

Orders rows within each partition:

```sql
SELECT
    profile_id,
    frame_hash,
    line_number,
    ROW_NUMBER() OVER (PARTITION BY profile_id ORDER BY line_number) as row_num
FROM frames
WHERE profile_id = 'profile1'
    LIMIT 6;
```

**Result:**
```
profile_id | frame_hash | line_number | row_num
-----------|------------|-------------|--------
profile1   | 12389      | 10          | 1
profile1   | 12345      | 15          | 2
profile1   | 12390      | 15          | 3
profile1   | 12346      | 20          | 4
profile1   | 12347      | 25          | 5
profile1   | 12391      | 30          | 6
```

### 3. **Frame Clause** - Define Window Size

Specifies which rows to include in the calculation:

```sql
SELECT
    profile_id,
    frame_hash,
    line_number,
    -- Last 3 rows including current
    AVG(line_number) OVER (
        PARTITION BY profile_id 
        ORDER BY frame_hash 
        ROWS BETWEEN 2 PRECEDING AND CURRENT ROW
    ) as moving_avg_3
FROM frames
WHERE profile_id = 'profile1'
    LIMIT 7;
```

**Result:**
```
profile_id | frame_hash | line_number | moving_avg_3
-----------|------------|-------------|-------------
profile1   | 12345      | 10          | 10.00       -- Only 1 row (current)
profile1   | 12346      | 15          | 12.50       -- 2 rows: (10+15)/2
profile1   | 12347      | 20          | 15.00       -- 3 rows: (10+15+20)/3
profile1   | 12348      | 25          | 20.00       -- 3 rows: (15+20+25)/3
profile1   | 12349      | 30          | 25.00       -- 3 rows: (20+25+30)/3
profile1   | 12350      | 35          | 30.00       -- 3 rows: (25+30+35)/3
profile1   | 12351      | 40          | 35.00       -- 3 rows: (30+35+40)/3
```

## Window Function Types

### 1. **Ranking Functions**

#### ROW_NUMBER - Unique Sequential Number

```sql
SELECT
    profile_id,
    class_name,
    method_name,
    line_number,
    ROW_NUMBER() OVER (PARTITION BY profile_id ORDER BY line_number) as row_num
FROM frames
WHERE profile_id IN ('profile1', 'profile2')
    LIMIT 10;
```

**Result:**
```
profile_id | class_name | method_name | line_number | row_num
-----------|------------|-------------|-------------|--------
profile1   | ClassA     | methodX     | 10          | 1
profile1   | ClassB     | methodY     | 15          | 2
profile1   | ClassC     | methodZ     | 15          | 3  <- Note: unique even with ties
profile1   | ClassD     | methodW     | 20          | 4
profile1   | ClassE     | methodV     | 25          | 5
profile2   | ClassF     | methodU     | 5           | 1
profile2   | ClassG     | methodT     | 10          | 2
profile2   | ClassH     | methodS     | 15          | 3
profile2   | ClassI     | methodR     | 20          | 4
profile2   | ClassJ     | methodQ     | 25          | 5
```

#### RANK - Ranking with Gaps

```sql
SELECT
    profile_id,
    class_name,
    COUNT(*) as occurrence_count,
    RANK() OVER (PARTITION BY profile_id ORDER BY COUNT(*) DESC) as rank
FROM frames
WHERE profile_id = 'profile1'
GROUP BY profile_id, class_name
ORDER BY profile_id, rank
    LIMIT 8;
```

**Result:**
```
profile_id | class_name | occurrence_count | rank
-----------|------------|------------------|-----
profile1   | ClassA     | 100              | 1
profile1   | ClassB     | 80               | 2
profile1   | ClassC     | 80               | 2   <- Tied for 2nd
profile1   | ClassD     | 50               | 4   <- Gap! Skips 3
profile1   | ClassE     | 40               | 5
profile1   | ClassF     | 40               | 5   <- Tied for 5th
profile1   | ClassG     | 30               | 7   <- Gap! Skips 6
profile1   | ClassH     | 20               | 8
```

#### DENSE_RANK - Ranking without Gaps

```sql
SELECT
    profile_id,
    class_name,
    COUNT(*) as occurrence_count,
    DENSE_RANK() OVER (PARTITION BY profile_id ORDER BY COUNT(*) DESC) as dense_rank
FROM frames
WHERE profile_id = 'profile1'
GROUP BY profile_id, class_name
ORDER BY profile_id, dense_rank
    LIMIT 8;
```

**Result:**
```
profile_id | class_name | occurrence_count | dense_rank
-----------|------------|------------------|------------
profile1   | ClassA     | 100              | 1
profile1   | ClassB     | 80               | 2
profile1   | ClassC     | 80               | 2   <- Tied for 2nd
profile1   | ClassD     | 50               | 3   <- No gap!
profile1   | ClassE     | 40               | 4
profile1   | ClassF     | 40               | 4   <- Tied for 4th
profile1   | ClassG     | 30               | 5   <- No gap!
profile1   | ClassH     | 20               | 6
```

#### NTILE - Divide into Buckets

```sql
-- Divide frames into 4 quartiles by line number
SELECT
    profile_id,
    frame_hash,
    line_number,
    NTILE(4) OVER (PARTITION BY profile_id ORDER BY line_number) as quartile
FROM frames
WHERE profile_id = 'profile1'
    LIMIT 12;
```

**Result:**
```
profile_id | frame_hash | line_number | quartile
-----------|------------|-------------|----------
profile1   | 12345      | 10          | 1
profile1   | 12346      | 15          | 1
profile1   | 12347      | 20          | 1
profile1   | 12348      | 25          | 2
profile1   | 12349      | 30          | 2
profile1   | 12350      | 35          | 2
profile1   | 12351      | 40          | 3
profile1   | 12352      | 45          | 3
profile1   | 12353      | 50          | 3
profile1   | 12354      | 55          | 4
profile1   | 12355      | 60          | 4
profile1   | 12356      | 65          | 4
```

#### PERCENT_RANK - Relative Rank (0 to 1)

```sql
SELECT
    profile_id,
    class_name,
    COUNT(*) as count,
    PERCENT_RANK() OVER (PARTITION BY profile_id ORDER BY COUNT(*)) as percentile
FROM frames
WHERE profile_id = 'profile1'
GROUP BY profile_id, class_name
ORDER BY count
    LIMIT 8;
```

**Result:**
```
profile_id | class_name | count | percentile
-----------|------------|-------|------------
profile1   | ClassH     | 20    | 0.00       <- Lowest (0%)
profile1   | ClassG     | 30    | 0.14       <- 14% of values below
profile1   | ClassF     | 40    | 0.29
profile1   | ClassE     | 40    | 0.29       <- Same value, same percentile
profile1   | ClassD     | 50    | 0.57
profile1   | ClassC     | 80    | 0.71
profile1   | ClassB     | 80    | 0.71
profile1   | ClassA     | 100   | 1.00       <- Highest (100%)
```

### 2. **Value Functions**

#### LAG - Access Previous Row

```sql
-- Compare each frame's line number with the previous frame
SELECT
    profile_id,
    frame_hash,
    class_name,
    line_number,
    LAG(line_number) OVER (PARTITION BY profile_id ORDER BY frame_hash) as prev_line,
    line_number - LAG(line_number) OVER (PARTITION BY profile_id ORDER BY frame_hash) as line_diff
FROM frames
WHERE profile_id = 'profile1'
ORDER BY frame_hash
    LIMIT 8;
```

**Result:**
```
profile_id | frame_hash | class_name | line_number | prev_line | line_diff
-----------|------------|------------|-------------|-----------|----------
profile1   | 12345      | ClassA     | 10          | NULL      | NULL     <- First row, no previous
profile1   | 12346      | ClassB     | 15          | 10        | 5
profile1   | 12347      | ClassC     | 20          | 15        | 5
profile1   | 12348      | ClassD     | 25          | 20        | 5
profile1   | 12349      | ClassE     | 30          | 25        | 5
profile1   | 12350      | ClassF     | 25          | 30        | -5       <- Line number decreased
profile1   | 12351      | ClassG     | 40          | 25        | 15
profile1   | 12352      | ClassH     | 45          | 40        | 5
```

**With offset and default:**
```sql
SELECT
    profile_id,
    frame_hash,
    line_number,
    LAG(line_number, 1, 0) OVER (PARTITION BY profile_id ORDER BY frame_hash) as prev_line,
    LAG(line_number, 2, 0) OVER (PARTITION BY profile_id ORDER BY frame_hash) as two_rows_back
FROM frames
WHERE profile_id = 'profile1'
ORDER BY frame_hash
    LIMIT 6;
```

**Result:**
```
profile_id | frame_hash | line_number | prev_line | two_rows_back
-----------|------------|-------------|-----------|---------------
profile1   | 12345      | 10          | 0         | 0             <- Default value used
profile1   | 12346      | 15          | 10        | 0             <- Default value used
profile1   | 12347      | 20          | 15        | 10
profile1   | 12348      | 25          | 20        | 15
profile1   | 12349      | 30          | 25        | 20
profile1   | 12350      | 35          | 30        | 25
```

#### LEAD - Access Next Row

```sql
-- Look ahead to next frame
SELECT
    profile_id,
    frame_hash,
    method_name,
    line_number,
    LEAD(method_name) OVER (PARTITION BY profile_id ORDER BY frame_hash) as next_method,
    LEAD(line_number, 2) OVER (PARTITION BY profile_id ORDER BY frame_hash) as line_after_next
FROM frames
WHERE profile_id = 'profile1'
ORDER BY frame_hash
    LIMIT 6;
```

**Result:**
```
profile_id | frame_hash | method_name | line_number | next_method | line_after_next
-----------|------------|-------------|-------------|-------------|----------------
profile1   | 12345      | methodA     | 10          | methodB     | 20
profile1   | 12346      | methodB     | 15          | methodC     | 25
profile1   | 12347      | methodC     | 20          | methodD     | 30
profile1   | 12348      | methodD     | 25          | methodE     | NULL          <- Beyond end
profile1   | 12349      | methodE     | 30          | NULL        | NULL          <- Last row
profile1   | 12350      | methodF     | 35          | NULL        | NULL
```

#### FIRST_VALUE - First Value in Window

```sql
-- Get the first (earliest) line number in each profile
SELECT
    profile_id,
    frame_hash,
    line_number,
    FIRST_VALUE(line_number) OVER (
        PARTITION BY profile_id 
        ORDER BY line_number
        ROWS BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED FOLLOWING
    ) as first_line_in_profile,
    line_number - FIRST_VALUE(line_number) OVER (
        PARTITION BY profile_id 
        ORDER BY line_number
        ROWS BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED FOLLOWING
    ) as offset_from_first
FROM frames
WHERE profile_id IN ('profile1', 'profile2')
    LIMIT 10;
```

**Result:**
```
profile_id | frame_hash | line_number | first_line_in_profile | offset_from_first
-----------|------------|-------------|----------------------|------------------
profile1   | 12345      | 10          | 10                   | 0
profile1   | 12346      | 15          | 10                   | 5
profile1   | 12347      | 20          | 10                   | 10
profile1   | 12348      | 25          | 10                   | 15
profile1   | 12349      | 30          | 10                   | 20
profile2   | 22345      | 5           | 5                    | 0
profile2   | 22346      | 10          | 5                    | 5
profile2   | 22347      | 15          | 5                    | 10
profile2   | 22348      | 20          | 5                    | 15
profile2   | 22349      | 25          | 5                    | 20
```

#### LAST_VALUE - Last Value in Window

```sql
-- Get the last (latest) line number in each profile
SELECT
    profile_id,
    frame_hash,
    line_number,
    LAST_VALUE(line_number) OVER (
        PARTITION BY profile_id 
        ORDER BY line_number
        ROWS BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED FOLLOWING
    ) as last_line_in_profile,
    LAST_VALUE(line_number) OVER (
        PARTITION BY profile_id 
        ORDER BY line_number
        ROWS BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED FOLLOWING
    ) - line_number as distance_to_last
FROM frames
WHERE profile_id = 'profile1'
ORDER BY line_number
    LIMIT 6;
```

**Result:**
```
profile_id | frame_hash | line_number | last_line_in_profile | distance_to_last
-----------|------------|-------------|---------------------|------------------
profile1   | 12345      | 10          | 100                 | 90
profile1   | 12346      | 15          | 100                 | 85
profile1   | 12347      | 20          | 100                 | 80
profile1   | 12348      | 25          | 100                 | 75
profile1   | 12349      | 30          | 100                 | 70
profile1   | 12350      | 35          | 100                 | 65
```

**⚠️ Common Pitfall with LAST_VALUE:**
```sql
-- ❌ WRONG: Default frame is RANGE BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW
SELECT
    profile_id,
    frame_hash,
    line_number,
    LAST_VALUE(line_number) OVER (
        PARTITION BY profile_id 
        ORDER BY line_number
    ) as last_val
FROM frames
WHERE profile_id = 'profile1'
ORDER BY line_number
    LIMIT 5;
```

**Result (WRONG):**
```
profile_id | frame_hash | line_number | last_val
-----------|------------|-------------|----------
profile1   | 12345      | 10          | 10       <- Returns current, not actual last!
profile1   | 12346      | 15          | 15       <- Returns current, not actual last!
profile1   | 12347      | 20          | 20       <- Returns current, not actual last!
profile1   | 12348      | 25          | 25       <- Returns current, not actual last!
profile1   | 12349      | 30          | 30       <- Returns current, not actual last!
```

```sql
-- ✅ CORRECT: Explicitly specify the frame
SELECT
    profile_id,
    frame_hash,
    line_number,
    LAST_VALUE(line_number) OVER (
        PARTITION BY profile_id 
        ORDER BY line_number
        ROWS BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED FOLLOWING
    ) as last_val
FROM frames
WHERE profile_id = 'profile1'
ORDER BY line_number
    LIMIT 5;
```

**Result (CORRECT):**
```
profile_id | frame_hash | line_number | last_val
-----------|------------|-------------|----------
profile1   | 12345      | 10          | 100      <- Correct: actual last value
profile1   | 12346      | 15          | 100      <- Correct: actual last value
profile1   | 12347      | 20          | 100      <- Correct: actual last value
profile1   | 12348      | 25          | 100      <- Correct: actual last value
profile1   | 12349      | 30          | 100      <- Correct: actual last value
```

#### NTH_VALUE - Nth Value in Window

```sql
-- Get the 3rd lowest line number in each profile
SELECT DISTINCT
    profile_id,
    NTH_VALUE(line_number, 3) OVER (
        PARTITION BY profile_id 
        ORDER BY line_number
        ROWS BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED FOLLOWING
    ) as third_line_number
FROM frames
WHERE profile_id IN ('profile1', 'profile2');
```

**Result:**
```
profile_id | third_line_number
-----------|------------------
profile1   | 20
profile2   | 15
```

### 3. **Aggregate Functions as Window Functions**

Any aggregate function can be used as a window function:

#### Running Totals

```sql
SELECT
    profile_id,
    frame_hash,
    line_number,
    SUM(1) OVER (
        PARTITION BY profile_id 
        ORDER BY frame_hash
        ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW
    ) as running_count,
    SUM(line_number) OVER (
        PARTITION BY profile_id 
        ORDER BY frame_hash
    ) as cumulative_line_numbers
FROM frames
WHERE profile_id = 'profile1'
ORDER BY frame_hash
    LIMIT 8;
```

**Result:**
```
profile_id | frame_hash | line_number | running_count | cumulative_line_numbers
-----------|------------|-------------|---------------|------------------------
profile1   | 12345      | 10          | 1             | 10
profile1   | 12346      | 15          | 2             | 25
profile1   | 12347      | 20          | 3             | 45
profile1   | 12348      | 25          | 4             | 70
profile1   | 12349      | 30          | 5             | 100
profile1   | 12350      | 35          | 6             | 135
profile1   | 12351      | 40          | 7             | 175
profile1   | 12352      | 45          | 8             | 220
```

#### Moving Averages

```sql
-- 5-frame moving average of line numbers
SELECT
    profile_id,
    frame_hash,
    line_number,
    ROUND(AVG(line_number) OVER (
        PARTITION BY profile_id 
        ORDER BY frame_hash
        ROWS BETWEEN 4 PRECEDING AND CURRENT ROW
    ), 2) as moving_avg_5,
    ROUND(AVG(line_number) OVER (
        PARTITION BY profile_id 
        ORDER BY frame_hash
        ROWS BETWEEN 2 PRECEDING AND 2 FOLLOWING
    ), 2) as centered_moving_avg_5
FROM frames
WHERE profile_id = 'profile1'
ORDER BY frame_hash
    LIMIT 10;
```

**Result:**
```
profile_id | frame_hash | line_number | moving_avg_5 | centered_moving_avg_5
-----------|------------|-------------|--------------|----------------------
profile1   | 12345      | 10          | 10.00        | 20.00      <- (10+15+20+25+30)/5
profile1   | 12346      | 15          | 12.50        | 22.00      <- (10+15+20+25+35)/5
profile1   | 12347      | 20          | 15.00        | 25.00      <- (15+20+25+30+35)/5
profile1   | 12348      | 25          | 17.50        | 29.00
profile1   | 12349      | 30          | 20.00        | 33.00
profile1   | 12350      | 35          | 25.00        | 37.00
profile1   | 12351      | 40          | 30.00        | 41.00
profile1   | 12352      | 45          | 35.00        | 45.00
profile1   | 12353      | 50          | 40.00        | 48.00
profile1   | 12354      | 55          | 45.00        | 52.00
```

#### Running Min/Max

```sql
SELECT
    profile_id,
    frame_hash,
    line_number,
    MIN(line_number) OVER (
        PARTITION BY profile_id 
        ORDER BY frame_hash
    ) as min_line_so_far,
    MAX(line_number) OVER (
        PARTITION BY profile_id 
        ORDER BY frame_hash
    ) as max_line_so_far
FROM frames
WHERE profile_id = 'profile1'
ORDER BY frame_hash
    LIMIT 8;
```

**Result:**
```
profile_id | frame_hash | line_number | min_line_so_far | max_line_so_far
-----------|------------|-------------|-----------------|----------------
profile1   | 12345      | 10          | 10              | 10
profile1   | 12346      | 15          | 10              | 15
profile1   | 12347      | 20          | 10              | 20
profile1   | 12348      | 25          | 10              | 25
profile1   | 12349      | 30          | 10              | 30
profile1   | 12350      | 25          | 10              | 30             <- Line decreased but max stays
profile1   | 12351      | 40          | 10              | 40
profile1   | 12352      | 45          | 10              | 45
```

#### Statistical Functions

```sql
SELECT
    profile_id,
    frame_hash,
    line_number,
    ROUND(AVG(line_number) OVER (PARTITION BY profile_id), 2) as avg_line,
    ROUND(STDDEV(line_number) OVER (PARTITION BY profile_id), 2) as stddev_line,
    ROUND(line_number - AVG(line_number) OVER (PARTITION BY profile_id), 2) as deviation_from_mean
FROM frames
WHERE profile_id = 'profile1'
ORDER BY frame_hash
    LIMIT 8;
```

**Result:**
```
profile_id | frame_hash | line_number | avg_line | stddev_line | deviation_from_mean
-----------|------------|-------------|----------|-------------|--------------------
profile1   | 12345      | 10          | 32.50    | 15.81       | -22.50
profile1   | 12346      | 15          | 32.50    | 15.81       | -17.50
profile1   | 12347      | 20          | 32.50    | 15.81       | -12.50
profile1   | 12348      | 25          | 32.50    | 15.81       | -7.50
profile1   | 12349      | 30          | 32.50    | 15.81       | -2.50
profile1   | 12350      | 35          | 32.50    | 15.81       | 2.50
profile1   | 12351      | 40          | 32.50    | 15.81       | 7.50
profile1   | 12352      | 45          | 32.50    | 15.81       | 12.50
```

## Frame Specifications

### ROWS vs RANGE

```sql
-- ROWS: Physical offset (count of rows)
SELECT
    frame_hash,
    line_number,
    SUM(line_number) OVER (
        ORDER BY line_number
        ROWS BETWEEN 1 PRECEDING AND 1 FOLLOWING
    ) as sum_rows
FROM frames
WHERE profile_id = 'profile1'
ORDER BY line_number
    LIMIT 6;
```

**Result:**
```
frame_hash | line_number | sum_rows
-----------|-------------|----------
12345      | 10          | 25       <- (10 + 15) - no preceding row
12346      | 15          | 45       <- (10 + 15 + 20)
12389      | 15          | 50       <- (15 + 15 + 20) - another row with 15
12347      | 20          | 60       <- (15 + 20 + 25)
12348      | 25          | 75       <- (20 + 25 + 30)
12349      | 30          | 90       <- (25 + 30 + 35)
```

```sql
-- RANGE: Logical offset (value range)
SELECT
    frame_hash,
    line_number,
    SUM(line_number) OVER (
        ORDER BY line_number
        RANGE BETWEEN 5 PRECEDING AND 5 FOLLOWING
    ) as sum_range
FROM frames
WHERE profile_id = 'profile1'
ORDER BY line_number
    LIMIT 6;
```

**Result:**
```
frame_hash | line_number | sum_range
-----------|-------------|----------
12345      | 10          | 70       <- Includes all rows where line_number is 5-15 (10, 15, 15)
12346      | 15          | 125      <- Includes all rows where line_number is 10-20 (10, 15, 15, 20, 20, 20)
12389      | 15          | 125      <- Same as above (ties have same result)
12347      | 20          | 185      <- Includes all rows where line_number is 15-25
12348      | 25          | 210      <- Includes all rows where line_number is 20-30
12349      | 30          | 240      <- Includes all rows where line_number is 25-35
```

## Practical Examples for Your Profiling Schema

### Example 1: Top N Hot Methods per Profile

```sql
-- Find top 5 most called methods in each profile
WITH method_counts AS (
    SELECT
        profile_id,
        class_name,
        method_name,
        COUNT(*) as call_count
    FROM frames
    GROUP BY profile_id, class_name, method_name
),
     ranked_methods AS (
         SELECT
             profile_id,
             class_name,
             method_name,
             call_count,
             RANK() OVER (PARTITION BY profile_id ORDER BY call_count DESC) as rank
         FROM method_counts
     )
SELECT *
FROM ranked_methods
WHERE rank <= 5
ORDER BY profile_id, rank;
```

**Result:**
```
profile_id | class_name      | method_name  | call_count | rank
-----------|-----------------|--------------|------------|-----
profile1   | HttpServlet     | doGet        | 1250       | 1
profile1   | DatabasePool    | getConnection| 1100       | 2
profile1   | JSONParser      | parse        | 950        | 3
profile1   | Authentication  | validate     | 820        | 4
profile1   | CacheManager    | get          | 780        | 5
profile2   | RequestHandler  | handle       | 890        | 1
profile2   | LoggingService  | log          | 750        | 2
profile2   | DataValidator   | validate     | 680        | 3
profile2   | SessionManager  | getSession   | 620        | 4
profile2   | ConfigLoader    | load         | 550        | 5
```

### Example 2: Frame Type Distribution within Stacktraces

```sql
-- Analyze frame type distribution in each stacktrace
WITH stacktrace_frames AS (
    SELECT
        s.profile_id,
        s.stacktrace_hash,
        s.type_id,
        f.frame_hash,
        f.frame_type,
        f.class_name,
        ROW_NUMBER() OVER (
            PARTITION BY s.stacktrace_hash 
            ORDER BY idx
        ) as position_in_stack
    FROM stacktraces s
             CROSS JOIN UNNEST(s.frame_hashes) WITH ORDINALITY AS t(frame_hash_val, idx)
             JOIN frames f
                  ON f.profile_id = s.profile_id
                      AND f.frame_hash = t.frame_hash_val
    WHERE s.stacktrace_hash = 98765  -- Example stacktrace
)
SELECT
    profile_id,
    stacktrace_hash,
    position_in_stack,
    frame_type,
    class_name,
    -- What percentage of the stack have we covered?
    ROUND(100.0 * position_in_stack /
          COUNT(*) OVER (PARTITION BY stacktrace_hash), 2) as pct_of_stack,
    -- Is this the first JIT frame in the stack?
    ROW_NUMBER() OVER (
        PARTITION BY stacktrace_hash, frame_type 
        ORDER BY position_in_stack
    ) as nth_of_this_type
FROM stacktrace_frames
ORDER BY position_in_stack
    LIMIT 10;
```

**Result:**
```
profile_id | stacktrace_hash | position_in_stack | frame_type  | class_name    | pct_of_stack | nth_of_this_type
-----------|-----------------|-------------------|-------------|---------------|--------------|------------------
profile1   | 98765           | 1                 | JIT         | HttpServlet   | 5.00         | 1
profile1   | 98765           | 2                 | JIT         | FilterChain   | 10.00        | 2
profile1   | 98765           | 3                 | JIT         | AuthFilter    | 15.00        | 3
profile1   | 98765           | 4                 | Interpreted | DataService   | 20.00        | 1
profile1   | 98765           | 5                 | Interpreted | DatabaseDAO   | 25.00        | 2
profile1   | 98765           | 6                 | Native      | JDBCDriver    | 30.00        | 1
profile1   | 98765           | 7                 | Native      | SocketRead    | 35.00        | 2
profile1   | 98765           | 8                 | JIT         | ResponseMgr   | 40.00        | 4
profile1   | 98765           | 9                 | JIT         | LoggingUtil   | 45.00        | 5
profile1   | 98765           | 10                | Interpreted | ErrorHandler  | 50.00        | 3
```

### Example 3: Detect Recursive Calls

```sql
-- Find methods that call themselves (appear multiple times in same stacktrace)
WITH stacktrace_frames AS (
    SELECT
        s.stacktrace_hash,
        s.profile_id,
        f.class_name,
        f.method_name,
        ROW_NUMBER() OVER (
            PARTITION BY s.stacktrace_hash 
            ORDER BY idx
        ) as depth
    FROM stacktraces s
             CROSS JOIN UNNEST(s.frame_hashes) WITH ORDINALITY AS t(fh, idx)
             JOIN frames f ON f.profile_id = s.profile_id AND f.frame_hash = t.fh
)
SELECT
    stacktrace_hash,
    profile_id,
    class_name,
    method_name,
    depth,
    LAG(depth) OVER (
        PARTITION BY stacktrace_hash, class_name, method_name 
        ORDER BY depth
    ) as prev_depth,
    depth - LAG(depth) OVER (
        PARTITION BY stacktrace_hash, class_name, method_name 
        ORDER BY depth
    ) as recursion_depth
FROM stacktrace_frames
WHERE class_name IS NOT NULL AND method_name IS NOT NULL
  AND COUNT(*) OVER (
        PARTITION BY stacktrace_hash, class_name, method_name
    ) > 1  -- Method appears more than once in this stack
ORDER BY stacktrace_hash, depth
    LIMIT 10;
```

**Result:**
```
stacktrace_hash | profile_id | class_name    | method_name | depth | prev_depth | recursion_depth
----------------|------------|---------------|-------------|-------|------------|----------------
55123           | profile1   | FileProcessor | processDir  | 3     | NULL       | NULL           <- First call
55123           | profile1   | FileProcessor | processDir  | 8     | 3          | 5              <- Recursive call
55123           | profile1   | FileProcessor | processDir  | 13    | 8          | 5              <- Recursive call
55123           | profile1   | FileProcessor | processDir  | 18    | 13         | 5              <- Recursive call
67890           | profile1   | TreeTraversal | traverse    | 2     | NULL       | NULL           <- First call
67890           | profile1   | TreeTraversal | traverse    | 5     | 2          | 3              <- Recursive call
67890           | profile1   | TreeTraversal | traverse    | 8     | 5          | 3              <- Recursive call
67890           | profile1   | TreeTraversal | traverse    | 11    | 8          | 3              <- Recursive call
67890           | profile1   | TreeTraversal | traverse    | 14    | 11         | 3              <- Recursive call
67890           | profile1   | TreeTraversal | traverse    | 17    | 14         | 3              <- Recursive call
```

### Example 4: Stack Depth Analysis

```sql
-- Analyze stack depth patterns
WITH stack_depths AS (
    SELECT
        profile_id,
        stacktrace_hash,
        type_id,
        array_length(frame_hashes) as depth
    FROM stacktraces
)
SELECT
    profile_id,
    stacktrace_hash,
    depth,
    -- Rank by depth within profile
    RANK() OVER (PARTITION BY profile_id ORDER BY depth DESC) as depth_rank,
    -- Average depth in profile
    ROUND(AVG(depth) OVER (PARTITION BY profile_id), 2) as avg_depth_in_profile,
    -- Percentile ranking
    ROUND(PERCENT_RANK() OVER (PARTITION BY profile_id ORDER BY depth), 3) as depth_percentile,
    -- Quartile
    NTILE(4) OVER (PARTITION BY profile_id ORDER BY depth) as depth_quartile,
    -- Is this an outlier? (> 2 std devs from mean)
    CASE
        WHEN ABS(depth - AVG(depth) OVER (PARTITION BY profile_id)) >
             2 * STDDEV(depth) OVER (PARTITION BY profile_id)
        THEN true
        ELSE false
        END as is_outlier
FROM stack_depths
WHERE profile_id = 'profile1'
ORDER BY depth DESC
    LIMIT 10;
```

**Result:**
```
profile_id | stacktrace_hash | depth | depth_rank | avg_depth_in_profile | depth_percentile | depth_quartile | is_outlier
-----------|-----------------|-------|------------|---------------------|------------------|----------------|------------
profile1   | 12345           | 125   | 1          | 42.50               | 0.998            | 4              | true
profile1   | 12346           | 98    | 2          | 42.50               | 0.996            | 4              | true
profile1   | 12347           | 87    | 3          | 42.50               | 0.994            | 4              | true
profile1   | 12348           | 72    | 4          | 42.50               | 0.992            | 4              | false
profile1   | 12349           | 68    | 5          | 42.50               | 0.990            | 4              | false
profile1   | 12350           | 55    | 6          | 42.50               | 0.800            | 3              | false
profile1   | 12351           | 48    | 7          | 42.50               | 0.700            | 3              | false
profile1   | 12352           | 42    | 8          | 42.50               | 0.500            | 2              | false
profile1   | 12353           | 35    | 9          | 42.50               | 0.400            | 2              | false
profile1   | 12354           | 28    | 10         | 42.50               | 0.300            | 1              | false
```

### Example 5: Frame Type Transitions

```sql
-- Analyze transitions between frame types (JIT -> Interpreted, etc.)
WITH frame_transitions AS (
    SELECT
        s.profile_id,
        s.stacktrace_hash,
        f.frame_type,
        LEAD(f.frame_type) OVER (
            PARTITION BY s.stacktrace_hash 
            ORDER BY idx
        ) as next_frame_type,
        ROW_NUMBER() OVER (
            PARTITION BY s.stacktrace_hash 
            ORDER BY idx
        ) as position
FROM stacktraces s
    CROSS JOIN UNNEST(s.frame_hashes) WITH ORDINALITY AS t(fh, idx)
    JOIN frames f ON f.profile_id = s.profile_id AND f.frame_hash = t.fh
WHERE s.profile_id = 'profile1'
    )
SELECT
    profile_id,
    frame_type || ' -> ' || next_frame_type as transition,
    COUNT(*) as transition_count,
    ROUND(100.0 * COUNT(*) / SUM(COUNT(*)) OVER (PARTITION BY profile_id), 2) as pct_of_transitions,
    RANK() OVER (PARTITION BY profile_id ORDER BY COUNT(*) DESC) as transition_rank
FROM frame_transitions
WHERE next_frame_type IS NOT NULL
GROUP BY profile_id, frame_type, next_frame_type
ORDER BY transition_count DESC
    LIMIT 10;
```

**Result:**
```
profile_id | transition              | transition_count | pct_of_transitions | transition_rank
-----------|-------------------------|------------------|-------------------|----------------
profile1   | JIT -> JIT              | 4250             | 42.50             | 1
profile1   | JIT -> Interpreted      | 1820             | 18.20             | 2
profile1   | Interpreted -> JIT      | 1650             | 16.50             | 3
profile1   | Interpreted -> Native   | 980              | 9.80              | 4
profile1   | Native -> Interpreted   | 520              | 5.20              | 5
profile1   | JIT -> Native           | 380              | 3.80              | 6
profile1   | Native -> JIT           | 240              | 2.40              | 7
profile1   | Interpreted->Interpreted| 160              | 1.60              | 8
```

### Example 6: Comparative Analysis Across Profiles

```sql
-- Compare frame distributions across profiles
WITH frame_stats AS (
    SELECT
        profile_id,
        frame_type,
        COUNT(*) as count
FROM frames
GROUP BY profile_id, frame_type
    )
SELECT
    profile_id,
    frame_type,
    count,
    -- Percentage within profile
    ROUND(100.0 * count / SUM(count) OVER (PARTITION BY profile_id), 2) as pct_in_profile,
    -- Rank within profile
    RANK() OVER (PARTITION BY profile_id ORDER BY count DESC) as rank_in_profile,
    -- Compare to other profiles with same frame type
    ROUND(100.0 * count / AVG(count) OVER (PARTITION BY frame_type), 2) as pct_vs_avg,
    -- Overall rank across all profiles and types
    RANK() OVER (ORDER BY count DESC) as overall_rank
FROM frame_stats
ORDER BY profile_id, count DESC;
```

**Result:**
```
profile_id | frame_type  | count | pct_in_profile | rank_in_profile | pct_vs_avg | overall_rank
-----------|-------------|-------|----------------|-----------------|------------|-------------
profile1   | JIT         | 900   | 60.00          | 1               | 150.00     | 1
profile1   | Interpreted | 450   | 30.00          | 2               | 112.50     | 3
profile1   | Native      | 150   | 10.00          | 3               | 75.00      | 5
profile2   | JIT         | 600   | 75.00          | 1               | 100.00     | 2
profile2   | Interpreted | 150   | 18.75          | 2               | 37.50      | 6
profile2   | Native      | 50    | 6.25           | 3               | 25.00      | 8
profile3   | Interpreted | 800   | 57.14          | 1               | 200.00     | 1
profile3   | JIT         | 400   | 28.57          | 2               | 66.67      | 4
profile3   | Native      | 200   | 14.29          | 3               | 100.00     | 7
```

### Example 7: Gap Analysis

```sql
-- Find gaps in line numbers (missing lines, jumps in execution)
WITH line_analysis AS (
    SELECT
        profile_id,
        class_name,
        line_number,
        LAG(line_number) OVER (
            PARTITION BY profile_id, class_name 
            ORDER BY line_number
        ) as prev_line,
        LEAD(line_number) OVER (
            PARTITION BY profile_id, class_name 
            ORDER BY line_number
        ) as next_line
    FROM frames
    WHERE line_number IS NOT NULL AND profile_id = 'profile1'
)
SELECT
    profile_id,
    class_name,
    line_number,
    prev_line,
    next_line,
    line_number - prev_line as gap_before,
    next_line - line_number as gap_after,
    CASE
        WHEN line_number - prev_line > 50 THEN 'Large gap before'
        WHEN next_line - line_number > 50 THEN 'Large gap after'
        ELSE 'Normal'
        END as gap_status
FROM line_analysis
WHERE (line_number - prev_line > 50 OR next_line - line_number > 50)
ORDER BY profile_id, class_name, line_number
    LIMIT 10;
```

**Result:**
```
profile_id | class_name      | line_number | prev_line | next_line | gap_before | gap_after | gap_status
-----------|-----------------|-------------|-----------|-----------|------------|-----------|------------------
profile1   | FileProcessor   | 45          | NULL      | 120       | NULL       | 75        | Large gap after
profile1   | FileProcessor   | 120         | 45        | 135       | 75         | 15        | Large gap before
profile1   | RequestHandler  | 200         | 145       | 320       | 55         | 120       | Large gap before
profile1   | RequestHandler  | 320         | 200       | 335       | 120        | 15        | Large gap before
profile1   | DataValidator   | 88          | 25        | 95        | 63         | 7         | Large gap before
profile1   | CacheManager    | 150         | 90        | 275       | 60         | 125       | Large gap before
profile1   | CacheManager    | 275         | 150       | 285       | 125        | 10        | Large gap before
```

### Example 8: Deduplicate with ROW_NUMBER

```sql
-- Remove duplicate frames, keeping the first occurrence
WITH ranked_frames AS (
    SELECT
        profile_id,
        frame_hash,
        class_name,
        method_name,
        frame_type,
        line_number,
        bytecode_index,
        ROW_NUMBER() OVER (
            PARTITION BY profile_id, frame_hash 
            ORDER BY line_number NULLS LAST, bytecode_index NULLS LAST
        ) as rn
    FROM frames
    WHERE profile_id = 'profile1'
)
SELECT
    profile_id,
    frame_hash,
    class_name,
    method_name,
    frame_type,
    line_number,
    bytecode_index
FROM ranked_frames
WHERE rn = 1
ORDER BY frame_hash
    LIMIT 8;
```

**Result:**
```
profile_id | frame_hash | class_name    | method_name | frame_type  | line_number | bytecode_index
-----------|------------|---------------|-------------|-------------|-------------|----------------
profile1   | 12345      | HttpServlet   | doGet       | JIT         | 42          | 10
profile1   | 12346      | FilterChain   | doFilter    | JIT         | 58          | 15
profile1   | 12347      | AuthFilter    | validate    | Interpreted | 125         | 8
profile1   | 12348      | DataService   | fetchData   | JIT         | 89          | 22
profile1   | 12349      | DatabaseDAO   | query       | Native      | 200         | NULL
profile1   | 12350      | CacheManager  | get         | JIT         | 45          | 5
profile1   | 12351      | LoggingUtil   | log         | Interpreted | 33          | 12
profile1   | 12352      | ResponseMgr   | send        | JIT         | 67          | 18
```

## Performance Tips

### 1. **Reuse Window Definitions**

```sql
-- ❌ REPETITIVE: Define window multiple times
SELECT
    profile_id,
    frame_hash,
    line_number,
    ROW_NUMBER() OVER (PARTITION BY profile_id ORDER BY line_number) as row_num,
    RANK() OVER (PARTITION BY profile_id ORDER BY line_number) as rank,
    DENSE_RANK() OVER (PARTITION BY profile_id ORDER BY line_number) as dense_rank
FROM frames
WHERE profile_id = 'profile1'
    LIMIT 5;
```

**Result:**
```
profile_id | frame_hash | line_number | row_num | rank | dense_rank
-----------|------------|-------------|---------|------|------------
profile1   | 12345      | 10          | 1       | 1    | 1
profile1   | 12346      | 15          | 2       | 2    | 2
profile1   | 12389      | 15          | 3       | 2    | 2
profile1   | 12347      | 20          | 4       | 4    | 3
profile1   | 12348      | 25          | 5       | 5    | 4
```

```sql
-- ✅ EFFICIENT: Define window once
SELECT
    profile_id,
    frame_hash,
    line_number,
    ROW_NUMBER() OVER w as row_num,
    RANK() OVER w as rank,
    DENSE_RANK() OVER w as dense_rank
FROM frames
WHERE profile_id = 'profile1'
WINDOW w AS (PARTITION BY profile_id ORDER BY line_number)
ORDER BY line_number
    LIMIT 5;
```

**Result (same as above, but more efficient):**
```
profile_id | frame_hash | line_number | row_num | rank | dense_rank
-----------|------------|-------------|---------|------|------------
profile1   | 12345      | 10          | 1       | 1    | 1
profile1   | 12346      | 15          | 2       | 2    | 2
profile1   | 12389      | 15          | 3       | 2    | 2
profile1   | 12347      | 20          | 4       | 4    | 3
profile1   | 12348      | 25          | 5       | 5    | 4
```

## Common Pitfalls

### 2. **WHERE on Window Functions**

```sql
-- ❌ WRONG: Can't use window functions in WHERE
SELECT *
FROM frames
WHERE ROW_NUMBER() OVER (PARTITION BY profile_id ORDER BY line_number) <= 10;
-- ERROR: Window functions cannot be used in WHERE clause
```

```sql
-- ✅ CORRECT: Use subquery or CTE
WITH ranked AS (
    SELECT
        profile_id,
        frame_hash,
        class_name,
        method_name,
        line_number,
        ROW_NUMBER() OVER (PARTITION BY profile_id ORDER BY line_number) as rn
    FROM frames
)
SELECT
    profile_id,
    frame_hash,
    class_name,
    method_name,
    line_number
FROM ranked
WHERE rn <= 3
ORDER BY profile_id, rn;
```

**Result:**
```
profile_id | frame_hash | class_name  | method_name | line_number
-----------|------------|-------------|-------------|------------
profile1   | 12345      | ClassA      | methodA     | 10
profile1   | 12346      | ClassB      | methodB     | 15
profile1   | 12389      | ClassB      | methodC     | 15
profile2   | 22345      | ClassX      | methodX     | 5
profile2   | 22346      | ClassY      | methodY     | 10
profile2   | 22347      | ClassZ      | methodZ     | 15
```

## Key Takeaways

1. **Window functions don't collapse rows** - unlike GROUP BY
2. **PARTITION BY** = which groups to calculate over
3. **ORDER BY** = order within each partition
4. **Frame clause** = which rows to include in calculation
5. **Use named windows** (WINDOW clause) to avoid repetition
6. **Filter before** window functions when possible
7. **Index** PARTITION BY and ORDER BY columns
8. **ROWS vs RANGE** - physical vs logical boundaries
9. **Always specify frame** for LAST_VALUE to avoid confusion
10. **Use CTEs** to filter on window function results

Window functions are incredibly powerful for profiling analysis - they let you rank hot methods, detect patterns in stack traces, calculate running statistics, and perform complex comparative analysis all while keeping your row-level detail intact!
