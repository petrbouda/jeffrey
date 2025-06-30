# JFR Heap Memory and Garbage Collection Events Context

Based on analysis of SAP Machine JFR events documentation, here are the key events and patterns for heap memory and garbage collection monitoring:

## Code Cache Events (Memory-Related)
These events track native code memory usage:

### CodeCacheFull
- **Category**: "Java Virtual Machine / Code Cache"
- **Description**: A code heap is full, disabling the compiler
- **Key Fields**:
  - `codeBlobType`: Type of code blob
  - `startAddress`: Start address of the code cache
  - `commitedTopAddress`: Committed top address
  - `reservedTopAddress`: Reserved top address
  - `entryCount`: Number of entries
  - `methodCount`: Number of methods
  - `unallocatedCapacity`: Remaining capacity

### CodeCacheStatistics
- **Category**: "Java Virtual Machine / Code Cache"
- **Period**: Every chunk
- **Key Fields**: Same as CodeCacheFull

### CodeCacheConfiguration
- **Category**: "Java Virtual Machine / Code Cache"
- **Period**: End of every chunk
- **Key Fields**:
  - `initialSize`: Initial size
  - `reservedSize`: Reserved size
  - `nonNMethodSize`: Non-method size
  - `profiledSize`: Profiled code size
  - `nonProfiledSize`: Non-profiled code size
  - `expansionSize`: Expansion size

## Common GC Event Patterns (Standard JFR)
While not found in the SAP-specific documentation, standard JFR GC events typically include:

### Heap Events
- `jdk.GCHeapSummary`: Heap usage before/after GC
- `jdk.GCConfiguration`: GC algorithm configuration
- `jdk.YoungGenerationConfiguration`: Young generation settings
- `jdk.OldGenerationConfiguration`: Old generation settings

### GC Events
- `jdk.GarbageCollection`: Main GC event with duration and cause
- `jdk.YoungGarbageCollection`: Young generation GC
- `jdk.OldGarbageCollection`: Old generation GC
- `jdk.ParallelOldGarbageCollection`: Parallel old GC
- `jdk.G1GarbageCollection`: G1GC events

### Memory Pool Events
- `jdk.GCHeapMemoryUsage`: Memory usage per heap region
- `jdk.MetaspaceAllocationFailure`: Metaspace allocation issues
- `jdk.AllocationRequiringGC`: Allocations triggering GC

## Implementation Notes for Jeffrey
1. Focus on heap summary events for memory usage trends
2. Track GC frequency and duration for performance analysis
3. Monitor code cache events for compilation performance
4. Correlate allocation events with GC triggers
5. Use memory pool events for detailed heap region analysis

## Event Processing Strategy
- Aggregate heap usage over time for memory leak detection
- Calculate GC overhead percentage
- Track allocation rates and patterns
- Monitor memory pool utilization
- Identify GC pressure points and optimization opportunities