# NetBeans OQL Library - Use Cases and Implementation Guide

**Artifact:** `org-netbeans-modules-profiler-oql`  
**Version:** RELEASE250  
**License:** Apache 2.0

---

## Table of Contents

1. [Overview](#1-overview)
2. [Maven Dependency](#2-maven-dependency)
3. [Core API Classes](#3-core-api-classes)
4. [Use Cases](#4-use-cases)
   - 4.1 [Memory Leak Detection](#41-memory-leak-detection)
   - 4.2 [Object Instance Analysis](#42-object-instance-analysis)
   - 4.3 [Class Histogram Generation](#43-class-histogram-generation)
   - 4.4 [GC Root Analysis](#44-gc-root-analysis)
   - 4.5 [Thread Analysis](#45-thread-analysis)
   - 4.6 [String Analysis](#46-string-analysis)
   - 4.7 [Collection Analysis](#47-collection-analysis)
   - 4.8 [ClassLoader Leak Detection](#48-classloader-leak-detection)
   - 4.9 [Session/Cache Analysis](#49-sessioncache-analysis)
   - 4.10 [Custom Domain Object Analysis](#410-custom-domain-object-analysis)
   - 4.11 [Duplicate Object Detection](#411-duplicate-object-detection)
   - 4.12 [Reference Chain Analysis](#412-reference-chain-analysis)
   - 4.13 [Heap Comparison](#413-heap-comparison)
   - 4.14 [Batch Processing Multiple Dumps](#414-batch-processing-multiple-dumps)
   - 4.15 [Integration with Monitoring Systems](#415-integration-with-monitoring-systems)
5. [OQL Syntax Reference](#5-oql-syntax-reference)
6. [Built-in Functions](#6-built-in-functions)
7. [JavaScript Integration](#7-javascript-integration)
8. [Performance Considerations](#8-performance-considerations)
9. [Complete Example Application](#9-complete-example-application)

---

## 1. Overview

The NetBeans OQL (Object Query Language) library provides a powerful mechanism for querying Java heap dumps programmatically. It combines SQL-like syntax with JavaScript expressions to enable flexible heap analysis without requiring GUI tools like VisualVM or Eclipse MAT.

**Key Benefits:**

- Headless/batch processing of heap dumps
- Automation of memory analysis in CI/CD pipelines
- Custom tooling for application-specific memory patterns
- Integration into existing monitoring and alerting systems
- No dependency on GUI or IDE components

---

## 2. Maven Dependency

```xml
<dependency>
    <groupId>org.netbeans.modules</groupId>
    <artifactId>org-netbeans-modules-profiler-oql</artifactId>
    <version>RELEASE250</version>
</dependency>
```

This transitively includes:
- `org-netbeans-lib-profiler` - Core heap parsing (HeapFactory, Heap, Instance, JavaClass)
- `org-netbeans-api-scripting` - JavaScript engine integration

---

## 3. Core API Classes

| Class | Package | Description |
|-------|---------|-------------|
| `HeapFactory` | `org.netbeans.lib.profiler.heap` | Factory for creating Heap from HPROF files |
| `Heap` | `org.netbeans.lib.profiler.heap` | Main interface to heap dump data |
| `HeapSummary` | `org.netbeans.lib.profiler.heap` | Summary statistics (instances, bytes, timestamp) |
| `JavaClass` | `org.netbeans.lib.profiler.heap` | Represents a class in the heap |
| `Instance` | `org.netbeans.lib.profiler.heap` | Represents an object instance |
| `FieldValue` | `org.netbeans.lib.profiler.heap` | Field values of instances |
| `GCRoot` | `org.netbeans.lib.profiler.heap` | GC root references |
| `OQLEngine` | `org.netbeans.modules.profiler.oql` | OQL query execution engine |

**Basic Usage:**

```java
import org.netbeans.lib.profiler.heap.*;
import org.netbeans.modules.profiler.oql.engine.api.*;

// Load heap dump
Heap heap = HeapFactory.createHeap(new File("dump.hprof"));

// For batch processing (no temp files):
Heap fastHeap = HeapFactory.createFastHeap(new File("dump.hprof"));

// Create OQL engine
OQLEngine engine = new OQLEngine(heap);

// Execute query
engine.executeQuery(
    "select s from java.lang.String s where s.value.length > 1000",
    new OQLEngine.ObjectVisitor() {
        public boolean visit(Object obj) {
            System.out.println(obj);
            return false; // continue iteration
        }
    }
);
```

---

## 4. Use Cases

### 4.1 Memory Leak Detection

**Purpose:** Identify objects unexpectedly retained in memory, consuming resources and potentially causing OutOfMemoryError.

**Find large String objects:**
```javascript
select s from java.lang.String s where s.value.length > 10000
```

**Find large byte arrays:**
```javascript
select a from byte[] a where a.length > 1000000
```

**Find oversized collections:**
```javascript
select m from java.util.HashMap m where m.size > 10000
select l from java.util.ArrayList l where l.size > 10000
select c from java.util.concurrent.ConcurrentHashMap c where c.size > 50000
```

**Find objects with high retained size:**
```javascript
select o from java.lang.Object o where rsizeof(o) > 10000000
```

**Java Implementation:**
```java
public List<Instance> findLargeObjects(Heap heap, long minRetainedSize) {
    List<Instance> results = new ArrayList<>();
    OQLEngine engine = new OQLEngine(heap);
    
    String query = String.format(
        "select o from java.lang.Object o where rsizeof(o) > %d", 
        minRetainedSize
    );
    
    engine.executeQuery(query, obj -> {
        if (obj instanceof Instance) {
            results.add((Instance) obj);
        }
        return false;
    });
    
    return results;
}
```

---

### 4.2 Object Instance Analysis

**Purpose:** Analyze specific object types, their field values, and relationships.

**Find all instances of a specific class:**
```javascript
select o from com.myapp.model.User o
```

**Find instances with specific field values:**
```javascript
select o from com.myapp.model.Order o where o.status.toString() == "PENDING"
```

**Find instances with null fields:**
```javascript
select o from com.myapp.model.Customer o where o.email == null
```

**Return multiple fields as JSON:**
```javascript
select {
    id: o.id,
    name: o.name.toString(),
    size: sizeof(o)
} from com.myapp.model.Entity o
```

**Java Implementation:**
```java
public void analyzeInstances(Heap heap, String className) {
    JavaClass javaClass = heap.getJavaClassByName(className);
    if (javaClass == null) {
        System.out.println("Class not found: " + className);
        return;
    }
    
    System.out.println("Class: " + javaClass.getName());
    System.out.println("Instance count: " + javaClass.getInstancesCount());
    System.out.println("Total size: " + javaClass.getAllInstancesSize());
    
    // Iterate instances
    for (Instance instance : javaClass.getInstances()) {
        System.out.println("  Instance ID: " + instance.getInstanceId());
        System.out.println("  Size: " + instance.getSize());
        
        for (FieldValue fv : instance.getFieldValues()) {
            System.out.println("    " + fv.getField().getName() + " = " + fv.getValue());
        }
    }
}
```

---

### 4.3 Class Histogram Generation

**Purpose:** Generate memory usage statistics grouped by class, similar to `jmap -histo`.

**Get class distribution:**
```javascript
var classes = [];
heap.forEachClass(function(cls) {
    if (cls.getInstancesCount() > 0) {
        classes.push({
            name: cls.getName(),
            count: cls.getInstancesCount(),
            size: cls.getAllInstancesSize()
        });
    }
});
classes.sort(function(a, b) { return b.size - a.size; });
classes.slice(0, 20);
```

**Java Implementation:**
```java
public List<ClassHistogramEntry> generateHistogram(Heap heap, int topN) {
    List<ClassHistogramEntry> histogram = new ArrayList<>();
    
    for (JavaClass jc : heap.getAllClasses()) {
        long count = jc.getInstancesCount();
        if (count > 0) {
            histogram.add(new ClassHistogramEntry(
                jc.getName(),
                count,
                jc.getAllInstancesSize()
            ));
        }
    }
    
    histogram.sort((a, b) -> Long.compare(b.totalSize, a.totalSize));
    return histogram.subList(0, Math.min(topN, histogram.size()));
}

record ClassHistogramEntry(String className, long instanceCount, long totalSize) {}
```

---

### 4.4 GC Root Analysis

**Purpose:** Identify why objects are not being garbage collected by analyzing GC roots.

**Find all GC roots:**
```javascript
select heap.roots()
```

**Find GC roots of specific type:**
```javascript
select r from instanceof java.lang.ref.Finalizer r
```

**Find objects held by threads:**
```javascript
select { 
    thread: t.name.toString(), 
    object: t 
} from java.lang.Thread t
```

**Java Implementation:**
```java
public void analyzeGCRoots(Heap heap) {
    Collection<GCRoot> roots = heap.getGCRoots();
    
    Map<String, Long> rootsByType = new HashMap<>();
    
    for (GCRoot root : roots) {
        String kind = root.getKind();
        rootsByType.merge(kind, 1L, Long::sum);
        
        Instance instance = root.getInstance();
        if (instance != null) {
            System.out.println("Root: " + kind + 
                " -> " + instance.getJavaClass().getName() +
                " (ID: " + instance.getInstanceId() + ")");
        }
    }
    
    System.out.println("\nGC Roots by Type:");
    rootsByType.forEach((type, count) -> 
        System.out.println("  " + type + ": " + count));
}
```

---

### 4.5 Thread Analysis

**Purpose:** Analyze thread states and objects referenced by thread stacks.

**Find all threads:**
```javascript
select t from java.lang.Thread t
```

**Find daemon threads:**
```javascript
select t from java.lang.Thread t where t.daemon == true
```

**Find threads by name pattern:**
```javascript
select t from java.lang.Thread t where /pool/.test(t.name.toString())
```

**Thread details with state:**
```javascript
select {
    name: t.name.toString(),
    daemon: t.daemon,
    priority: t.priority,
    threadId: t.tid
} from java.lang.Thread t
```

**Java Implementation:**
```java
public void analyzeThreads(Heap heap) {
    OQLEngine engine = new OQLEngine(heap);
    
    String query = "select { " +
        "name: t.name ? t.name.toString() : 'unnamed', " +
        "daemon: t.daemon, " +
        "priority: t.priority " +
        "} from java.lang.Thread t";
    
    engine.executeQuery(query, obj -> {
        System.out.println("Thread: " + obj);
        return false;
    });
}
```

---

### 4.6 String Analysis

**Purpose:** Analyze String objects for memory optimization opportunities (duplicate strings, long strings, etc.).

**Find duplicate strings:**
```javascript
var strings = {};
heap.forEachObject(function(s) {
    var val = s.toString();
    if (!strings[val]) strings[val] = 0;
    strings[val]++;
}, "java.lang.String");

var duplicates = [];
for (var k in strings) {
    if (strings[k] > 10) {
        duplicates.push({value: k.substring(0, 50), count: strings[k]});
    }
}
duplicates.sort(function(a,b) { return b.count - a.count; });
duplicates.slice(0, 20);
```

**Find strings matching pattern:**
```javascript
select s from java.lang.String s where /password/i.test(s.toString())
select s from java.lang.String s where /jdbc:/.test(s.toString())
select s from java.lang.String s where s.toString().startsWith("Bearer ")
```

**Find long strings:**
```javascript
select {
    preview: s.toString().substring(0, 100),
    length: s.value.length
} from java.lang.String s where s.value.length > 10000
```

**Java Implementation:**
```java
public Map<String, Integer> findDuplicateStrings(Heap heap, int minCount) {
    Map<String, Integer> stringCounts = new HashMap<>();
    
    JavaClass stringClass = heap.getJavaClassByName("java.lang.String");
    for (Instance instance : stringClass.getInstances()) {
        String value = getStringValue(instance);
        if (value != null) {
            stringCounts.merge(value, 1, Integer::sum);
        }
    }
    
    return stringCounts.entrySet().stream()
        .filter(e -> e.getValue() >= minCount)
        .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
        .collect(Collectors.toMap(
            Map.Entry::getKey, 
            Map.Entry::getValue, 
            (a, b) -> a, 
            LinkedHashMap::new
        ));
}

private String getStringValue(Instance stringInstance) {
    // Implementation depends on JDK version (String internals changed)
    FieldValue valueField = stringInstance.getValueOfField("value");
    if (valueField != null && valueField.getValue() instanceof Instance) {
        // Handle char[] or byte[] depending on JDK version
    }
    return null;
}
```

---

### 4.7 Collection Analysis

**Purpose:** Analyze collections for sizing issues, empty collections, and memory waste.

**Find empty collections:**
```javascript
select m from java.util.HashMap m where m.size == 0
select l from java.util.ArrayList l where l.size == 0
```

**Find oversized ArrayList (wasted capacity):**
```javascript
select {
    list: l,
    size: l.size,
    capacity: l.elementData.length,
    waste: l.elementData.length - l.size
} from java.util.ArrayList l 
where l.elementData.length > l.size * 2 && l.elementData.length > 100
```

**Find large HashMap with poor load factor:**
```javascript
select {
    map: m,
    size: m.size,
    buckets: m.table.length,
    loadFactor: m.size / m.table.length
} from java.util.HashMap m where m.size > 1000
```

**Count collection instances by type:**
```javascript
var counts = {};
heap.forEachObject(function(c) {
    var name = classof(c).name;
    counts[name] = (counts[name] || 0) + 1;
}, "java.util.Collection");
counts;
```

---

### 4.8 ClassLoader Leak Detection

**Purpose:** Detect ClassLoader leaks common in application servers and OSGi environments.

**Find all ClassLoaders:**
```javascript
select cl from instanceof java.lang.ClassLoader cl
```

**Find WebApp ClassLoaders:**
```javascript
select cl from instanceof java.lang.ClassLoader cl 
where /WebappClassLoader|ParallelWebappClassLoader/.test(classof(cl).name)
```

**Find classes loaded by specific ClassLoader:**
```javascript
select c from instanceof java.lang.Class c 
where c.classLoader != null && 
      classof(c.classLoader).name.contains("WebappClassLoader")
```

**Java Implementation:**
```java
public void detectClassLoaderLeaks(Heap heap) {
    JavaClass classLoaderClass = heap.getJavaClassByName("java.lang.ClassLoader");
    
    Map<String, List<Instance>> loadersByType = new HashMap<>();
    
    for (Instance instance : classLoaderClass.getInstances()) {
        String className = instance.getJavaClass().getName();
        loadersByType.computeIfAbsent(className, k -> new ArrayList<>())
                     .add(instance);
    }
    
    System.out.println("ClassLoader instances by type:");
    loadersByType.forEach((type, instances) -> {
        System.out.println("  " + type + ": " + instances.size());
        
        // Multiple webapp classloaders might indicate leak
        if (type.contains("WebappClassLoader") && instances.size() > 1) {
            System.out.println("    WARNING: Possible ClassLoader leak!");
        }
    });
}
```

---

### 4.9 Session/Cache Analysis

**Purpose:** Analyze HTTP sessions, caches, and other application-specific memory structures.

**Find HTTP sessions:**
```javascript
select s from instanceof org.apache.catalina.session.StandardSession s
```

**Find Hibernate sessions:**
```javascript
select s from instanceof org.hibernate.internal.SessionImpl s
```

**Find EhCache entries:**
```javascript
select e from instanceof net.sf.ehcache.Element e
```

**Find Spring beans:**
```javascript
select b from instanceof org.springframework.beans.factory.config.BeanDefinition b
```

**Analyze Caffeine cache:**
```javascript
select {
    cache: c,
    size: c.data.size
} from instanceof com.github.benmanes.caffeine.cache.BoundedLocalCache c
```

---

### 4.10 Custom Domain Object Analysis

**Purpose:** Query application-specific objects for business logic validation.

**Find orders in specific state:**
```javascript
select o from com.myapp.Order o where o.status.name.toString() == "PROCESSING"
```

**Find users with expired sessions:**
```javascript
select {
    user: u.username.toString(),
    lastAccess: u.lastAccessTime
} from com.myapp.UserSession u 
where u.lastAccessTime < (new Date().getTime() - 3600000)
```

**Find connections not returned to pool:**
```javascript
select c from com.zaxxer.hikari.pool.ProxyConnection c 
where c.delegate != null
```

**Java Implementation Template:**
```java
public <T> List<T> queryDomainObjects(Heap heap, String query, 
                                       Function<Instance, T> mapper) {
    List<T> results = new ArrayList<>();
    OQLEngine engine = new OQLEngine(heap);
    
    engine.executeQuery(query, obj -> {
        if (obj instanceof Instance) {
            T mapped = mapper.apply((Instance) obj);
            if (mapped != null) {
                results.add(mapped);
            }
        }
        return false;
    });
    
    return results;
}
```

---

### 4.11 Duplicate Object Detection

**Purpose:** Find duplicate objects that could be deduplicated to save memory.

**Find duplicate arrays:**
```javascript
var arrays = {};
heap.forEachObject(function(a) {
    var key = a.length + ":" + (a[0] || "");
    if (!arrays[key]) arrays[key] = [];
    arrays[key].push(a);
}, "int[]");

var dups = [];
for (var k in arrays) {
    if (arrays[k].length > 5) {
        dups.push({pattern: k, count: arrays[k].length});
    }
}
dups;
```

**Find duplicate Date objects:**
```javascript
var dates = {};
heap.forEachObject(function(d) {
    var time = d.fastTime;
    dates[time] = (dates[time] || 0) + 1;
}, "java.util.Date");

var duplicates = [];
for (var t in dates) {
    if (dates[t] > 10) {
        duplicates.push({time: t, count: dates[t]});
    }
}
duplicates;
```

---

### 4.12 Reference Chain Analysis

**Purpose:** Find the reference path from GC root to a specific object.

**Get referrers to an object:**
```javascript
select referrers(o) from java.lang.Object o where objectid(o) == 12345678
```

**Get objects referenced by an object:**
```javascript
select reachables(o) from com.myapp.Cache o
```

**Find shortest path to GC root:**
```javascript
select root(o) from java.lang.Object o where objectid(o) == 12345678
```

**Java Implementation:**
```java
public void printPathToGCRoot(Heap heap, Instance instance) {
    Instance current = instance;
    int depth = 0;
    
    while (current != null && depth < 50) {
        System.out.println("  ".repeat(depth) + 
            current.getJavaClass().getName() + 
            " (id=" + current.getInstanceId() + ")");
        
        Instance nearestRoot = current.getNearestGCRootPointer();
        if (nearestRoot == current || nearestRoot == null) {
            // Reached GC root
            GCRoot root = heap.getGCRoot(current);
            if (root != null) {
                System.out.println("  ".repeat(depth + 1) + 
                    "GC Root: " + root.getKind());
            }
            break;
        }
        current = nearestRoot;
        depth++;
    }
}
```

---

### 4.13 Heap Comparison

**Purpose:** Compare two heap dumps to identify memory growth.

**Java Implementation:**
```java
public class HeapComparator {
    
    public record ClassDiff(String className, long countDiff, long sizeDiff) {}
    
    public List<ClassDiff> compareHeaps(File heap1File, File heap2File) 
            throws IOException {
        
        Heap heap1 = HeapFactory.createFastHeap(heap1File);
        Heap heap2 = HeapFactory.createFastHeap(heap2File);
        
        Map<String, long[]> stats1 = collectStats(heap1);
        Map<String, long[]> stats2 = collectStats(heap2);
        
        List<ClassDiff> diffs = new ArrayList<>();
        
        Set<String> allClasses = new HashSet<>();
        allClasses.addAll(stats1.keySet());
        allClasses.addAll(stats2.keySet());
        
        for (String className : allClasses) {
            long[] s1 = stats1.getOrDefault(className, new long[]{0, 0});
            long[] s2 = stats2.getOrDefault(className, new long[]{0, 0});
            
            long countDiff = s2[0] - s1[0];
            long sizeDiff = s2[1] - s1[1];
            
            if (Math.abs(countDiff) > 100 || Math.abs(sizeDiff) > 10000) {
                diffs.add(new ClassDiff(className, countDiff, sizeDiff));
            }
        }
        
        diffs.sort((a, b) -> Long.compare(
            Math.abs(b.sizeDiff), Math.abs(a.sizeDiff)));
        
        return diffs;
    }
    
    private Map<String, long[]> collectStats(Heap heap) {
        Map<String, long[]> stats = new HashMap<>();
        for (JavaClass jc : heap.getAllClasses()) {
            stats.put(jc.getName(), new long[]{
                jc.getInstancesCount(),
                jc.getAllInstancesSize()
            });
        }
        return stats;
    }
}
```

---

### 4.14 Batch Processing Multiple Dumps

**Purpose:** Process multiple heap dumps for trending and pattern detection.

**Java Implementation:**
```java
public class HeapDumpBatchProcessor {
    
    public void processDirectory(Path dumpDir, Consumer<HeapAnalysisResult> handler) 
            throws IOException {
        
        try (var files = Files.list(dumpDir)) {
            files.filter(p -> p.toString().endsWith(".hprof"))
                 .sorted()
                 .forEach(path -> {
                     try {
                         HeapAnalysisResult result = analyzeHeap(path);
                         handler.accept(result);
                     } catch (Exception e) {
                         System.err.println("Failed to process: " + path);
                         e.printStackTrace();
                     }
                 });
        }
    }
    
    private HeapAnalysisResult analyzeHeap(Path path) throws IOException {
        Heap heap = HeapFactory.createFastHeap(path.toFile());
        HeapSummary summary = heap.getSummary();
        
        // Run standard queries
        List<String> leakSuspects = findLeakSuspects(heap);
        Map<String, Long> topClasses = getTopClassesBySize(heap, 10);
        
        return new HeapAnalysisResult(
            path.getFileName().toString(),
            summary.getTime(),
            summary.getTotalLiveBytes(),
            summary.getTotalLiveInstances(),
            leakSuspects,
            topClasses
        );
    }
    
    record HeapAnalysisResult(
        String fileName,
        long timestamp,
        long totalBytes,
        long totalInstances,
        List<String> leakSuspects,
        Map<String, Long> topClasses
    ) {}
}
```

---

### 4.15 Integration with Monitoring Systems

**Purpose:** Export heap analysis results to monitoring systems (Prometheus, Grafana, etc.).

**Java Implementation:**
```java
public class HeapMetricsExporter {
    
    private final MeterRegistry registry; // Micrometer registry
    
    public void exportMetrics(Heap heap) {
        HeapSummary summary = heap.getSummary();
        
        // Gauge metrics
        Gauge.builder("heap.total.bytes", summary, HeapSummary::getTotalLiveBytes)
             .register(registry);
        
        Gauge.builder("heap.total.instances", summary, s -> s.getTotalLiveInstances())
             .register(registry);
        
        // Per-class metrics for top consumers
        for (JavaClass jc : getTopClasses(heap, 20)) {
            String safeName = jc.getName().replace('.', '_');
            
            Gauge.builder("heap.class.instances", jc, JavaClass::getInstancesCount)
                 .tag("class", safeName)
                 .register(registry);
            
            Gauge.builder("heap.class.bytes", jc, JavaClass::getAllInstancesSize)
                 .tag("class", safeName)
                 .register(registry);
        }
    }
    
    public String generatePrometheusOutput(Heap heap) {
        StringBuilder sb = new StringBuilder();
        HeapSummary summary = heap.getSummary();
        
        sb.append("# HELP heap_total_bytes Total heap size in bytes\n");
        sb.append("# TYPE heap_total_bytes gauge\n");
        sb.append("heap_total_bytes ").append(summary.getTotalLiveBytes()).append("\n\n");
        
        sb.append("# HELP heap_total_instances Total number of instances\n");
        sb.append("# TYPE heap_total_instances gauge\n");
        sb.append("heap_total_instances ").append(summary.getTotalLiveInstances()).append("\n\n");
        
        sb.append("# HELP heap_class_bytes Bytes per class\n");
        sb.append("# TYPE heap_class_bytes gauge\n");
        for (JavaClass jc : getTopClasses(heap, 50)) {
            sb.append("heap_class_bytes{class=\"")
              .append(jc.getName())
              .append("\"} ")
              .append(jc.getAllInstancesSize())
              .append("\n");
        }
        
        return sb.toString();
    }
}
```

---

## 5. OQL Syntax Reference

### Basic SELECT

```javascript
select <expression> from <class> <alias> [where <condition>]
```

### Class Specification

| Syntax | Description |
|--------|-------------|
| `java.lang.String` | Exact class |
| `instanceof java.util.Map` | Class and all subclasses |
| `char[]` or `[C` | Primitive array |
| `java.lang.Object[]` or `[Ljava.lang.Object;` | Object array |

### Operators

| Operator | Description |
|----------|-------------|
| `==`, `!=` | Equality |
| `<`, `>`, `<=`, `>=` | Comparison |
| `&&`, `\|\|`, `!` | Logical |
| `+`, `-`, `*`, `/` | Arithmetic |
| `/regex/.test(string)` | Regular expression |

### Field Access

```javascript
// Direct field access
o.fieldName

// Nested access
o.parent.child.value

// Array access
a[0], a[index]

// Method-like access (special)
s.toString()  // For strings
```

---

## 6. Built-in Functions

| Function | Description | Example |
|----------|-------------|---------|
| `sizeof(o)` | Shallow size in bytes | `sizeof(o) > 1000` |
| `rsizeof(o)` | Retained size in bytes | `rsizeof(o) > 1000000` |
| `objectid(o)` | Object ID | `objectid(o) == 12345` |
| `classof(o)` | JavaClass of object | `classof(o).name` |
| `reachables(o)` | All reachable objects | `select reachables(o)` |
| `referrers(o)` | Objects referencing o | `select referrers(o)` |
| `referees(o)` | Objects referenced by o | `select referees(o)` |
| `root(o)` | Nearest GC root | `select root(o)` |
| `heap.findClass(name)` | Find class by name | `heap.findClass('java.lang.String')` |
| `heap.findObject(id)` | Find object by ID | `heap.findObject(12345)` |
| `heap.objects(class)` | Iterator of instances | `heap.objects('java.lang.Thread')` |
| `heap.classes()` | Iterator of all classes | `heap.classes()` |
| `heap.roots()` | All GC roots | `heap.roots()` |
| `toHtml(o)` | HTML representation | For display purposes |

---

## 7. JavaScript Integration

OQL supports full JavaScript for complex analysis:

```javascript
// Variables and functions
var count = 0;
function isLarge(obj) {
    return sizeof(obj) > 10000;
}

// Iterate with forEachObject
heap.forEachObject(function(obj) {
    if (isLarge(obj)) count++;
}, "java.lang.Object");

// Array operations
var results = [];
heap.forEachObject(function(s) {
    if (s.value.length > 100) {
        results.push({
            value: s.toString().substring(0, 50),
            length: s.value.length
        });
    }
}, "java.lang.String");

results.sort(function(a, b) { return b.length - a.length; });
results.slice(0, 10);

// Map/filter/reduce patterns
var strings = toArray(heap.objects("java.lang.String"));
var longStrings = filter(strings, "it.value.length > 1000");
var count = length(longStrings);
```

### Built-in JavaScript Helpers

| Function | Description |
|----------|-------------|
| `map(array, expr)` | Transform elements |
| `filter(array, expr)` | Filter elements |
| `sort(array, expr)` | Sort elements |
| `toArray(iterator)` | Convert to array |
| `length(array)` | Array length |
| `sum(array, expr)` | Sum values |
| `max(array, expr)` | Maximum value |
| `min(array, expr)` | Minimum value |
| `unique(array)` | Remove duplicates |
| `contains(array, obj)` | Check containment |

---

## 8. Performance Considerations

### Use FastHeap for Batch Processing

```java
// Standard heap - creates index files, better for interactive use
Heap heap = HeapFactory.createHeap(file);

// Fast heap - no temp files, better for batch/automated processing
Heap fastHeap = HeapFactory.createFastHeap(file);

// With custom buffer size
Heap fastHeap = HeapFactory.createFastHeap(file, 256 * 1024 * 1024); // 256MB
```

### Avoid Expensive Operations

```java
// SLOW: rsizeof() calculates retained size for each object
select o from java.lang.Object o where rsizeof(o) > 1000000

// FASTER: Filter by class first, then calculate retained size
select o from java.util.HashMap o where rsizeof(o) > 1000000
```

### Limit Result Sets

```javascript
// Use slice() to limit results
var results = toArray(heap.objects("java.lang.String"));
results.slice(0, 1000);  // First 1000 only
```

### Memory Requirements

- Heap analysis requires approximately 1-2x the heap dump size in RAM
- For very large dumps (10GB+), consider streaming approaches
- Use `-Xmx` to allocate sufficient memory to the analyzer JVM

---

## 9. Complete Example Application

```java
package com.example.heapanalyzer;

import org.netbeans.lib.profiler.heap.*;
import org.netbeans.modules.profiler.oql.engine.api.*;
import java.io.*;
import java.util.*;

public class HeapAnalyzerApp {
    
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.out.println("Usage: java HeapAnalyzerApp <heap.hprof> [query]");
            System.exit(1);
        }
        
        File heapFile = new File(args[0]);
        System.out.println("Loading heap dump: " + heapFile);
        
        Heap heap = HeapFactory.createFastHeap(heapFile);
        HeapSummary summary = heap.getSummary();
        
        System.out.println("\n=== Heap Summary ===");
        System.out.println("Total instances: " + summary.getTotalLiveInstances());
        System.out.println("Total bytes: " + formatBytes(summary.getTotalLiveBytes()));
        System.out.println("Classes: " + heap.getAllClasses().size());
        System.out.println("GC roots: " + heap.getGCRoots().size());
        System.out.println("Timestamp: " + new Date(summary.getTime()));
        
        System.out.println("\n=== Top 10 Classes by Size ===");
        List<JavaClass> classes = new ArrayList<>(heap.getAllClasses());
        classes.sort((a, b) -> Long.compare(
            b.getAllInstancesSize(), a.getAllInstancesSize()));
        
        for (int i = 0; i < Math.min(10, classes.size()); i++) {
            JavaClass jc = classes.get(i);
            System.out.printf("%,12d bytes  %,8d instances  %s%n",
                jc.getAllInstancesSize(),
                jc.getInstancesCount(),
                jc.getName());
        }
        
        if (args.length > 1) {
            String query = args[1];
            System.out.println("\n=== Query Results ===");
            System.out.println("Query: " + query);
            System.out.println();
            
            OQLEngine engine = new OQLEngine(heap);
            List<Object> results = new ArrayList<>();
            
            engine.executeQuery(query, obj -> {
                results.add(obj);
                if (results.size() >= 100) {
                    return true; // Stop after 100 results
                }
                return false;
            });
            
            for (Object result : results) {
                System.out.println(result);
            }
            
            System.out.println("\nTotal results: " + results.size() + 
                (results.size() >= 100 ? " (limited)" : ""));
        }
    }
    
    private static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }
}
```

**Build and Run:**

```bash
# Compile
mvn clean package

# Run with heap dump
java -Xmx4g -jar heap-analyzer.jar /path/to/dump.hprof

# Run with custom query
java -Xmx4g -jar heap-analyzer.jar /path/to/dump.hprof \
    "select s from java.lang.String s where s.value.length > 1000"
```

---

## References

- [Apache NetBeans GitHub](https://github.com/apache/netbeans)
- [NetBeans Profiler Documentation](https://netbeans.apache.org/tutorial/main/kb/docs/java/profiler-intro/)
- [VisualVM OQL Help](https://visualvm.github.io/oqlhelp.html)
- [HPROF Binary Format](https://hg.openjdk.org/jdk/jdk/file/tip/src/hotspot/share/services/heapDumper.cpp)
