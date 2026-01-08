# Eclipse MAT Batch Mode Reference for Jeffrey

A comprehensive guide to using Eclipse Memory Analyzer Tool (MAT) in batch mode for heap dump analysis and integration with Jeffrey profiler.

## Table of Contents

1. [Setup and Prerequisites](#setup-and-prerequisites)
2. [Basic Usage](#basic-usage)
3. [Standard Reports](#standard-reports)
4. [Query Commands](#query-commands)
   - [Core Analysis](#core-analysis)
   - [Thread Analysis](#thread-analysis)
   - [ClassLoader Analysis](#classloader-analysis)
   - [Collection Analysis](#collection-analysis)
   - [String Analysis](#string-analysis)
   - [Reference Analysis](#reference-analysis)
   - [Leak Detection](#leak-detection)
   - [Comparison Reports](#comparison-reports)
5. [OQL Queries](#oql-queries)
6. [Output Formats and Parsing](#output-formats-and-parsing)
7. [Integration Scripts](#integration-scripts)

---

## Setup and Prerequisites

### Download MAT Standalone

```bash
# Download latest MAT (check https://eclipse.dev/mat/downloads.php for current version)
wget https://download.eclipse.org/mat/1.15.0/rcp/MemoryAnalyzer-1.15.0.20231206-linux.gtk.x86_64.zip
unzip MemoryAnalyzer-1.15.0.20231206-linux.gtk.x86_64.zip
cd mat
```

### Configure Memory

Edit `MemoryAnalyzer.ini` for large heap dumps:

```ini
-startup
plugins/org.eclipse.equinox.launcher_1.6.400.v20210924-0641.jar
--launcher.library
plugins/org.eclipse.equinox.launcher.gtk.linux.x86_64_1.2.400.v20211117-0650
-vmargs
-Xms4g
-Xmx16g
```

### Verify Installation

```bash
# ParseHeapDump.sh does not have --help, just run without arguments to see if it works
./ParseHeapDump.sh
```

---

## Basic Usage

### General Syntax

```bash
./ParseHeapDump.sh <heap_dump_file> [options] [report_id|query]
```

### Common Options

| Option | Description |
|--------|-------------|
| `-keep_unreachable_objects` | Retain objects not reachable from GC roots |
| `-snapshot_identifier=<id>` | Select specific snapshot if multiple exist |
| `-command=<query>` | Run a specific query |
| `-format=txt` | Output as plain text (default: HTML) |
| `-unzip` | Don't zip the output |
| `-limit=<n>` | Limit number of rows in output |

### Output Location

All outputs are created in the same directory as the heap dump:
- Index files: `*.index`, `*.threads`, etc.
- Reports: `*_<ReportName>.zip` or unzipped files with `-unzip`

---

## Standard Reports

### Leak Suspects Report

Automatic analysis of potential memory leaks.

```bash
./ParseHeapDump.sh dump.hprof org.eclipse.mat.api:suspects
```

**Output:** `dump_Leak_Suspects.zip` containing HTML report

**Parsable format:**
```bash
./ParseHeapDump.sh dump.hprof -command=leak_hunter -format=txt -unzip org.eclipse.mat.api:query
```

---

### Overview Report

General heap dump overview with system properties, threads, and top consumers.

```bash
./ParseHeapDump.sh dump.hprof org.eclipse.mat.api:overview
```

**Output:** `dump_System_Overview.zip`

---

### Top Components Report

Memory usage by component/package.

```bash
./ParseHeapDump.sh dump.hprof org.eclipse.mat.api:top_components
```

**Output:** `dump_Top_Components.zip`

---

### Run Multiple Reports

```bash
./ParseHeapDump.sh dump.hprof \
    org.eclipse.mat.api:suspects \
    org.eclipse.mat.api:overview \
    org.eclipse.mat.api:top_components
```

---

## Query Commands

### Core Analysis

#### Histogram

Class-level memory distribution.

```bash
# HTML output
./ParseHeapDump.sh dump.hprof -command=histogram org.eclipse.mat.api:query

# Parsable text output
./ParseHeapDump.sh dump.hprof -command=histogram -format=txt -unzip org.eclipse.mat.api:query
```

**Output format (txt):**
```
Class Name                                     | Objects |  Shallow Heap | Retained Heap
-----------------------------------------------|---------|---------------|---------------
byte[]                                         | 145,678 |   523,456,789 |   523,456,789
char[]                                         |  98,234 |   234,567,890 |   234,567,890
java.lang.String                               |  87,456 |     2,800,000 |   237,000,000
java.lang.Object[]                             |  45,123 |     1,234,567 |    89,000,000
```

---

#### Dominator Tree

Hierarchical view of memory ownership.

```bash
./ParseHeapDump.sh dump.hprof -command=dominator_tree -format=txt -unzip org.eclipse.mat.api:query
```

**Output format:**
```
Class Name                                     | Shallow Heap | Retained Heap |    Percent
-----------------------------------------------|--------------|---------------|------------
java.lang.Thread @ 0x7f3c4d900000 main         |          120 |   523,456,789 |     58.76%
├─ com.example.CacheManager @ 0x7f3c4d900100   |           48 |   456,789,012 |     51.28%
│  └─ java.util.HashMap @ 0x7f3c4d900200       |           48 |   400,000,000 |     44.91%
```

---

#### GC Roots

List all garbage collection roots by type.

```bash
./ParseHeapDump.sh dump.hprof -command=gc_roots -format=txt -unzip org.eclipse.mat.api:query
```

**Output format:**
```
GC Root Type                                   | Objects | Shallow Heap
-----------------------------------------------|---------|-------------
Thread                                         |      45 |       5,400
JNI Global                                     |     123 |      14,760
System Class                                   |   2,456 |     294,720
```

---

#### Biggest Objects

Top objects by retained heap size.

```bash
./ParseHeapDump.sh dump.hprof -command=biggest_objects -format=txt -unzip org.eclipse.mat.api:query
```

---

#### Top Consumers

Top memory consumers grouped by class, classloader, and package.

```bash
./ParseHeapDump.sh dump.hprof -command=top_consumers -format=txt -unzip org.eclipse.mat.api:query
```

---

#### Unreachable Objects Histogram

Objects that would be garbage collected.

```bash
./ParseHeapDump.sh dump.hprof -command=unreachable_objects_histogram -format=txt -unzip org.eclipse.mat.api:query
```

---

#### System Properties

JVM system properties at dump time.

```bash
./ParseHeapDump.sh dump.hprof -command=system_properties -format=txt -unzip org.eclipse.mat.api:query
```

---

### Thread Analysis

#### Thread Overview

All threads with their retained heap and stack information.

```bash
./ParseHeapDump.sh dump.hprof -command=thread_overview -format=txt -unzip org.eclipse.mat.api:query
```

**Output format:**
```
Name                          | Shallow Heap | Retained Heap | Context Class Loader          | Is Daemon
------------------------------|--------------|---------------|-------------------------------|----------
main                          |          120 |   523,456,789 | sun.misc.Launcher$AppClass... | false
pool-2-thread-1               |          120 |    12,345,678 | sun.misc.Launcher$AppClass... | true
GC Daemon                     |          120 |         1,234 | null                          | true
```

---

#### Thread Stacks

Thread stack traces (available in `.threads` file after parsing).

```bash
# First parse to generate .threads file
./ParseHeapDump.sh dump.hprof

# The threads file is plain text
cat dump.threads
```

---

### ClassLoader Analysis

#### ClassLoader Explorer

List all classloaders with their defined classes.

```bash
./ParseHeapDump.sh dump.hprof -command=class_loader_explorer -format=txt -unzip org.eclipse.mat.api:query
```

**Output format:**
```
Class Loader                                   | Defined Classes | Retained Heap
-----------------------------------------------|-----------------|---------------
sun.misc.Launcher$AppClassLoader @ 0x7f3c...   |           2,456 |   123,456,789
sun.misc.Launcher$ExtClassLoader @ 0x7f3c...   |             234 |    12,345,678
<bootstrap>                                    |           1,890 |    89,012,345
```

---

#### Duplicate Classes

Classes loaded by multiple classloaders (potential classloader leak).

```bash
./ParseHeapDump.sh dump.hprof -command=duplicate_classes -format=txt -unzip org.eclipse.mat.api:query
```

**Output format:**
```
Class Name                                     | Count | Class Loaders
-----------------------------------------------|-------|-----------------------------------------------
org.apache.commons.logging.Log                 |     3 | WebAppClassLoader@1, WebAppClassLoader@2...
com.google.common.collect.ImmutableList        |     2 | WebAppClassLoader@1, WebAppClassLoader@2
```

---

### Collection Analysis

#### Collection Fill Ratio

Efficiency of collection backing arrays.

```bash
./ParseHeapDump.sh dump.hprof -command=collection_fill_ratio -format=txt -unzip org.eclipse.mat.api:query
```

**Output format:**
```
Collection Class                               | Fill Ratio |   Objects | Wasted Memory
-----------------------------------------------|------------|-----------|---------------
java.util.ArrayList                            |       12%  |     5,432 |     2,345,678
java.util.HashMap                              |       45%  |     3,456 |     1,234,567
java.util.HashSet                              |       23%  |     2,345 |       567,890
```

---

#### Collections Grouped by Size

Distribution of collection sizes.

```bash
./ParseHeapDump.sh dump.hprof -command=collections_grouped_by_size -format=txt -unzip org.eclipse.mat.api:query
```

**Output format:**
```
Size      | Collections | Total Shallow Heap
----------|-------------|-------------------
0         |      12,345 |         1,234,500
1-4       |       8,765 |           876,500
5-10      |       3,456 |           345,600
11-50     |       1,234 |           123,400
51-100    |         456 |            45,600
>100      |         123 |            12,300
```

---

#### Array Fill Ratio

Efficiency of array usage.

```bash
./ParseHeapDump.sh dump.hprof -command=array_fill_ratio -format=txt -unzip org.eclipse.mat.api:query
```

---

#### Arrays Grouped by Size

Distribution of array sizes.

```bash
./ParseHeapDump.sh dump.hprof -command=arrays_grouped_by_size -format=txt -unzip org.eclipse.mat.api:query
```

---

#### Map Collision Ratio

HashMap collision analysis (indicates poor hashCode implementations).

```bash
./ParseHeapDump.sh dump.hprof -command=map_collision_ratio -format=txt -unzip org.eclipse.mat.api:query
```

**Output format:**
```
Collection                                     | Collision Ratio |   Objects | Size
-----------------------------------------------|-----------------|-----------|------
java.util.HashMap @ 0x7f3c4d900100             |           85.2% |         1 | 1024
java.util.HashMap @ 0x7f3c4d900200             |           72.1% |         1 |  512
```

---

#### Hash Entries

Extract key-value pairs from HashMaps.

```bash
./ParseHeapDump.sh dump.hprof "-command=hash_entries 0x7f3c4d900100" -format=txt -unzip org.eclipse.mat.api:query
```

---

#### Primitive Arrays with Constant Value

Arrays filled with single value (potential waste).

```bash
./ParseHeapDump.sh dump.hprof -command=primitive_arrays_with_a_constant_value -format=txt -unzip org.eclipse.mat.api:query
```

---

### String Analysis

#### Find Strings

Find strings matching a pattern.

```bash
./ParseHeapDump.sh dump.hprof "-command=find_strings -pattern=.*password.*" -format=txt -unzip org.eclipse.mat.api:query
```

---

#### Duplicate Strings (Group by Value)

Strings with identical content.

```bash
./ParseHeapDump.sh dump.hprof -command=group_by_value -format=txt -unzip org.eclipse.mat.api:query
```

**Output format:**
```
Value                                          | Count | Shallow Heap | Wasted
-----------------------------------------------|-------|--------------|--------
"UTF-8"                                        | 1,234 |       98,720 |  98,640
"true"                                         |   987 |       78,960 |  78,880
"null"                                         |   876 |       70,080 |  70,000
```

---

#### Waste in Char Arrays

Wasted space in String backing arrays (pre-Java 9).

```bash
./ParseHeapDump.sh dump.hprof -command=waste_in_char_arrays -format=txt -unzip org.eclipse.mat.api:query
```

---

### Reference Analysis

#### Soft Reference Statistics

Analysis of SoftReference objects.

```bash
./ParseHeapDump.sh dump.hprof -command=soft_reference_statistics -format=txt -unzip org.eclipse.mat.api:query
```

**Output format:**
```
Category                                       |   Objects | Shallow Heap | Retained Heap
-----------------------------------------------|-----------|--------------|---------------
Soft References                                |   217,035 |   17,362,800 |    17,362,800
Softly Referenced Objects                      |    38,874 |    3,109,920 |   234,567,890
Only Softly Retained                           |    77,745 |   20,800,000 |    20,800,000
Referents Strongly Retained (Potential Leak)   |         0 |            0 |             0
```

---

#### Weak Reference Statistics

Analysis of WeakReference objects.

```bash
./ParseHeapDump.sh dump.hprof -command=weak_reference_statistics -format=txt -unzip org.eclipse.mat.api:query
```

---

#### Phantom Reference Statistics

Analysis of PhantomReference objects.

```bash
./ParseHeapDump.sh dump.hprof -command=phantom_reference_statistics -format=txt -unzip org.eclipse.mat.api:query
```

---

#### Reference Leak

Detect potential soft/weak reference leaks.

```bash
./ParseHeapDump.sh dump.hprof -command=reference_leak -format=txt -unzip org.eclipse.mat.api:query
```

---

#### Finalizer Overview

Objects pending finalization.

```bash
./ParseHeapDump.sh dump.hprof -command=finalizer_overview -format=txt -unzip org.eclipse.mat.api:query
```

**Output format:**
```
Finalizer Queue Statistics
--------------------------
Finalizer Thread:                               java.lang.ref.Finalizer$FinalizerThread @ 0x7f3c...
Queue Length:                                   1,234
Total Retained by Queue:                        12,345,678 bytes

Top Finalizables by Class:
Class Name                                     | Count | Retained Heap
-----------------------------------------------|-------|---------------
java.util.zip.ZipFile                          |   456 |     4,560,000
java.io.FileInputStream                        |   234 |       234,000
```

---

### Leak Detection

#### Leak Hunter

Automatic leak suspect analysis.

```bash
./ParseHeapDump.sh dump.hprof -command=leak_hunter -format=txt -unzip org.eclipse.mat.api:query
```

**Output format:**
```
Leak Suspect #1
---------------
Object:          com.example.CacheManager @ 0x7f3c4d900100
Retained Heap:   456,789,012 bytes (51.28% of total)
Accumulation Point: java.util.HashMap @ 0x7f3c4d900200

Description:
One instance of "com.example.CacheManager" loaded by "sun.misc.Launcher$AppClassLoader"
occupies 456,789,012 bytes (51.28% of the total heap).

Keywords:
CacheManager, HashMap, cache, data
```

---

#### Big Drops

Objects with disproportionately large retained heap compared to shallow heap.

```bash
./ParseHeapDump.sh dump.hprof -command=big_drops -format=txt -unzip org.eclipse.mat.api:query
```

---

#### Immediate Dominators

Find what is keeping objects alive (requires object selection).

```bash
# Via OQL
./ParseHeapDump.sh dump.hprof "-command=oql \"SELECT OBJECTS dominators(s) FROM java.lang.String s WHERE s.@retainedHeapSize > 1000000\"" -format=txt -unzip org.eclipse.mat.api:query
```

---

#### Path to GC Roots

Find retention path (requires specific object address).

```bash
./ParseHeapDump.sh dump.hprof "-command=path_to_gc_roots 0x7f3c4d900100" -format=txt -unzip org.eclipse.mat.api:query
```

---

### Comparison Reports

#### Compare Two Heap Dumps

Side-by-side comparison of heap state.

```bash
./ParseHeapDump.sh dump1.hprof -snapshot2=dump2.hprof org.eclipse.mat.api:compare
```

**Output:** `dump1_Compare.zip` with delta analysis

---

#### Leak Suspects with Baseline

Find new leaks compared to baseline dump.

```bash
./ParseHeapDump.sh dump2.hprof -baseline=dump1.hprof org.eclipse.mat.api:suspects2
```

---

## OQL Queries

Object Query Language allows custom heap analysis.

### Basic Syntax

```bash
./ParseHeapDump.sh dump.hprof "-command=oql \"<OQL_QUERY>\"" -format=txt -unzip org.eclipse.mat.api:query
```

### Escaping Rules

- Wrap entire command in double quotes
- Escape inner double quotes with backslash: `\"`
- On Windows, use 7 backslashes before inner quotes: `\\\\\\\"`

---

### Common OQL Queries

#### Top Classes by Retained Heap

```bash
./ParseHeapDump.sh dump.hprof "-command=oql \"SELECT * FROM INSTANCEOF java.lang.Object o WHERE o.@retainedHeapSize > 1000000\"" -format=txt -unzip org.eclipse.mat.api:query
```

---

#### Large Strings

```bash
./ParseHeapDump.sh dump.hprof "-command=oql \"SELECT s, toString(s), s.@retainedHeapSize FROM java.lang.String s WHERE s.@retainedHeapSize > 10000\"" -format=txt -unzip org.eclipse.mat.api:query
```

---

#### Thread Names and Retained Heap

```bash
./ParseHeapDump.sh dump.hprof "-command=oql \"SELECT t, toString(t.name), t.@retainedHeapSize FROM java.lang.Thread t\"" -format=txt -unzip org.eclipse.mat.api:query
```

---

#### ClassLoader Memory Usage

```bash
./ParseHeapDump.sh dump.hprof "-command=oql \"SELECT cl, cl.@displayName, cl.@retainedHeapSize FROM INSTANCEOF java.lang.ClassLoader cl\"" -format=txt -unzip org.eclipse.mat.api:query
```

---

#### Large HashMaps

```bash
./ParseHeapDump.sh dump.hprof "-command=oql \"SELECT m, m.size, m.@retainedHeapSize FROM java.util.HashMap m WHERE m.size > 1000\"" -format=txt -unzip org.eclipse.mat.api:query
```

---

#### ThreadLocal Analysis

```bash
./ParseHeapDump.sh dump.hprof "-command=oql \"SELECT t, t.threadLocals FROM java.lang.Thread t WHERE t.threadLocals != null\"" -format=txt -unzip org.eclipse.mat.api:query
```

---

#### NIO Buffer Analysis

```bash
./ParseHeapDump.sh dump.hprof "-command=oql \"SELECT b, b.capacity, b.@retainedHeapSize FROM INSTANCEOF java.nio.Buffer b WHERE b.capacity > 1000000\"" -format=txt -unzip org.eclipse.mat.api:query
```

---

#### HTTP Session Analysis (Web Apps)

```bash
./ParseHeapDump.sh dump.hprof "-command=oql \"SELECT s, s.@retainedHeapSize FROM INSTANCEOF javax.servlet.http.HttpSession s\"" -format=txt -unzip org.eclipse.mat.api:query
```

---

#### JDBC Connection Pool

```bash
./ParseHeapDump.sh dump.hprof "-command=oql \"SELECT * FROM INSTANCEOF javax.sql.DataSource\"" -format=txt -unzip org.eclipse.mat.api:query
```

---

#### Guava Cache Analysis

```bash
./ParseHeapDump.sh dump.hprof "-command=oql \"SELECT c, c.@retainedHeapSize FROM INSTANCEOF com.google.common.cache.LocalCache c\"" -format=txt -unzip org.eclipse.mat.api:query
```

---

#### Objects by Package

```bash
./ParseHeapDump.sh dump.hprof "-command=oql \"SELECT * FROM INSTANCEOF com.example.* c\"" -format=txt -unzip org.eclipse.mat.api:query
```

---

#### Duplicate String Values (with COUNT)

```bash
./ParseHeapDump.sh dump.hprof "-command=oql \"SELECT toString(s) AS value, COUNT(*) AS cnt, SUM(s.@retainedHeapSize) AS totalRetained FROM java.lang.String s GROUP BY toString(s) HAVING COUNT(*) > 10\"" -format=txt -unzip org.eclipse.mat.api:query
```

---

#### Find Specific String Content

```bash
./ParseHeapDump.sh dump.hprof "-command=oql \"SELECT s, toString(s) FROM java.lang.String s WHERE toString(s) LIKE '.*password.*'\"" -format=txt -unzip org.eclipse.mat.api:query
```

---

## Output Formats and Parsing

### Output Format Options

| Option | Description | Best For |
|--------|-------------|----------|
| (default) | HTML in ZIP | Human viewing |
| `-format=txt` | Plain text | Parsing |
| `-format=csv` | CSV (limited support) | Spreadsheets |
| `-unzip` | Don't compress output | Immediate access |

---

### Parsing Text Output

The text format uses pipe (`|`) as delimiter with header row.

#### Python Parser Example

```python
#!/usr/bin/env python3
"""
MAT Output Parser for Jeffrey Integration
"""

import re
import json
from pathlib import Path
from typing import List, Dict, Any

def parse_mat_table(file_path: str) -> List[Dict[str, Any]]:
    """
    Parse MAT text table output into list of dictionaries.
    """
    with open(file_path, 'r') as f:
        lines = f.readlines()
    
    # Find header line (contains |)
    header_idx = None
    for i, line in enumerate(lines):
        if '|' in line and not line.strip().startswith('-'):
            header_idx = i
            break
    
    if header_idx is None:
        return []
    
    # Parse header
    header_line = lines[header_idx]
    headers = [h.strip() for h in header_line.split('|')]
    headers = [h for h in headers if h]  # Remove empty
    
    # Skip separator line
    data_start = header_idx + 2
    
    results = []
    for line in lines[data_start:]:
        if '|' not in line:
            continue
        if line.strip().startswith('-'):
            continue
        if line.strip() == '':
            continue
            
        values = [v.strip() for v in line.split('|')]
        values = [v for v in values if v != '']
        
        if len(values) != len(headers):
            continue
            
        row = {}
        for header, value in zip(headers, values):
            # Try to parse numbers
            row[header] = parse_value(value)
        
        results.append(row)
    
    return results


def parse_value(value: str) -> Any:
    """
    Parse string value to appropriate type.
    """
    # Remove commas from numbers
    clean = value.replace(',', '').strip()
    
    # Try integer
    try:
        return int(clean)
    except ValueError:
        pass
    
    # Try float
    try:
        return float(clean)
    except ValueError:
        pass
    
    # Try percentage
    if clean.endswith('%'):
        try:
            return float(clean[:-1]) / 100
        except ValueError:
            pass
    
    # Return as string
    return value.strip()


def parse_histogram(file_path: str) -> Dict[str, Any]:
    """
    Parse histogram output into structured format.
    """
    rows = parse_mat_table(file_path)
    
    return {
        "type": "histogram",
        "total_objects": sum(r.get("Objects", 0) for r in rows),
        "total_shallow_heap": sum(r.get("Shallow Heap", 0) for r in rows),
        "classes": rows
    }


def parse_thread_overview(file_path: str) -> Dict[str, Any]:
    """
    Parse thread overview output.
    """
    rows = parse_mat_table(file_path)
    
    return {
        "type": "thread_overview",
        "thread_count": len(rows),
        "total_retained": sum(r.get("Retained Heap", 0) for r in rows),
        "threads": rows
    }


# Main usage
if __name__ == "__main__":
    import sys
    
    if len(sys.argv) < 2:
        print("Usage: python mat_parser.py <mat_output_file>")
        sys.exit(1)
    
    result = parse_mat_table(sys.argv[1])
    print(json.dumps(result, indent=2))
```

---

#### Shell Script Parser

```bash
#!/bin/bash
# mat_to_json.sh - Convert MAT text output to JSON

INPUT_FILE="$1"
OUTPUT_FILE="${2:-output.json}"

if [ -z "$INPUT_FILE" ]; then
    echo "Usage: $0 <mat_output.txt> [output.json]"
    exit 1
fi

# Use awk to parse pipe-delimited table
awk -F'|' '
BEGIN {
    print "["
    first = 1
    header_found = 0
}

# Skip empty lines and separators
/^[[:space:]]*$/ || /^[-|]+$/ { next }

# Detect and parse header
!header_found && /\|/ {
    n = split($0, headers, "|")
    for (i = 1; i <= n; i++) {
        gsub(/^[[:space:]]+|[[:space:]]+$/, "", headers[i])
    }
    header_found = 1
    next
}

# Skip separator after header
header_found && /^[-|]+$/ { next }

# Parse data rows
header_found && /\|/ {
    if (!first) print ","
    first = 0
    
    printf "  {"
    n = split($0, values, "|")
    first_field = 1
    
    for (i = 1; i <= n; i++) {
        if (headers[i] == "") continue
        
        gsub(/^[[:space:]]+|[[:space:]]+$/, "", values[i])
        
        if (!first_field) printf ", "
        first_field = 0
        
        # Check if numeric
        if (values[i] ~ /^[0-9,]+$/) {
            gsub(/,/, "", values[i])
            printf "\"%s\": %s", headers[i], values[i]
        } else {
            gsub(/"/, "\\\"", values[i])
            printf "\"%s\": \"%s\"", headers[i], values[i]
        }
    }
    printf "}"
}

END {
    print "\n]"
}
' "$INPUT_FILE" > "$OUTPUT_FILE"

echo "Converted to $OUTPUT_FILE"
```

---

## Integration Scripts

### Complete Analysis Script

```bash
#!/bin/bash
# jeffrey_mat_analysis.sh - Run all MAT analyses for Jeffrey integration

set -e

MAT_HOME="${MAT_HOME:-/opt/mat}"
HEAP_DUMP="$1"
OUTPUT_DIR="${2:-./mat_output}"

if [ -z "$HEAP_DUMP" ]; then
    echo "Usage: $0 <heap_dump.hprof> [output_dir]"
    exit 1
fi

mkdir -p "$OUTPUT_DIR"

echo "=== Running MAT Analysis ==="
echo "Heap dump: $HEAP_DUMP"
echo "Output: $OUTPUT_DIR"

# Function to run query and convert to JSON
run_query() {
    local cmd="$1"
    local name="$2"
    
    echo "Running: $name"
    
    "$MAT_HOME/ParseHeapDump.sh" "$HEAP_DUMP" \
        -command="$cmd" \
        -format=txt \
        -unzip \
        org.eclipse.mat.api:query 2>/dev/null || true
    
    # Find output file and move to output dir
    local output_file=$(ls -t "${HEAP_DUMP%.*}"*Query*.txt 2>/dev/null | head -1)
    if [ -n "$output_file" ] && [ -f "$output_file" ]; then
        mv "$output_file" "$OUTPUT_DIR/${name}.txt"
        echo "  -> $OUTPUT_DIR/${name}.txt"
    fi
}

# Core Analysis
run_query "histogram" "histogram"
run_query "dominator_tree" "dominator_tree"
run_query "gc_roots" "gc_roots"
run_query "biggest_objects" "biggest_objects"
run_query "top_consumers" "top_consumers"

# Thread Analysis
run_query "thread_overview" "thread_overview"

# ClassLoader Analysis
run_query "class_loader_explorer" "classloader_explorer"
run_query "duplicate_classes" "duplicate_classes"

# Collection Analysis
run_query "collection_fill_ratio" "collection_fill_ratio"
run_query "collections_grouped_by_size" "collections_by_size"
run_query "array_fill_ratio" "array_fill_ratio"
run_query "arrays_grouped_by_size" "arrays_by_size"
run_query "map_collision_ratio" "map_collision_ratio"
run_query "primitive_arrays_with_a_constant_value" "constant_arrays"

# String Analysis  
run_query "waste_in_char_arrays" "char_array_waste"

# Reference Analysis
run_query "soft_reference_statistics" "soft_references"
run_query "weak_reference_statistics" "weak_references"
run_query "finalizer_overview" "finalizer_overview"

# Leak Detection
run_query "leak_hunter" "leak_suspects"
run_query "unreachable_objects_histogram" "unreachable_objects"

echo ""
echo "=== Analysis Complete ==="
echo "Output files in: $OUTPUT_DIR"
ls -la "$OUTPUT_DIR"
```

---

### OQL Query Runner

```bash
#!/bin/bash
# run_oql.sh - Run custom OQL query

MAT_HOME="${MAT_HOME:-/opt/mat}"
HEAP_DUMP="$1"
OQL_QUERY="$2"
OUTPUT_NAME="${3:-oql_result}"

if [ -z "$HEAP_DUMP" ] || [ -z "$OQL_QUERY" ]; then
    echo "Usage: $0 <heap_dump.hprof> <oql_query> [output_name]"
    echo ""
    echo "Example:"
    echo "  $0 dump.hprof \"SELECT s FROM java.lang.String s WHERE s.@retainedHeapSize > 10000\" large_strings"
    exit 1
fi

"$MAT_HOME/ParseHeapDump.sh" "$HEAP_DUMP" \
    "-command=oql \"$OQL_QUERY\"" \
    -format=txt \
    -unzip \
    org.eclipse.mat.api:query

# Find and rename output
OUTPUT_FILE=$(ls -t "${HEAP_DUMP%.*}"*Query*.txt 2>/dev/null | head -1)
if [ -n "$OUTPUT_FILE" ] && [ -f "$OUTPUT_FILE" ]; then
    mv "$OUTPUT_FILE" "${OUTPUT_NAME}.txt"
    echo "Result saved to: ${OUTPUT_NAME}.txt"
fi
```

---

### Docker Integration

```dockerfile
# Dockerfile for MAT batch processing
FROM eclipse-temurin:21-jdk

# Install MAT
RUN apt-get update && apt-get install -y wget unzip && \
    wget -q https://download.eclipse.org/mat/1.15.0/rcp/MemoryAnalyzer-1.15.0.20231206-linux.gtk.x86_64.zip && \
    unzip MemoryAnalyzer-1.15.0.20231206-linux.gtk.x86_64.zip -d /opt && \
    rm MemoryAnalyzer-1.15.0.20231206-linux.gtk.x86_64.zip && \
    chmod +x /opt/mat/ParseHeapDump.sh

# Configure MAT memory
RUN sed -i 's/-Xmx.*/-Xmx8g/' /opt/mat/MemoryAnalyzer.ini

ENV MAT_HOME=/opt/mat
ENV PATH="${MAT_HOME}:${PATH}"

WORKDIR /data

ENTRYPOINT ["/opt/mat/ParseHeapDump.sh"]
```

**Usage:**

```bash
# Build
docker build -t mat-analyzer .

# Run analysis
docker run -v /path/to/dumps:/data mat-analyzer /data/dump.hprof -command=histogram -format=txt -unzip org.eclipse.mat.api:query
```

---

### Jeffrey Integration Example (Java)

```java
package io.jeffrey.mat;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

/**
 * MAT Integration for Jeffrey Profiler
 */
public class MatAnalyzer {
    
    private final Path matHome;
    private final Path workDir;
    
    public MatAnalyzer(Path matHome, Path workDir) {
        this.matHome = matHome;
        this.workDir = workDir;
    }
    
    /**
     * Run MAT query and return parsed results
     */
    public List<Map<String, Object>> runQuery(Path heapDump, String command) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(
            matHome.resolve("ParseHeapDump.sh").toString(),
            heapDump.toString(),
            "-command=" + command,
            "-format=txt",
            "-unzip",
            "org.eclipse.mat.api:query"
        );
        
        pb.directory(workDir.toFile());
        pb.redirectErrorStream(true);
        
        Process process = pb.start();
        int exitCode = process.waitFor();
        
        if (exitCode != 0) {
            throw new RuntimeException("MAT failed with exit code: " + exitCode);
        }
        
        // Find output file
        Path outputFile = findLatestOutput(heapDump);
        if (outputFile == null) {
            return Collections.emptyList();
        }
        
        return parseTableOutput(outputFile);
    }
    
    /**
     * Run OQL query
     */
    public List<Map<String, Object>> runOql(Path heapDump, String oql) throws Exception {
        return runQuery(heapDump, "oql \"" + oql + "\"");
    }
    
    /**
     * Parse MAT table output
     */
    private List<Map<String, Object>> parseTableOutput(Path file) throws IOException {
        List<String> lines = Files.readAllLines(file);
        List<Map<String, Object>> results = new ArrayList<>();
        
        String[] headers = null;
        boolean headerFound = false;
        
        for (String line : lines) {
            if (line.trim().isEmpty() || line.matches("^[-|]+$")) {
                continue;
            }
            
            if (!headerFound && line.contains("|")) {
                headers = Arrays.stream(line.split("\\|"))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toArray(String[]::new);
                headerFound = true;
                continue;
            }
            
            if (headerFound && line.contains("|")) {
                String[] values = Arrays.stream(line.split("\\|"))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toArray(String[]::new);
                
                if (values.length == headers.length) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int i = 0; i < headers.length; i++) {
                        row.put(headers[i], parseValue(values[i]));
                    }
                    results.add(row);
                }
            }
        }
        
        return results;
    }
    
    /**
     * Parse value to appropriate type
     */
    private Object parseValue(String value) {
        String clean = value.replace(",", "").trim();
        
        // Try long
        try {
            return Long.parseLong(clean);
        } catch (NumberFormatException e) {}
        
        // Try double
        try {
            return Double.parseDouble(clean);
        } catch (NumberFormatException e) {}
        
        // Percentage
        if (clean.endsWith("%")) {
            try {
                return Double.parseDouble(clean.substring(0, clean.length() - 1)) / 100;
            } catch (NumberFormatException e) {}
        }
        
        return value.trim();
    }
    
    private Path findLatestOutput(Path heapDump) throws IOException {
        String baseName = heapDump.getFileName().toString().replaceAll("\\.hprof$", "");
        
        return Files.list(workDir)
            .filter(p -> p.getFileName().toString().startsWith(baseName))
            .filter(p -> p.getFileName().toString().endsWith(".txt"))
            .max(Comparator.comparingLong(p -> p.toFile().lastModified()))
            .orElse(null);
    }
}
```

---

## Summary Table

### Reports

| Report ID | Description | Output |
|-----------|-------------|--------|
| `org.eclipse.mat.api:suspects` | Leak suspects | HTML ZIP |
| `org.eclipse.mat.api:overview` | System overview | HTML ZIP |
| `org.eclipse.mat.api:top_components` | Top consumers by component | HTML ZIP |
| `org.eclipse.mat.api:compare` | Compare two dumps | HTML ZIP |
| `org.eclipse.mat.api:suspects2` | Leak suspects with baseline | HTML ZIP |

### Query Commands

| Command | Category | Description |
|---------|----------|-------------|
| `histogram` | Core | Class-level memory distribution |
| `dominator_tree` | Core | Memory ownership hierarchy |
| `gc_roots` | Core | GC root listing |
| `biggest_objects` | Core | Largest objects |
| `top_consumers` | Core | Top memory consumers |
| `thread_overview` | Threads | Thread memory analysis |
| `class_loader_explorer` | ClassLoaders | ClassLoader hierarchy |
| `duplicate_classes` | ClassLoaders | Classes in multiple loaders |
| `collection_fill_ratio` | Collections | Collection efficiency |
| `collections_grouped_by_size` | Collections | Collection size distribution |
| `array_fill_ratio` | Collections | Array efficiency |
| `arrays_grouped_by_size` | Collections | Array size distribution |
| `map_collision_ratio` | Collections | HashMap collision analysis |
| `primitive_arrays_with_a_constant_value` | Collections | Wasted constant arrays |
| `find_strings` | Strings | Search for strings |
| `group_by_value` | Strings | Duplicate strings |
| `waste_in_char_arrays` | Strings | String backing array waste |
| `soft_reference_statistics` | References | SoftReference analysis |
| `weak_reference_statistics` | References | WeakReference analysis |
| `phantom_reference_statistics` | References | PhantomReference analysis |
| `reference_leak` | References | Reference leak detection |
| `finalizer_overview` | References | Finalizer queue status |
| `leak_hunter` | Leaks | Automatic leak detection |
| `unreachable_objects_histogram` | Leaks | Garbage histogram |
| `system_properties` | Misc | JVM properties |

---

## Appendix: OQL Reference

### Select Syntax

```sql
SELECT [DISTINCT] <expression> [AS <alias>], ...
FROM [INSTANCEOF] <class_name> [<alias>]
[WHERE <condition>]
[GROUP BY <expression>]
[HAVING <condition>]
```

### Built-in Attributes

| Attribute | Description |
|-----------|-------------|
| `@objectId` | Internal MAT object ID |
| `@objectAddress` | Heap address |
| `@class` | Class object |
| `@clazz` | Class name |
| `@displayName` | Display name |
| `@shallowHeapSize` | Shallow heap size |
| `@retainedHeapSize` | Retained heap size |
| `@usedHeapSize` | Used heap size |
| `@GCRootInfo` | GC root information |

### Built-in Functions

| Function | Description |
|----------|-------------|
| `toString(object)` | Convert to string |
| `toHex(number)` | Convert to hex |
| `dominators(object)` | Get dominators |
| `dominatorof(object)` | Get immediate dominator |
| `outbounds(object)` | Outbound references |
| `inbounds(object)` | Inbound references |
| `classof(object)` | Get class |
| `eval(expression)` | Evaluate expression |

---

*Document generated for Jeffrey profiler integration. Last updated: 2025*
