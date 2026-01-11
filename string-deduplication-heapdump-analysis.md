# String Deduplication Analysis with NetBeans Heap Dump Parser

> Research for Jeffrey: Analyzing deduplicated strings and building flamegraph visualizations
> **Target: Java 9+ only** (compact strings with `byte[]` and `coder` field)

---

## Overview

1. Parsing heap dumps with NetBeans Profiler library
2. Detecting already-deduplicated strings (shared `byte[]` arrays)
3. Finding deduplication opportunities (same content, different arrays)
4. Building flamegraphs showing where duplicate strings are held
5. Database schema for storing analysis results

---

## Maven Dependency

```xml
<dependency>
    <groupId>org.netbeans.modules</groupId>
    <artifactId>org-netbeans-lib-profiler</artifactId>
    <version>RELEASE220</version>
</dependency>
```

---

## Part 1: String Deduplication Analyzer

```java
import org.netbeans.lib.profiler.heap.*;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class StringDeduplicationAnalyzer {

    public record DeduplicationEntry(
        String content, 
        int count, 
        long arraySize, 
        long savings
    ) {}
    
    public record DeduplicationReport(
        long totalStrings,
        long totalStringShallowSize,
        long uniqueArrays,
        long sharedArrays,
        long totalSharedStrings,
        long memorySavedByDedup,
        long potentialSavings,
        List<DeduplicationEntry> alreadyDeduplicated,
        List<DeduplicationEntry> opportunities
    ) {}

    public static DeduplicationReport analyze(Heap heap) {
        JavaClass stringClass = heap.getJavaClassByName("java.lang.String");
        if (stringClass == null) {
            throw new IllegalStateException("String class not found in heap dump");
        }

        // Map: value array instance ID -> list of String instances sharing it
        Map<Long, List<Instance>> valueArrayToStrings = new HashMap<>();
        Map<Long, Long> valueArraySizes = new HashMap<>();
        
        long totalStrings = 0;
        long totalStringShallowSize = 0;

        for (Instance stringInstance : stringClass.getInstances()) {
            totalStrings++;
            totalStringShallowSize += stringInstance.getSize();
            
            Object valueField = stringInstance.getValueOfField("value");
            
            if (valueField instanceof Instance valueArray) {
                long valueArrayId = valueArray.getInstanceId();
                
                valueArrayToStrings
                    .computeIfAbsent(valueArrayId, k -> new ArrayList<>())
                    .add(stringInstance);
                
                valueArraySizes.putIfAbsent(valueArrayId, valueArray.getSize());
            }
        }

        long uniqueArrays = valueArrayToStrings.size();
        long sharedArrays = 0;
        long totalSharedStrings = 0;
        long memorySavedByDedup = 0;
        
        List<DeduplicationEntry> alreadyDeduplicated = new ArrayList<>();
        Map<String, List<Instance>> contentToStrings = new HashMap<>();

        for (var entry : valueArrayToStrings.entrySet()) {
            List<Instance> strings = entry.getValue();
            long arraySize = valueArraySizes.get(entry.getKey());
            
            if (strings.size() > 1) {
                // Array IS shared (deduplication active)
                sharedArrays++;
                totalSharedStrings += strings.size();
                memorySavedByDedup += (strings.size() - 1) * arraySize;
                
                alreadyDeduplicated.add(new DeduplicationEntry(
                    getStringValue(strings.get(0)),
                    strings.size(),
                    arraySize,
                    (strings.size() - 1) * arraySize
                ));
            } else {
                // Single use - check for same content with different arrays
                String content = getStringValue(strings.get(0));
                if (content != null) {
                    contentToStrings
                        .computeIfAbsent(content, k -> new ArrayList<>())
                        .add(strings.get(0));
                }
            }
        }

        // Find deduplication opportunities
        long potentialSavings = 0;
        List<DeduplicationEntry> opportunities = new ArrayList<>();
        
        for (var entry : contentToStrings.entrySet()) {
            if (entry.getValue().size() > 1) {
                Instance first = entry.getValue().get(0);
                Instance valueArray = (Instance) first.getValueOfField("value");
                long arraySize = valueArray != null ? valueArray.getSize() : 0;
                long savings = (entry.getValue().size() - 1) * arraySize;
                potentialSavings += savings;
                
                opportunities.add(new DeduplicationEntry(
                    entry.getKey(),
                    entry.getValue().size(),
                    arraySize,
                    savings
                ));
            }
        }

        alreadyDeduplicated.sort((a, b) -> Long.compare(b.savings(), a.savings()));
        opportunities.sort((a, b) -> Long.compare(b.savings(), a.savings()));

        return new DeduplicationReport(
            totalStrings,
            totalStringShallowSize,
            uniqueArrays,
            sharedArrays,
            totalSharedStrings,
            memorySavedByDedup,
            potentialSavings,
            alreadyDeduplicated,
            opportunities
        );
    }

    /**
     * Extract String value from heap dump instance.
     * Java 9+ compact strings: byte[] value + byte coder (0=LATIN1, 1=UTF16)
     */
    public static String getStringValue(Instance stringInstance) {
        try {
            Object valueField = stringInstance.getValueOfField("value");
            if (!(valueField instanceof PrimitiveArrayInstance array)) {
                return null;
            }
            
            List<String> values = array.getValues();
            byte[] bytes = new byte[values.size()];
            for (int i = 0; i < values.size(); i++) {
                bytes[i] = Byte.parseByte(values.get(i));
            }
            
            // coder: 0 = LATIN1, 1 = UTF16
            Object coder = stringInstance.getValueOfField("coder");
            boolean isLatin1 = coder == null || ((Number) coder).intValue() == 0;
            
            return isLatin1 
                ? new String(bytes, StandardCharsets.ISO_8859_1)
                : new String(bytes, StandardCharsets.UTF_16LE);
                
        } catch (Exception e) {
            return null;
        }
    }

    public static void printDashboard(DeduplicationReport report) {
        System.out.println("═══════════════════════════════════════════════════════════════");
        System.out.println("              STRING DEDUPLICATION DASHBOARD                    ");
        System.out.println("═══════════════════════════════════════════════════════════════");
        System.out.printf("Total String instances:        %,d%n", report.totalStrings());
        System.out.printf("Total String shallow size:     %,d bytes (%.2f MB)%n", 
            report.totalStringShallowSize(), 
            report.totalStringShallowSize() / 1024.0 / 1024.0);
        System.out.printf("Unique value arrays:           %,d%n", report.uniqueArrays());
        System.out.println();
        System.out.println("─── CURRENT DEDUPLICATION STATUS ───");
        System.out.printf("Shared arrays (dedup active):  %,d%n", report.sharedArrays());
        System.out.printf("Strings sharing arrays:        %,d%n", report.totalSharedStrings());
        System.out.printf("Memory saved by dedup:         %,d bytes (%.2f MB)%n", 
            report.memorySavedByDedup(), 
            report.memorySavedByDedup() / 1024.0 / 1024.0);
        System.out.println();
        System.out.println("─── DEDUPLICATION OPPORTUNITIES ───");
        System.out.printf("Potential additional savings:  %,d bytes (%.2f MB)%n", 
            report.potentialSavings(), 
            report.potentialSavings() / 1024.0 / 1024.0);
        
        System.out.println();
        System.out.println("─── TOP 10 ALREADY DEDUPLICATED ───");
        report.alreadyDeduplicated().stream().limit(10).forEach(e -> 
            System.out.printf("  [%,d refs, saved %,d bytes] \"%s\"%n", 
                e.count(), e.savings(), truncate(e.content(), 50)));
        
        System.out.println();
        System.out.println("─── TOP 10 DEDUPLICATION OPPORTUNITIES ───");
        report.opportunities().stream().limit(10).forEach(e -> 
            System.out.printf("  [%,d copies, could save %,d bytes] \"%s\"%n", 
                e.count(), e.savings(), truncate(e.content(), 50)));
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return "<null>";
        if (s.length() <= maxLen) return s;
        return s.substring(0, maxLen - 3) + "...";
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: java StringDeduplicationAnalyzer <heapdump.hprof>");
            System.exit(1);
        }
        Heap heap = HeapFactory.createHeap(new File(args[0]));
        DeduplicationReport report = analyze(heap);
        printDashboard(report);
    }
}
```

---

## Part 2: Reference-Path Flamegraph Builder

```java
import org.netbeans.lib.profiler.heap.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class StringReferenceFlameGraphBuilder {

    public static class RefPathNode {
        private final String name;
        private long selfDuplicateBytes;
        private final Map<String, RefPathNode> children = new HashMap<>();
        
        public RefPathNode(String name) {
            this.name = name;
        }
        
        public void addBytes(long bytes) {
            this.selfDuplicateBytes += bytes;
        }
        
        public String getName() { return name; }
        public long getSelfDuplicateBytes() { return selfDuplicateBytes; }
        public Map<String, RefPathNode> getChildren() { return children; }
    }

    public static RefPathNode buildReferenceFlameGraph(
            Heap heap, 
            Map<String, List<Instance>> duplicateGroups) {
        
        RefPathNode root = new RefPathNode("root");
        
        for (var entry : duplicateGroups.entrySet()) {
            List<Instance> duplicates = entry.getValue();
            if (duplicates.size() <= 1) continue;
            
            Instance first = duplicates.get(0);
            Instance valueArray = (Instance) first.getValueOfField("value");
            long wastedBytes = valueArray != null 
                ? (duplicates.size() - 1) * valueArray.getSize() 
                : 0;
            
            for (Instance dup : duplicates.subList(1, duplicates.size())) {
                List<String> refPath = traceToRoot(dup);
                addPathToTree(root, refPath, wastedBytes / (duplicates.size() - 1));
            }
        }
        
        return root;
    }

    private static List<String> traceToRoot(Instance instance) {
        List<String> path = new ArrayList<>();
        Instance current = instance;
        Set<Long> visited = new HashSet<>();
        int maxDepth = 50;
        
        while (current != null && visited.add(current.getInstanceId()) && path.size() < maxDepth) {
            current = findReferrer(current, path);
        }
        
        Collections.reverse(path);
        return path;
    }

    @SuppressWarnings("unchecked")
    private static Instance findReferrer(Instance instance, List<String> pathAccumulator) {
        List<Value> references = (List<Value>) instance.getReferences();
        
        for (Value ref : references) {
            if (ref instanceof ObjectFieldValue ofv) {
                Instance referrer = ofv.getDefiningInstance();
                Field field = ofv.getField();
                
                pathAccumulator.add(referrer.getJavaClass().getName() + "." + field.getName());
                
                if (referrer.isGCRoot()) {
                    GCRoot gcRoot = referrer.getNearestGCRootPointer();
                    String kind = gcRoot != null ? gcRoot.getKind() : "unknown";
                    pathAccumulator.add("[GC Root: " + kind + "]");
                    return null;
                }
                
                return referrer;
            } else if (ref instanceof ArrayItemValue aiv) {
                Instance referrer = aiv.getDefiningInstance();
                pathAccumulator.add(referrer.getJavaClass().getName() + "[]");
                
                if (referrer.isGCRoot()) {
                    return null;
                }
                return referrer;
            }
        }
        return null;
    }

    private static void addPathToTree(RefPathNode root, List<String> path, long bytes) {
        RefPathNode current = root;
        for (String segment : path) {
            current = current.getChildren().computeIfAbsent(segment, RefPathNode::new);
        }
        current.addBytes(bytes);
    }

    /**
     * Convert tree to collapsed stack format for flamegraph.pl
     */
    public static List<String> toCollapsedStacks(RefPathNode root) {
        List<String> stacks = new ArrayList<>();
        collectStacks(root, new ArrayDeque<>(), stacks);
        return stacks;
    }

    private static void collectStacks(RefPathNode node, Deque<String> path, List<String> result) {
        if (node.getSelfDuplicateBytes() > 0 && !path.isEmpty()) {
            result.add(String.join(";", path) + " " + node.getSelfDuplicateBytes());
        }
        
        for (var child : node.getChildren().entrySet()) {
            path.addLast(child.getKey());
            collectStacks(child.getValue(), path, result);
            path.removeLast();
        }
    }
    
    /**
     * Find all duplicate string groups in the heap.
     */
    public static Map<String, List<Instance>> findDuplicateStrings(Heap heap) {
        JavaClass stringClass = heap.getJavaClassByName("java.lang.String");
        Map<String, List<Instance>> contentToStrings = new HashMap<>();
        
        for (Instance stringInstance : stringClass.getInstances()) {
            String content = getStringValue(stringInstance);
            if (content != null) {
                contentToStrings
                    .computeIfAbsent(content, k -> new ArrayList<>())
                    .add(stringInstance);
            }
        }
        
        contentToStrings.entrySet().removeIf(e -> e.getValue().size() <= 1);
        return contentToStrings;
    }
    
    public static String getStringValue(Instance stringInstance) {
        try {
            Object valueField = stringInstance.getValueOfField("value");
            if (!(valueField instanceof PrimitiveArrayInstance array)) {
                return null;
            }
            
            List<String> values = array.getValues();
            byte[] bytes = new byte[values.size()];
            for (int i = 0; i < values.size(); i++) {
                bytes[i] = Byte.parseByte(values.get(i));
            }
            
            Object coder = stringInstance.getValueOfField("coder");
            boolean isLatin1 = coder == null || ((Number) coder).intValue() == 0;
            
            return isLatin1 
                ? new String(bytes, StandardCharsets.ISO_8859_1)
                : new String(bytes, StandardCharsets.UTF_16LE);
                
        } catch (Exception e) {
            return null;
        }
    }
}
```

---

## Part 3: JFR Allocation-Site Analyzer

```java
import jdk.jfr.consumer.*;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public class JfrStringAllocationAnalyzer {

    public record AllocationSite(
        String stackTrace, 
        long totalAllocated, 
        int count
    ) {}
    
    public static Map<String, AllocationSite> analyzeStringAllocations(Path jfrFile) 
            throws Exception {
        
        Map<String, AllocationSite> sitesByStack = new HashMap<>();
        
        try (RecordingFile rf = new RecordingFile(jfrFile)) {
            while (rf.hasMoreEvents()) {
                RecordedEvent event = rf.readEvent();
                
                if (isStringAllocationEvent(event)) {
                    RecordedStackTrace stack = event.getStackTrace();
                    if (stack == null) continue;
                    
                    String stackKey = formatStackTrace(stack);
                    long size = getAllocationSize(event);
                    
                    sitesByStack.merge(stackKey,
                        new AllocationSite(stackKey, size, 1),
                        (a, b) -> new AllocationSite(
                            a.stackTrace, 
                            a.totalAllocated + b.totalAllocated, 
                            a.count + b.count));
                }
            }
        }
        
        return sitesByStack;
    }

    private static boolean isStringAllocationEvent(RecordedEvent event) {
        String name = event.getEventType().getName();
        if (!name.equals("jdk.ObjectAllocationSample") &&
            !name.equals("jdk.ObjectAllocationInNewTLAB") &&
            !name.equals("jdk.ObjectAllocationOutsideTLAB")) {
            return false;
        }
        
        RecordedClass objectClass = event.getValue("objectClass");
        return objectClass != null && "java.lang.String".equals(objectClass.getName());
    }

    private static long getAllocationSize(RecordedEvent event) {
        if (event.hasField("allocationSize")) {
            return event.getLong("allocationSize");
        }
        if (event.hasField("tlabSize")) {
            return event.getLong("tlabSize");
        }
        return 0;
    }

    private static String formatStackTrace(RecordedStackTrace stack) {
        return stack.getFrames().stream()
            .limit(20)
            .map(f -> f.getMethod().getType().getName() + "." + f.getMethod().getName())
            .collect(Collectors.joining(";"));
    }
    
    public static List<String> toCollapsedStacks(Map<String, AllocationSite> sites) {
        return sites.values().stream()
            .map(site -> site.stackTrace + " " + site.totalAllocated)
            .toList();
    }
}
```

---

## Part 4: Combined Flamegraph Generator

```java
import org.netbeans.lib.profiler.heap.*;
import java.nio.file.Path;
import java.util.*;

public class StringDuplicationFlameGraphGenerator {

    public enum FlameGraphType {
        REFERENCE_PATH,   // Where strings are held (heap only)
        ALLOCATION_SITE,  // Where strings are created (JFR only)  
        COMBINED
    }

    public static List<String> generateCollapsedStacks(
            Path heapDump, 
            Path jfrRecording,
            FlameGraphType type) throws Exception {
        
        Heap heap = HeapFactory.createHeap(heapDump.toFile());
        Map<String, List<Instance>> duplicates = 
            StringReferenceFlameGraphBuilder.findDuplicateStrings(heap);
        
        return switch (type) {
            case REFERENCE_PATH -> {
                var root = StringReferenceFlameGraphBuilder.buildReferenceFlameGraph(heap, duplicates);
                yield StringReferenceFlameGraphBuilder.toCollapsedStacks(root);
            }
            case ALLOCATION_SITE -> {
                var sites = JfrStringAllocationAnalyzer.analyzeStringAllocations(jfrRecording);
                yield JfrStringAllocationAnalyzer.toCollapsedStacks(sites);
            }
            case COMBINED -> {
                List<String> stacks = new ArrayList<>();
                
                var root = StringReferenceFlameGraphBuilder.buildReferenceFlameGraph(heap, duplicates);
                stacks.addAll(StringReferenceFlameGraphBuilder.toCollapsedStacks(root));
                
                if (jfrRecording != null) {
                    var sites = JfrStringAllocationAnalyzer.analyzeStringAllocations(jfrRecording);
                    stacks.addAll(JfrStringAllocationAnalyzer.toCollapsedStacks(sites));
                }
                
                yield stacks;
            }
        };
    }
}
```

---

## Part 5: DuckDB Schema

```sql
CREATE TABLE string_duplications (
    recording_id UUID,
    analysis_timestamp TIMESTAMP,
    string_hash UINT64,
    string_preview VARCHAR(100),
    duplicate_count INTEGER,
    wasted_bytes BIGINT,
    allocation_stack VARCHAR[],
    reference_paths VARCHAR[],
    top_holders VARCHAR[]
);

CREATE VIEW flamegraph_data AS
SELECT 
    recording_id,
    array_to_string(allocation_stack, ';') as stack,
    SUM(wasted_bytes) as total_waste,
    COUNT(*) as unique_strings,
    SUM(duplicate_count) as total_duplicates
FROM string_duplications
GROUP BY recording_id, allocation_stack;

-- Collapsed stack output for flamegraph.pl
SELECT stack || ' ' || total_waste as collapsed_line
FROM flamegraph_data
WHERE recording_id = ?
ORDER BY total_waste DESC;
```

---

## Java 9+ String Internals

```
┌─────────────────────────────────────┐
│           java.lang.String          │
├─────────────────────────────────────┤
│  byte[] value   ───────────────────────► [0x48, 0x65, 0x6c, 0x6c, 0x6f]
│  byte coder     = 0 (LATIN1)        │    "Hello" in ISO-8859-1
│  int hash       = 0 (lazy computed) │
└─────────────────────────────────────┘

┌─────────────────────────────────────┐
│           java.lang.String          │
├─────────────────────────────────────┤
│  byte[] value   ───────────────────────► [0x1F, 0x60, 0x3E, 0x8A, ...]
│  byte coder     = 1 (UTF16)         │    UTF-16LE encoded
│  int hash       = 0                 │
└─────────────────────────────────────┘
```

**Deduplication Detection:**
- **Deduplicated**: Multiple `String` instances → same `byte[]` instance ID
- **Opportunity**: Multiple `String` instances → different `byte[]` with identical content

**G1 String Deduplication:**
- Enable with `-XX:+UseStringDeduplication` (G1 GC only)
- Delay: `-XX:StringDeduplicationAgeThreshold=3` (default)
- Only processes strings that survived to old generation
