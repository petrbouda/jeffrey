# HPROF Heap Dump Sanitizer — Implementation Prompt

## Project Context

I'm building **Jeffrey**, an open-source Java Flight Recorder (JFR) analysis tool for profiling and flamegraph generation (https://github.com/petrbouda/jeffrey). Jeffrey also supports heap dump analysis using the **NetBeans HPROF parser** (`org.netbeans.lib.profiler.heap`).

The problem: when a Java application doesn't terminate gracefully (OOMKilled in Kubernetes, SIGKILL, JVM crash), the generated HPROF heap dump file is structurally invalid. The NetBeans parser throws `IOException: Heap dump is broken` and refuses to parse it. However, **Eclipse MAT** can open these same files because it has configurable strictness modes and multi-pass error recovery.

I need to implement an **HPROF repair/sanitizer preprocessor** that fixes known corruption patterns in the HPROF binary file BEFORE passing it to the NetBeans parser. This approach avoids switching parsers or forking the NetBeans library.

## HPROF Binary Format Specification

### File Header
```
[version string]    - null-terminated ASCII, e.g. "JAVA PROFILE 1.0.2\0"
[identifier size]   - u4 (4 bytes) — value is 4 or 8 (matches JVM pointer width)
[timestamp high]    - u4 (high 32 bits of epoch millis)
[timestamp low]     - u4 (low 32 bits of epoch millis)
```

### Top-Level Record Structure
After the header, the file is a flat sequence of records. Each record has:
```
[tag]               - u1 (1 byte)  — record type identifier
[timestamp delta]   - u4 (4 bytes) — microseconds since header timestamp
[body length]       - u4 (4 bytes) — number of bytes in the body that follows
[body]              - exactly [body length] bytes
```
Total record header size: **9 bytes**.

### Top-Level Record Tags
```
Tag   Name                        Description
───────────────────────────────────────────────────────────────
0x01  HPROF_UTF8                  UTF-8 string definition (ID + bytes)
0x02  HPROF_LOAD_CLASS            Class load event (serial, obj ID, stack serial, name ID)
0x03  HPROF_UNLOAD_CLASS          Class unload event
0x04  HPROF_FRAME                 Stack frame (ID, method name ID, signature ID, source ID, serial, line)
0x05  HPROF_TRACE                 Stack trace (serial, thread serial, nframes, frame IDs[])
0x06  HPROF_ALLOC_SITES           Allocation sites
0x07  HPROF_HEAP_SUMMARY          Heap summary
0x0A  HPROF_START_THREAD          Thread start
0x0B  HPROF_END_THREAD            Thread end
0x0C  HPROF_HEAP_DUMP             Complete heap dump in one record (old format, rare)
0x0D  HPROF_CPU_SAMPLES           CPU samples
0x0E  HPROF_CONTROL_SETTINGS      Control settings
0x1C  HPROF_HEAP_DUMP_SEGMENT     Heap dump segment (chunked format, standard)
0x2C  HPROF_HEAP_DUMP_END         End of heap dump segments (empty body, length=0)
```

### Heap Dump Segment Sub-Records (inside 0x1C and 0x0C body)
These sub-records are packed sequentially inside the body of `HEAP_DUMP_SEGMENT` records. They do NOT have the 9-byte top-level record header — they have their own compact layout with just a 1-byte sub-tag followed by type-specific data.

```
Sub-tag  Name                     Layout (after sub-tag byte)
─────────────────────────────────────────────────────────────────────
0xFF     GC_ROOT_UNKNOWN          [object ID]
0x01     GC_ROOT_JNI_GLOBAL       [object ID] [JNI global ref ID]
0x02     GC_ROOT_JNI_LOCAL        [object ID] [thread serial:u4] [frame#:u4]
0x03     GC_ROOT_JAVA_FRAME       [object ID] [thread serial:u4] [frame#:u4]
0x04     GC_ROOT_NATIVE_STACK     [object ID] [thread serial:u4]
0x05     GC_ROOT_STICKY_CLASS     [object ID]
0x06     GC_ROOT_THREAD_BLOCK     [object ID] [thread serial:u4]
0x07     GC_ROOT_MONITOR_USED     [object ID]
0x08     GC_ROOT_THREAD_OBJ       [object ID] [thread serial:u4] [stack trace serial:u4]

0x20     GC_CLASS_DUMP            [class obj ID] [stack trace serial:u4] [super class obj ID]
                                  [classloader obj ID] [signers obj ID] [protection domain obj ID]
                                  [reserved ID] [reserved ID]
                                  [instance size:u4]
                                  [constant pool count:u2]
                                    for each: [cp index:u2] [type:u1] [value:type-dependent]
                                  [static field count:u2]
                                    for each: [name ID] [type:u1] [value:type-dependent]
                                  [instance field count:u2]
                                    for each: [name ID] [type:u1]

0x21     GC_INSTANCE_DUMP         [object ID] [stack trace serial:u4] [class obj ID]
                                  [bytes following:u4] [instance field values...]

0x22     GC_OBJ_ARRAY_DUMP        [array obj ID] [stack trace serial:u4]
                                  [number of elements:u4] [array class obj ID]
                                  [element IDs...] (each is idSize bytes)

0x23     GC_PRIM_ARRAY_DUMP       [array obj ID] [stack trace serial:u4]
                                  [number of elements:u4] [element type:u1]
                                  [elements...] (each is typeSize bytes)
```

### Java Type Sizes (for constant pool values, static field values, primitive arrays)
```
Type  Name       Size
──────────────────────
2     object     idSize bytes (4 or 8)
4     boolean    1 byte
5     char       2 bytes
6     float      4 bytes
7     double     8 bytes
8     byte       1 byte
9     short      2 bytes
10    int        4 bytes
11    long       8 bytes
```

## Known Corruption Patterns from Ungraceful JVM Shutdown

### Pattern 1: Zero-Length HEAP_DUMP_SEGMENT (MOST COMMON)
**Cause**: HotSpot's `DumpWriter` writes segment headers with `length=0` as a placeholder, then seeks back to fill in the actual length after writing all sub-records. SIGKILL prevents the seek-back.
**Symptom**: Record with tag `0x1C`, valid timestamp, but `body length = 0`.
**NetBeans error**: `IOException: Heap dump is broken. Tag 0x1c at offset X has zero length.`
**Fix**: Scan forward through the sub-records inside the segment body to determine the actual length, then correct the length field.

### Pattern 2: Truncated Last Record
**Cause**: The JVM's 8 MB internal write buffer wasn't fully flushed before SIGKILL.
**Symptom**: A record declares `body length = N` but the file only has `< N` bytes remaining.
**Fix**: Discard the incomplete record by truncating the output at the start of this record.

### Pattern 3: Missing HEAP_DUMP_END
**Cause**: The `HEAP_DUMP_END` record (tag `0x2C`) is always the last thing written. In crash scenarios, it's almost never present.
**Symptom**: File ends without a `0x2C` record.
**Fix**: Append a `HEAP_DUMP_END` record: `[0x2C] [0x00 0x00 0x00 0x00] [0x00 0x00 0x00 0x00]` (9 bytes total).

### Pattern 4: Truncated Sub-Record within a Segment
**Cause**: SIGKILL during serialization of a large object (e.g., a multi-MB byte array).
**Symptom**: A `GC_PRIM_ARRAY_DUMP` declares N elements but the segment/file ends before all elements are written.
**Fix**: During sub-record scanning, detect when remaining bytes are insufficient for the declared content. Truncate the segment at the last completely valid sub-record.

### Pattern 5: Integer Overflow in Record Length (>2GB segments)
**Cause**: Heap dump segments can exceed 2GB, but the length field is a signed 32-bit integer.
**Symptom**: Negative length value in the record header.
**Fix**: Eclipse MAT's Bug #404679 workaround — detect negative length and recalculate by scanning sub-records.

## Implementation Requirements

### Module Location
- Create the sanitizer in the Jeffrey project codebase
- Use Java (the project uses Java 21+, can use modern Java features)
- The sanitizer should be a standalone utility class that can be called before invoking the NetBeans parser

### Class Design
```java
/**
 * Repairs/sanitizes HPROF heap dump files that may be structurally invalid
 * due to ungraceful JVM termination (OOMKill, SIGKILL, crash).
 *
 * Known corruption patterns handled:
 * 1. Zero-length HEAP_DUMP_SEGMENT records (deferred length write-back)
 * 2. Truncated last record (unflushed write buffer)
 * 3. Missing HEAP_DUMP_END terminator
 * 4. Truncated sub-records within segments
 * 5. Negative/overflowed record lengths (>2GB segments)
 */
public class HprofSanitizer {

    /**
     * Checks if the HPROF file needs sanitization.
     * Quick check: validates basic structure without full scan.
     * At minimum, checks if HEAP_DUMP_END is present.
     */
    public static boolean needsSanitization(Path hprofFile) throws IOException;

    /**
     * Sanitizes the HPROF file and writes a repaired version.
     * If the file is already valid, copies it as-is (or returns the original path).
     *
     * @param input  path to the potentially corrupt HPROF file
     * @param output path for the repaired HPROF file
     * @return result with metadata about what was fixed
     */
    public static SanitizeResult sanitize(Path input, Path output) throws IOException;
}
```

### Result Class
```java
public record SanitizeResult(
    boolean wasModified,              // true if any fixes were applied
    boolean hadZeroLengthSegments,    // Pattern 1
    boolean wasTruncated,             // Pattern 2
    boolean hadMissingEndMarker,      // Pattern 3
    boolean hadTruncatedSubRecords,   // Pattern 4
    boolean hadOverflowedLengths,     // Pattern 5
    int zeroLengthSegmentsFixed,      // count of Pattern 1 fixes
    long totalRecordsProcessed,       // total top-level records read
    long totalBytesRead,              // bytes read from input
    long totalBytesWritten,           // bytes written to output
    long estimatedObjectsRecovered,   // sub-records in fixed segments
    String summaryMessage             // human-readable summary for UI
) {}
```

### Core Algorithm

```
1. Read and validate HPROF header
   - Parse version string (must be "JAVA PROFILE 1.0.1" or "1.0.2")
   - Read identifier size (must be 4 or 8)
   - Read timestamp
   - Copy header to output

2. Sequential record scan with repair:
   for each top-level record:
     a. Read 9-byte record header (tag, timestamp, length)

     b. If cannot read full 9 bytes → truncated, stop

     c. If tag == 0x1C (HEAP_DUMP_SEGMENT):
        - If length == 0 OR length < 0 (overflow):
          → Enter sub-record scanning mode
          → Walk sub-records one by one, computing each sub-record's size
          → Stop when: invalid sub-tag, insufficient bytes, or EOF
          → The computed total is the real segment length
          → Write corrected record header + valid sub-record data to output

        - If length > 0 and length <= remaining file:
          → Validate by scanning sub-records within the declared length
          → If sub-records are consistent, copy as-is
          → If sub-records show internal truncation, trim to last valid sub-record

     d. If tag == 0x2C (HEAP_DUMP_END):
        → Copy to output, mark end found

     e. For any other valid tag (0x01-0x0E):
        - If remaining file < declared length → truncated, stop
        - Copy record as-is to output

     f. If tag is invalid (not in known set):
        → We've hit garbage data, stop

3. Post-processing:
   - If no HEAP_DUMP_END was encountered, append one
   - Build and return SanitizeResult
```

### Sub-Record Size Calculation
This is the most critical helper function. Given a sub-tag byte and the identifier size, it must compute the exact byte size of the sub-record (excluding the sub-tag byte itself).

```
function computeSubRecordSize(subTag, idSize, dataBuffer, offset, remaining):
  switch subTag:
    0xFF: return idSize                                          // ROOT_UNKNOWN
    0x01: return idSize + idSize                                 // ROOT_JNI_GLOBAL
    0x02: return idSize + 4 + 4                                  // ROOT_JNI_LOCAL
    0x03: return idSize + 4 + 4                                  // ROOT_JAVA_FRAME
    0x04: return idSize + 4                                      // ROOT_NATIVE_STACK
    0x05: return idSize                                          // ROOT_STICKY_CLASS
    0x06: return idSize + 4                                      // ROOT_THREAD_BLOCK
    0x07: return idSize                                          // ROOT_MONITOR_USED
    0x08: return idSize + 4 + 4                                  // ROOT_THREAD_OBJ

    0x20:                                                        // CLASS_DUMP
      size = 7*idSize + 4 + 4                                   // fixed fields + instance size + stack serial
      // constant pool
      cpCount = readU2(buffer, offset + 7*idSize + 4 + 4)       // constant pool count
      pos = 7*idSize + 4 + 4 + 2
      for i in 0..cpCount:
        pos += 2                                                 // cp index
        type = readU1(buffer, offset + pos)
        pos += 1 + typeSize(type, idSize)                        // type byte + value
      // static fields
      sfCount = readU2(buffer, offset + pos)
      pos += 2
      for i in 0..sfCount:
        pos += idSize                                            // field name ID
        type = readU1(buffer, offset + pos)
        pos += 1 + typeSize(type, idSize)                        // type byte + value
      // instance fields
      ifCount = readU2(buffer, offset + pos)
      pos += 2
      for i in 0..ifCount:
        pos += idSize + 1                                        // field name ID + type byte
      return pos

    0x21:                                                        // INSTANCE_DUMP
      bytesFollowing = readU4(buffer, offset + idSize + 4 + idSize)
      return idSize + 4 + idSize + 4 + bytesFollowing

    0x22:                                                        // OBJ_ARRAY_DUMP
      numElements = readU4(buffer, offset + idSize + 4)
      return idSize + 4 + 4 + idSize + (numElements * idSize)

    0x23:                                                        // PRIM_ARRAY_DUMP
      numElements = readU4(buffer, offset + idSize + 4)
      elemType = readU1(buffer, offset + idSize + 4 + 4)
      return idSize + 4 + 4 + 1 + (numElements * typeSize(elemType, idSize))

    default: return -1                                           // unknown sub-tag, stop scanning
```

### Performance Requirements
- The sanitizer must handle **large heap dumps** (10+ GB) without loading them into memory
- Use **buffered streaming I/O** — read and write in chunks (e.g., 64 KB or larger buffers)
- For zero-length segment repair, you may need to buffer up to one full segment (~256 MB max per segment in practice) to compute the length before writing the corrected header
- Consider using `FileChannel` and `ByteBuffer` for efficient I/O
- The quick `needsSanitization()` check should only read the last 9 bytes + scan backward, or do a fast forward scan of record tags

### Edge Cases to Handle
1. **File smaller than header** — return error, not a valid HPROF
2. **Empty file** — return error
3. **Valid file with no corruption** — return quickly with `wasModified=false`
4. **Multiple zero-length segments** — fix each one
5. **Zero-length segment followed by valid segments** — fix the zero-length one, continue with valid ones
6. **Segment with negative length** — treat as overflow, scan sub-records like zero-length
7. **File that is all header + metadata but no heap segments** — valid but unusual, pass through
8. **Compressed HPROF** (gzip, JDK 15+ `jcmd -gz=1`) — detect gzip magic bytes `0x1F 0x8B` and decompress first or inform user

### Testing Strategy
- Create test HPROF files by generating valid dumps and then artificially corrupting them:
  - Truncate at various points
  - Zero out segment length fields
  - Remove the last 9 bytes (HEAP_DUMP_END)
  - Truncate mid-sub-record
- Verify that the NetBeans parser successfully opens the sanitized output
- Verify that valid HPROF files pass through unchanged
- Compare sanitized file analysis results with Eclipse MAT's analysis of the same corrupt file

### Dependencies
- No external dependencies beyond the JDK standard library
- Use `java.nio` for file I/O (`FileChannel`, `ByteBuffer`, `MappedByteBuffer` for reading)
- The sanitizer should be usable independently of the rest of Jeffrey

## Additional Notes

- The HPROF version string in modern JVMs is always `"JAVA PROFILE 1.0.2"` (19 chars + null = 20 bytes). Version `1.0.1` used 4-byte identifiers only; `1.0.2` added 8-byte identifier support.
- The identifier size from the header is critical — it determines the byte width of ALL ID fields throughout the entire file (object IDs, class IDs, string IDs, etc.)
- HotSpot's `heapDumper.cpp` writes segments up to `HeapDumpSegmentSize` (default ~1 GB) before starting a new segment. In practice, most dumps have multiple segments.
- The `typeSize` function maps Java basic type tags to byte sizes: object→idSize, boolean→1, char→2, float→4, double→8, byte→1, short→2, int→4, long→8.

Please implement this as production-quality Java code with proper error handling, logging, and comprehensive JavaDoc.
