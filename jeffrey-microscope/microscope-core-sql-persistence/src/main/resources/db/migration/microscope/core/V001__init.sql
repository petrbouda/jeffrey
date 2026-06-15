/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

--
-- LOCAL CORE DATABASE SCHEMA
-- Contains tables used by the local deployment. Profile event data is stored in per-profile databases.
--

--
-- REMOTE SERVER TABLES
-- One row per connected jeffrey-server. Workspaces are NOT stored locally —
-- they are listed live from the server via gRPC ListWorkspaces.
--

CREATE TABLE IF NOT EXISTS remote_servers
(
    server_id   VARCHAR PRIMARY KEY,
    name        VARCHAR NOT NULL,
    hostname    VARCHAR NOT NULL,
    port        INTEGER NOT NULL DEFAULT 443,
    -- gRPC client uses cleartext h2c when true, TLS when false. Default false
    -- preserves the existing public-internet TLS workflow; flip to true for
    -- in-cluster Service DNS or trusted-LAN setups.
    plaintext   BOOLEAN NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ NOT NULL,
    UNIQUE (hostname, port)
);

--
-- RECORDING TABLES
-- Used by both project recordings (project_id set) and quick analysis recordings (project_id NULL).
--

CREATE TABLE IF NOT EXISTS recordings
(
    id                    VARCHAR NOT NULL PRIMARY KEY,
    project_id            VARCHAR,
    recording_name        VARCHAR NOT NULL,
    group_id              VARCHAR,
    event_source          VARCHAR NOT NULL,
    created_at            TIMESTAMPTZ NOT NULL,
    recording_started_at  TIMESTAMPTZ,
    recording_finished_at TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS recording_files
(
    id             VARCHAR NOT NULL PRIMARY KEY,
    project_id     VARCHAR,
    recording_id   VARCHAR NOT NULL,
    filename       VARCHAR NOT NULL,
    supported_type VARCHAR NOT NULL,
    uploaded_at    TIMESTAMPTZ NOT NULL,
    size_in_bytes  BIGINT  NOT NULL
);


CREATE TABLE IF NOT EXISTS recording_groups
(
    id         VARCHAR NOT NULL PRIMARY KEY,
    project_id VARCHAR,
    name       VARCHAR NOT NULL,
    created_at TIMESTAMPTZ
);

--
-- RECORDING TAGS
-- Key-value metadata attached to a recording. Tags whose key starts with "origin." are
-- application-managed (set automatically when a recording lands in QA from a project
-- session) and read-only. Other keys are reserved for user-defined tags.
--

CREATE TABLE IF NOT EXISTS recording_tags
(
    recording_id VARCHAR NOT NULL,
    tag_key      VARCHAR NOT NULL,
    tag_value    VARCHAR NOT NULL,
    PRIMARY KEY (recording_id, tag_key)
);

CREATE INDEX IF NOT EXISTS recording_tags_key_value_idx
    ON recording_tags (tag_key, tag_value);

--
-- PROFILE METADATA TABLE
-- Note: Profile event data (events, stacktraces, frames, threads, cache) is stored in per-profile databases.
--

CREATE TABLE IF NOT EXISTS profiles
(
    profile_id            VARCHAR NOT NULL,
    project_id            VARCHAR,
    workspace_id          VARCHAR,
    profile_name          VARCHAR NOT NULL,
    event_source          VARCHAR NOT NULL,
    created_at            TIMESTAMPTZ  NOT NULL,
    recording_id          VARCHAR,
    recording_started_at  TIMESTAMPTZ,
    recording_finished_at TIMESTAMPTZ,
    enabled_at            TIMESTAMPTZ,
    modified              BOOLEAN NOT NULL DEFAULT false,
    PRIMARY KEY (profile_id)
);

--
-- PROFILER SETTINGS TABLE
--

CREATE TABLE IF NOT EXISTS profiler_settings
(
    workspace_id    VARCHAR,
    project_id      VARCHAR,
    agent_settings  VARCHAR NOT NULL,
    UNIQUE (workspace_id, project_id)
);

--
-- APPLICATION SETTINGS TABLE
-- Stores user-configurable application settings as key-value pairs grouped by category.
-- Secret values (e.g., API keys) are stored encrypted with machine-bound AES-256-GCM.
--

CREATE TABLE IF NOT EXISTS settings
(
    category VARCHAR NOT NULL,
    name     VARCHAR NOT NULL,
    value    VARCHAR NOT NULL,
    secret   BOOLEAN NOT NULL DEFAULT false,
    PRIMARY KEY (category, name)
);

--
-- GUARDIAN GUARDS
-- Central, editable definitions of every Guardian guard. Built-in guards (built_in = true) are
-- seeded below; users can edit them or add custom guards from the Microscope UI. matcher_spec and
-- preconditions hold JSON (stored as text; parsed in Java) — see the MatchExpr / TraversalStrategy
-- sealed types in the profile-guardian module.
--

CREATE TABLE IF NOT EXISTS guardian_guards
(
    guard_id          VARCHAR     NOT NULL PRIMARY KEY,
    name              VARCHAR     NOT NULL,
    enabled           BOOLEAN     NOT NULL DEFAULT true,
    built_in          BOOLEAN     NOT NULL DEFAULT false,
    group_kind        VARCHAR     NOT NULL,
    category          VARCHAR     NOT NULL,
    result_type       VARCHAR     NOT NULL,
    target_frame      VARCHAR     NOT NULL,
    matching_type     VARCHAR     NOT NULL,
    info_threshold    DOUBLE      NOT NULL,
    warning_threshold DOUBLE      NOT NULL,
    matcher_spec      VARCHAR     NOT NULL,
    preconditions     VARCHAR,
    summary_noun      VARCHAR,
    explanation       VARCHAR,
    solution          VARCHAR,
    created_at        TIMESTAMPTZ NOT NULL
);

--
-- GUARDIAN GROUP SETTINGS
-- Per-group minimum-sample gates: a group's guards run only when the recording has at least this
-- many samples in that group's event dimension.
--

CREATE TABLE IF NOT EXISTS guardian_group_settings
(
    group_kind  VARCHAR NOT NULL PRIMARY KEY,
    min_samples BIGINT  NOT NULL
);

INSERT INTO guardian_group_settings (group_kind, min_samples) VALUES
    ('EXECUTION_SAMPLE', 1000),
    ('CPU_TIME_SAMPLE', 1000),
    ('ALLOCATION', 1000),
    ('WALL_CLOCK', 1000),
    ('BLOCKING', 100);


-- Built-in guard definitions (73 rows). Generated; edit via the Microscope UI.
INSERT INTO guardian_guards (guard_id, name, enabled, built_in, group_kind, category, result_type, target_frame, matching_type, info_threshold, warning_threshold, matcher_spec, preconditions, summary_noun, explanation, solution, created_at) VALUES
    ('exec-logback', $g$Logback CPU Overhead$g$, true, true, 'EXECUTION_SAMPLE', 'APPLICATION', 'SAMPLES', 'JAVA', 'FULL_MATCH', 0.02, 0.03, '{"anchor":{"type":"Predicate","op":"PREFIX","value":"ch.qos.logback"}}', NULL, $g$the logging$g$, $g$Extensive logging can cause significant overhead in allocation and CPU usage. Some application
with a lower number of transactions/requests can log even detailed information, however, when the
application is under a heavy load, the logging can become a bottleneck.$g$, $g$<ul>
    <li>Consider whether the logging is necessary, and difference between the logging levels (INFO, DEBUG, ..)
    <li>Use templating for the log messages to avoid the string concatenation (even if the log level is not enabled)
</ul>$g$, TIMESTAMPTZ '2026-01-01 00:00:00+00'),
    ('exec-log4j', $g$Log4j CPU Overhead$g$, true, true, 'EXECUTION_SAMPLE', 'APPLICATION', 'SAMPLES', 'JAVA', 'FULL_MATCH', 0.02, 0.03, '{"anchor":{"type":"Predicate","op":"PREFIX","value":"org.apache.logging.log4j."}}', NULL, $g$the logging$g$, $g$Extensive logging using Log4j2 can cause significant overhead in allocation and CPU usage.
Some applications with a lower number of transactions/requests can log even detailed information,
however, when the application is under a heavy load, the logging can become a bottleneck.$g$, $g$<ul>
    <li>Consider whether the logging is necessary, and difference between the logging levels (INFO, DEBUG, ..)
    <li>Use parameterized messages to avoid string concatenation when the log level is not enabled
    <li>Consider using asynchronous appenders to reduce the impact on the application thread
</ul>$g$, TIMESTAMPTZ '2026-01-01 00:00:00+00'),
    ('exec-hashmap', $g$HashMap Collisions$g$, true, true, 'EXECUTION_SAMPLE', 'APPLICATION', 'SAMPLES', 'JAVA', 'FULL_MATCH', 0.02, 0.04, '{"anchor":{"type":"AnyOf","of":[{"type":"Predicate","op":"SUFFIX","value":"Map$TreeNode#getTreeNode"},{"type":"Predicate","op":"SUFFIX","value":"Map$TreeNode#putTreeVal"},{"type":"Predicate","op":"SUFFIX","value":"Map$TreeNode#findTreeNode"}]}}', NULL, $g$hash collisions$g$, $g$Key collision is a common issue in HashMaps. It can lead to performance degradation
because the time complexity of the operations increases from O(1) to O(n). The keys that have
the same hashcode are stored in a linked list (it often uses a balanced tree for a bucket with
a small number of collisions - JEP 180, then the linked list takes a place).
The more collisions, the longer the time to find the key because the list
needs to be iterated one item after another.
<br>
The Guard keeps an eye only on hash maps that are implemented in OpenJDK.$g$, $g$The solution is to reduce the number of collisions. It can be achieved by:
<ul>
    <li>Implementing better hashCode() and equals() methods
    <li>Using a different data structure
</ul>$g$, TIMESTAMPTZ '2026-01-01 00:00:00+00'),
    ('exec-regex', $g$Regular Expressions$g$, true, true, 'EXECUTION_SAMPLE', 'APPLICATION', 'SAMPLES', 'JAVA', 'FULL_MATCH', 0.02, 0.04, '{"anchor":{"type":"AnyOf","of":[{"type":"Predicate","op":"PREFIX","value":"java.util.regex.Matcher"},{"type":"Predicate","op":"PREFIX","value":"java.util.regex.Pattern"}]}}', NULL, $g$regular expressions$g$, $g$Regular expressions are used to match patterns in strings. They are powerful, but they can be slow.
Sometimes its inevitable to use them, but they should be used with caution.
This guard should help you to identify the places where regular expressions are used too often.
<br>
Especially <b>java.util.Pattern#compile</b> should be used only once for a pattern and avoid compiling it
every iteration.$g$, $g$<ul>
    <li>Identify the places where the regular expressions are used too often and can be replaced by a different approach
    <li>Be careful to String operation where regular expressions are used under the hood
    <li>Use <b>java.util.Pattern#compile</b> only once for a pattern and avoid compiling it every iteration
    <li>Optimize the regular expressions
</ul>$g$, TIMESTAMPTZ '2026-01-01 00:00:00+00'),
    ('exec-classloading', $g$Class Loading Overhead$g$, true, true, 'EXECUTION_SAMPLE', 'APPLICATION', 'SELF_SAMPLES', 'JAVA', 'FULL_MATCH', 0.03, 0.05, '{"anchor":{"type":"AnyOf","of":[{"type":"Predicate","op":"PREFIX","value":"java.lang.ClassLoader#loadClass"},{"type":"Predicate","op":"PREFIX","value":"java.lang.Class#forName"}]}}', NULL, $g$class loading activity$g$, $g$Excessive dynamic class loading can cause significant CPU overhead. This often occurs with
heavy use of reflection, dynamic proxies, bytecode generation frameworks (e.g., CGLIB, Byte Buddy),
or OSGi-style modular class loading. Each class load involves I/O, verification, and linking steps
that consume CPU cycles.$g$, $g$<ul>
    <li>Check if classes are being loaded repeatedly instead of being cached
    <li>Reduce reliance on reflection-heavy frameworks or configure them to cache generated classes
    <li>Consider using static compilation or ahead-of-time class generation where possible
    <li>Review usage of Class.forName() and ensure it is not called in hot paths
</ul>$g$, TIMESTAMPTZ '2026-01-01 00:00:00+00'),
    ('exec-reflection', $g$Reflection Overhead$g$, true, true, 'EXECUTION_SAMPLE', 'APPLICATION', 'SELF_SAMPLES', 'JAVA', 'FULL_MATCH', 0.03, 0.05, '{"anchor":{"type":"AnyOf","of":[{"type":"Predicate","op":"PREFIX","value":"java.lang.reflect."},{"type":"Predicate","op":"PREFIX","value":"jdk.internal.reflect."}]}}', NULL, $g$reflection activity$g$, $g$Heavy use of Java reflection (Method.invoke, Field access, Constructor.newInstance) can cause
significant CPU overhead. Reflection bypasses compile-time optimizations and requires additional
security checks, type resolution, and boxing/unboxing of arguments at runtime.$g$, $g$<ul>
    <li>Replace reflection with direct method calls or MethodHandle where possible
    <li>Cache Method, Field, and Constructor objects instead of looking them up repeatedly
    <li>Consider using code generation (e.g., Byte Buddy) to replace hot reflective paths
    <li>Review serialization frameworks that may use reflection internally
</ul>$g$, TIMESTAMPTZ '2026-01-01 00:00:00+00'),
    ('exec-serialization', $g$Java Serialization Overhead$g$, true, true, 'EXECUTION_SAMPLE', 'APPLICATION', 'SELF_SAMPLES', 'JAVA', 'FULL_MATCH', 0.03, 0.05, '{"anchor":{"type":"AnyOf","of":[{"type":"Predicate","op":"PREFIX","value":"java.io.ObjectOutputStream#writeObject"},{"type":"Predicate","op":"PREFIX","value":"java.io.ObjectInputStream#readObject"}]}}', NULL, $g$Java serialization activity$g$, $g$Java's built-in serialization (ObjectOutputStream/ObjectInputStream) is known to be slow and
resource-intensive. It uses reflection, generates significant temporary objects, and performs
complex graph traversal. This overhead is especially noticeable in high-throughput systems
that serialize/deserialize data frequently.$g$, $g$<ul>
    <li>Consider replacing Java serialization with faster alternatives (Protocol Buffers, Kryo, Jackson, etc.)
    <li>Implement Externalizable instead of Serializable for fine-grained control
    <li>Reduce the size and complexity of serialized object graphs
    <li>Cache serialized forms when the same objects are serialized repeatedly
</ul>$g$, TIMESTAMPTZ '2026-01-01 00:00:00+00'),
    ('exec-xml', $g$XML Parsing Overhead$g$, true, true, 'EXECUTION_SAMPLE', 'APPLICATION', 'SAMPLES', 'JAVA', 'FULL_MATCH', 0.03, 0.05, '{"anchor":{"type":"AnyOf","of":[{"type":"Predicate","op":"PREFIX","value":"javax.xml."},{"type":"Predicate","op":"PREFIX","value":"com.sun.xml."},{"type":"Predicate","op":"PREFIX","value":"com.sun.org.apache.xerces."}]}}', NULL, $g$XML parsing activity$g$, $g$XML processing (DOM, SAX, StAX) can be CPU-intensive, especially when parsing large documents
or when XML parsing is used in hot paths. DOM parsing in particular loads the entire document
into memory and builds an object tree, which is both CPU and memory intensive.$g$, $g$<ul>
    <li>Use StAX (streaming) parsing instead of DOM for large documents
    <li>Consider replacing XML with a more efficient format (JSON, Protocol Buffers)
    <li>Cache parsed XML documents if the same content is parsed repeatedly
    <li>Use XML binding frameworks (JAXB) with pre-compiled schemas for better performance
</ul>$g$, TIMESTAMPTZ '2026-01-01 00:00:00+00'),
    ('exec-json', $g$JSON Processing Overhead$g$, true, true, 'EXECUTION_SAMPLE', 'APPLICATION', 'SAMPLES', 'JAVA', 'FULL_MATCH', 0.03, 0.05, '{"anchor":{"type":"AnyOf","of":[{"type":"Predicate","op":"PREFIX","value":"com.fasterxml.jackson."},{"type":"Predicate","op":"PREFIX","value":"com.google.gson."}]}}', NULL, $g$JSON processing activity$g$, $g$JSON parsing and serialization (Jackson, Gson) can consume significant CPU when processing
large payloads or when used in high-throughput paths. Object mapping through reflection,
type resolution, and string processing all contribute to the overhead.$g$, $g$<ul>
    <li>Use streaming APIs (JsonParser/JsonGenerator) instead of tree model for large payloads
    <li>Reuse ObjectMapper instances (they are thread-safe in Jackson)
    <li>Consider using Jackson afterburner/blackbird module for faster serialization
    <li>Reduce the size of serialized objects by excluding unnecessary fields
</ul>$g$, TIMESTAMPTZ '2026-01-01 00:00:00+00'),
    ('exec-exception', $g$Exception Overhead$g$, true, true, 'EXECUTION_SAMPLE', 'APPLICATION', 'SAMPLES', 'JAVA', 'FULL_MATCH', 0.03, 0.05, '{"anchor":{"type":"AnyOf","of":[{"type":"Predicate","op":"SUFFIX","value":"Throwable#<init>"},{"type":"Predicate","op":"SUFFIX","value":"Throwable#fillInStackTrace"}]}}', NULL, $g$exception creation activity$g$, $g$Excessive exception creation and handling can cause significant CPU overhead. The most expensive
part is fillInStackTrace(), which walks the entire call stack to capture the stack trace.
Using exceptions for control flow or creating exceptions in hot paths leads to performance degradation.$g$, $g$<ul>
    <li>Avoid using exceptions for control flow (e.g., catching NumberFormatException instead of validating input)
    <li>Consider pre-allocated singleton exceptions with overridden fillInStackTrace() for expected errors
    <li>Use Optional or error codes instead of exceptions for expected failure cases
    <li>Check the flamegraph to identify which exception types are created most frequently
</ul>$g$, TIMESTAMPTZ '2026-01-01 00:00:00+00'),
    ('exec-stringconcat', $g$String Concatenation Overhead$g$, true, true, 'EXECUTION_SAMPLE', 'APPLICATION', 'SAMPLES', 'JAVA', 'FULL_MATCH', 0.03, 0.05, '{"anchor":{"type":"AnyOf","of":[{"type":"Predicate","op":"PREFIX","value":"java.lang.StringBuilder#append"},{"type":"Predicate","op":"PREFIX","value":"java.lang.StringBuffer#append"}]}}', NULL, $g$string concatenation activity$g$, $g$Excessive string building via StringBuilder or StringBuffer can indicate inefficient string
construction patterns, such as concatenation in loops. Each append may trigger internal array
resizing and copying, which consumes CPU cycles and generates garbage.$g$, $g$<ul>
    <li>Pre-size StringBuilder with an estimated capacity to avoid repeated resizing
    <li>Use String.join() or StringJoiner for joining collections
    <li>Consider using String.format() or MessageFormat for complex string construction
    <li>Replace StringBuffer with StringBuilder if thread-safety is not required
</ul>$g$, TIMESTAMPTZ '2026-01-01 00:00:00+00'),
    ('exec-threadsync', $g$Thread Synchronization Overhead$g$, true, true, 'EXECUTION_SAMPLE', 'APPLICATION', 'SAMPLES', 'ALL', 'FULL_MATCH', 0.03, 0.05, '{"anchor":{"type":"AnyOf","of":[{"type":"Predicate","op":"PREFIX","value":"jdk.internal.misc.Unsafe#park"},{"type":"Predicate","op":"SUFFIX","value":"ObjectMonitor::enter"}]}}', NULL, $g$thread synchronization activity$g$, $g$Heavy lock contention visible on the CPU profile indicates threads are spending time
competing for locks. This includes both Java-level synchronized blocks (ObjectMonitor::enter)
and explicit Lock implementations using Unsafe.park. High contention reduces parallelism
and can become a scalability bottleneck.$g$, $g$<ul>
    <li>Reduce the scope of synchronized blocks to the minimum necessary
    <li>Consider using concurrent data structures (ConcurrentHashMap, etc.) instead of synchronized collections
    <li>Use ReadWriteLock when reads are more frequent than writes
    <li>Consider lock-free algorithms or atomic operations for simple counters and flags
</ul>$g$, TIMESTAMPTZ '2026-01-01 00:00:00+00'),
    ('exec-crypto', $g$Crypto/TLS Overhead$g$, true, true, 'EXECUTION_SAMPLE', 'APPLICATION', 'SAMPLES', 'JAVA', 'FULL_MATCH', 0.03, 0.05, '{"anchor":{"type":"AnyOf","of":[{"type":"Predicate","op":"PREFIX","value":"javax.crypto."},{"type":"Predicate","op":"PREFIX","value":"sun.security.ssl."},{"type":"Predicate","op":"PREFIX","value":"sun.security.provider."}]}}', NULL, $g$crypto/TLS activity$g$, $g$Cryptographic operations and TLS processing can consume significant CPU, especially during
TLS handshakes, bulk encryption/decryption, and certificate validation. This is often seen
in applications with many short-lived HTTPS connections or heavy payload encryption.$g$, $g$<ul>
    <li>Enable TLS session resumption and session tickets to reduce handshake overhead
    <li>Use HTTP/2 or connection pooling to reduce the number of TLS handshakes
    <li>Consider hardware acceleration for crypto operations (AES-NI is usually enabled by default)
    <li>Review cipher suite configuration - prefer modern, efficient algorithms
</ul>$g$, TIMESTAMPTZ '2026-01-01 00:00:00+00'),
    ('exec-compress', $g$Compression Overhead$g$, true, true, 'EXECUTION_SAMPLE', 'APPLICATION', 'SAMPLES', 'JAVA', 'FULL_MATCH', 0.03, 0.05, '{"anchor":{"type":"AnyOf","of":[{"type":"Predicate","op":"PREFIX","value":"java.util.zip."},{"type":"Predicate","op":"PREFIX","value":"java.util.jar."}]}}', NULL, $g$compression/decompression activity$g$, $g$Compression and decompression (ZIP, GZIP, JAR) operations are CPU-intensive by nature.
This overhead is often seen in applications that compress HTTP responses, process ZIP archives,
or load resources from JAR files frequently.$g$, $g$<ul>
    <li>Consider whether compression is necessary for all responses/data
    <li>Adjust compression level (lower levels trade compression ratio for speed)
    <li>Cache compressed/decompressed results when the same data is processed repeatedly
    <li>Consider using faster compression algorithms (e.g., LZ4, Snappy) instead of DEFLATE
</ul>$g$, TIMESTAMPTZ '2026-01-01 00:00:00+00'),
    ('exec-finalizer', $g$Finalizer/Cleaner Overhead$g$, true, true, 'EXECUTION_SAMPLE', 'APPLICATION', 'SAMPLES', 'JAVA', 'FULL_MATCH', 0.01, 0.03, '{"anchor":{"type":"AnyOf","of":[{"type":"Predicate","op":"PREFIX","value":"java.lang.ref.Finalizer"},{"type":"Predicate","op":"PREFIX","value":"jdk.internal.ref.Cleaner"},{"type":"Predicate","op":"PREFIX","value":"java.lang.ref.ReferenceQueue"}]}}', NULL, $g$finalization / cleaner machinery$g$, $g$CPU spent inside java.lang.ref.Finalizer / jdk.internal.ref.Cleaner signals that reference-queue
processing is material. Finalization in particular has been a well-known performance hazard since
JDK 1.0 - every finalizable object costs two GC cycles (one to queue, one after run()) and serialises
through a single Finalizer thread. Cleaner is strictly better but can still choke if cleanable
resources are churned at high rate.$g$, $g$<ul>
    <li>Replace any remaining <code>finalize()</code> methods with try-with-resources + <code>Cleaner</code>
    <li>Avoid creating Cleaner-backed resources in tight loops - pool or reuse where feasible
    <li>If the flame graph shows Finalizer thread saturation, migrate the offending class to explicit close()
    <li>Confirm no library you depend on still relies on finalization (common in legacy JDBC drivers and old NIO wrappers)
</ul>$g$, TIMESTAMPTZ '2026-01-01 00:00:00+00'),
    ('exec-jit', $g$JIT Compilation$g$, true, true, 'EXECUTION_SAMPLE', 'JIT', 'SAMPLES', 'JVM', 'SINGLE_MATCH', 0.15, 0.2, '{"anchor":{"type":"AnyOf","of":[{"type":"Predicate","op":"EQUALS","value":"JavaThread::thread_main_inner"},{"type":"Predicate","op":"EQUALS","value":"CompileBroker::compiler_thread_loop"}]}}', '{"eventSource":"ASYNC_PROFILER"}', $g$JIT compilation$g$, $g$The JIT compilation ratio is a metric that helps to understand how much time the JVM spends on
compiling the code. The higher the ratio, the more time the JVM spends on the compilation process.
This can lead to higher CPU usage and longer response times. <br>
There are multiple reasons why the ratio can be higher than expected value:
<ul>
    <li>higher compilation time is usually observed at the start of the application (Warm-up period)
    <li>higher compilation time can be caused by the change of the application's behavior
    (JIT needs to compile or recompile additional classes/methods)
    <li>high number of deoptimizations can lead to the recompilation of the code
</ul>$g$, $g$To reduce the JIT compilation ratio, you can try to:
<ul>
    <li>capture a longer recording of the application with JIT compilation activity in the steady state
    <li>check the application's behavior and try to reduce the number of recompilations, or loading new classes
    <li>check the number of deoptimizations
</ul>$g$, TIMESTAMPTZ '2026-01-01 00:00:00+00'),
    ('exec-deopt', $g$JIT Deoptimization$g$, true, true, 'EXECUTION_SAMPLE', 'JIT', 'SAMPLES', 'JVM', 'SINGLE_MATCH', 0.03, 0.05, '{"anchor":{"type":"Predicate","op":"PREFIX","value":"Deoptimization::"}}', '{"eventSource":"ASYNC_PROFILER"}', $g$JIT deoptimization$g$, $g$JIT deoptimization occurs when the JVM invalidates previously compiled code because an assumption
made during compilation turns out to be wrong. This forces the JVM to fall back to interpreted
execution and potentially recompile the code. Frequent deoptimization causes CPU overhead and
can indicate unstable code patterns such as polymorphic call sites or speculative optimizations
that keep failing.$g$, $g$<ul>
    <li>Check for class hierarchy changes that invalidate compiled code
    <li>Look for uncommon traps (type checks, null checks, range checks) that trigger deoptimization
    <li>Reduce polymorphism at hot call sites (prefer monomorphic or bimorphic dispatch)
    <li>Use -XX:+TraceDeoptimization to identify specific deoptimization reasons
</ul>$g$, TIMESTAMPTZ '2026-01-01 00:00:00+00'),
    ('exec-safepoint', $g$Safepoint Overhead$g$, true, true, 'EXECUTION_SAMPLE', 'JIT', 'SAMPLES', 'JVM', 'SINGLE_MATCH', 0.03, 0.05, '{"anchor":{"type":"Predicate","op":"PREFIX","value":"SafepointSynchronize::"}}', '{"eventSource":"ASYNC_PROFILER"}', $g$safepoint synchronization$g$, $g$VM safepoints are points where all Java threads are stopped to allow the JVM to perform
operations that require a consistent view of the heap (e.g., GC, deoptimization, biased lock
revocation). The synchronization overhead includes the time to bring all threads to a safepoint
and the time spent at the safepoint itself. Excessive safepoint overhead indicates the JVM is
spending too much time coordinating thread stops.$g$, $g$<ul>
    <li>Identify the VM operations triggering safepoints (GC, deoptimization, etc.)
    <li>Check for counted loops without safepoint polls (use -XX:+UseCountedLoopSafepoints if needed)
    <li>Reduce the frequency of full GC pauses which require safepoints
    <li>Use -Xlog:safepoint to diagnose safepoint timing details
</ul>$g$, TIMESTAMPTZ '2026-01-01 00:00:00+00'),
    ('exec-vmop', $g$VM Operation Overhead$g$, true, true, 'EXECUTION_SAMPLE', 'JIT', 'SAMPLES', 'JVM', 'SINGLE_MATCH', 0.03, 0.05, '{"anchor":{"type":"Predicate","op":"EQUALS","value":"VM_Operation::evaluate"}}', '{"eventSource":"ASYNC_PROFILER"}', $g$VM operations$g$, $g$VM operations are internal JVM tasks that often require a safepoint to execute. Beyond GC
(which is tracked separately), these include operations like class redefinition, biased lock
revocation, thread dump generation, and code cache management. High VM operation overhead
indicates the JVM is spending significant time on internal housekeeping rather than running
application code.$g$, $g$<ul>
    <li>Check if monitoring tools are triggering frequent thread dumps or heap inspections
    <li>Review biased locking usage (disabled by default since JDK 15)
    <li>Look for frequent class redefinition from agents or instrumentation frameworks
    <li>Examine if code cache is being flushed frequently
</ul>$g$, TIMESTAMPTZ '2026-01-01 00:00:00+00'),
    ('exec-gc-serial', $g$Serial GC$g$, true, true, 'EXECUTION_SAMPLE', 'GARBAGE_COLLECTION', 'SAMPLES', 'JVM', 'SINGLE_MATCH', 0.07, 0.1, '{"anchor":{"type":"Predicate","op":"EQUALS","value":"VM_GenCollectForAllocation::doit"}}', '{"eventSource":"ASYNC_PROFILER","garbageCollectorType":"SERIAL"}', $g$the Serial GC$g$, $g$The GC ratio is a metric that helps to understand how much time the JVM spends on
collecting the garbage. The higher the ratio, the more time the JVM spends in GC.
This can lead to higher CPU usage and longer response times because of the
stop-the-world nature of Serial GC.
<ul>
    <li>high allocation rate caused by creating new objects
    <li>promotion of the objects to old generation
</ul>$g$, $g$CPU is not the main problem of Serial GC, but it very often leads to very high response time.
Try to check this out:
<ul>
    <li>SerialGC is convenient for very small heaps and devices, isn't SerialGC just misconfiguration (it might be a JVM default in smaller containers)?
    <li>check whether whether young generation is big enough to handle short-lived objects
    <li>consider a different GC if the response time is the application's issue
</ul>$g$, TIMESTAMPTZ '2026-01-01 00:00:00+00'),
    ('exec-gc-parallel', $g$Parallel GC$g$, true, true, 'EXECUTION_SAMPLE', 'GARBAGE_COLLECTION', 'SAMPLES', 'JVM', 'SINGLE_MATCH', 0.07, 0.1, '{"anchor":{"type":"Predicate","op":"EQUALS","value":"Thread::call_run"},"traversal":{"type":"Descend","steps":[{"type":"ByName","frameName":"WorkerThread::run"},{"type":"ByMatcher","base":{"type":"Predicate","op":"EQUALS","value":"VM_Operation::evaluate"},"target":{"type":"Predicate","op":"PREFIX","value":"VM_ParallelGC"}}]}}', '{"eventSource":"ASYNC_PROFILER","garbageCollectorType":"PARALLEL"}', $g$the Parallel GC$g$, $g$The GC ratio is a metric that helps to understand how much time the JVM spends on collecting the
garbage. The higher the ratio, the more time the JVM spends in GC, which can lead to higher CPU usage
and longer response times.$g$, $g$<ul>
    <li>check the allocation rate of the application; increasing the heap size can reduce the number of GC cycles
    <li>check the promotion rate and the young generation sizing
    <li>consider a different GC if the response time is the application's issue
</ul>$g$, TIMESTAMPTZ '2026-01-01 00:00:00+00'),
    ('exec-gc-g1', $g$G1 GC$g$, true, true, 'EXECUTION_SAMPLE', 'GARBAGE_COLLECTION', 'SAMPLES', 'JVM', 'SINGLE_MATCH', 0.07, 0.1, '{"anchor":{"type":"Predicate","op":"EQUALS","value":"Thread::call_run"},"traversal":{"type":"Descend","steps":[{"type":"ByName","frameName":"ConcurrentGCThread::run"},{"type":"ByName","frameName":"WorkerThread::run"},{"type":"ByMatcher","base":{"type":"Predicate","op":"EQUALS","value":"VM_Operation::evaluate"},"target":{"type":"Predicate","op":"PREFIX","value":"VM_G1"}}]}}', '{"eventSource":"ASYNC_PROFILER","garbageCollectorType":"G1"}', $g$the G1GC$g$, $g$The GC ratio is a metric that helps to understand how much time the JVM spends on
collecting the garbage. The higher the ratio, the more time the JVM spends in GC.
G1GC is a concurrent garbage collector but it still has a stop-the-world phase
which can lead to longer response times. <br>
There are multiple reasons why the ratio can be higher than expected value:
<ul>
    <li>high allocation rate caused by creating new objects
    <li>promotion of the objects to old generation
    <li>IHOP (Initiating Heap Occupancy Percent) is not set correctly and concurrency marking start to often
    <li>too small heap size for the given application workload
    <li>huge number of cross-region references leading to higher remember sets scanning and processing
</ul>$g$, $g$<ul>
    <li>check the allocation rate of the application, increasing the heap size can help to reduce the number of GC
    <li>check the promotion rate, bigger young generation can reduce the number of concurrent cycles
    <li>check the IHOP value, it should be set to the value that allows the GC to finish the marking phase before the heap is full
</ul>$g$, TIMESTAMPTZ '2026-01-01 00:00:00+00'),
    ('exec-gc-shenandoah', $g$Shenandoah GC$g$, true, true, 'EXECUTION_SAMPLE', 'GARBAGE_COLLECTION', 'SAMPLES', 'JVM', 'SINGLE_MATCH', 0.07, 0.1, '{"anchor":{"type":"Predicate","op":"EQUALS","value":"Thread::call_run"},"traversal":{"type":"Descend","steps":[{"type":"ByName","frameName":"ConcurrentGCThread::run"},{"type":"ByName","frameName":"WorkerThread::run"},{"type":"ByMatcher","base":{"type":"Predicate","op":"EQUALS","value":"VM_Operation::evaluate"},"target":{"type":"Predicate","op":"PREFIX","value":"VM_Shenandoah"}}]}}', '{"eventSource":"ASYNC_PROFILER","garbageCollectorType":"SHENANDOAH"}', $g$the Shenandoah GC$g$, $g$The GC ratio is a metric that helps to understand how much time the JVM spends on collecting the
garbage. The higher the ratio, the more time the JVM spends in GC, which can lead to higher CPU usage
and longer response times.$g$, $g$<ul>
    <li>check the allocation rate of the application; increasing the heap size can reduce the number of GC cycles
    <li>check the promotion rate and the young generation sizing
    <li>consider a different GC if the response time is the application's issue
</ul>$g$, TIMESTAMPTZ '2026-01-01 00:00:00+00'),
    ('exec-gc-z', $g$Z GC$g$, true, true, 'EXECUTION_SAMPLE', 'GARBAGE_COLLECTION', 'SAMPLES', 'JVM', 'SINGLE_MATCH', 0.07, 0.1, '{"anchor":{"type":"Predicate","op":"EQUALS","value":"Thread::call_run"},"traversal":{"type":"Descend","steps":[{"type":"ByName","frameName":"ConcurrentGCThread::run"},{"type":"ByName","frameName":"WorkerThread::run"},{"type":"ByMatcher","base":{"type":"Predicate","op":"EQUALS","value":"VM_Operation::evaluate"},"target":{"type":"Predicate","op":"PREFIX","value":"VM_XOperation"}}]}}', '{"eventSource":"ASYNC_PROFILER","garbageCollectorType":"Z"}', $g$the Z GC$g$, $g$The GC ratio is a metric that helps to understand how much time the JVM spends on collecting the
garbage. The higher the ratio, the more time the JVM spends in GC, which can lead to higher CPU usage
and longer response times.$g$, $g$<ul>
    <li>check the allocation rate of the application; increasing the heap size can reduce the number of GC cycles
    <li>check the promotion rate and the young generation sizing
    <li>consider a different GC if the response time is the application's issue
</ul>$g$, TIMESTAMPTZ '2026-01-01 00:00:00+00'),
    ('exec-gc-zgen', $g$Z Generational GC$g$, true, true, 'EXECUTION_SAMPLE', 'GARBAGE_COLLECTION', 'SAMPLES', 'JVM', 'SINGLE_MATCH', 0.07, 0.1, '{"anchor":{"type":"Predicate","op":"EQUALS","value":"Thread::call_run"},"traversal":{"type":"Descend","steps":[{"type":"ByName","frameName":"ConcurrentGCThread::run"},{"type":"ByName","frameName":"WorkerThread::run"},{"type":"ByMatcher","base":{"type":"Predicate","op":"EQUALS","value":"VM_Operation::evaluate"},"target":{"type":"Predicate","op":"PREFIX","value":"VM_ZOperation"}}]}}', '{"eventSource":"ASYNC_PROFILER","garbageCollectorType":"ZGENERATIONAL"}', $g$the Z Generational GC$g$, $g$The GC ratio is a metric that helps to understand how much time the JVM spends on collecting the
garbage. The higher the ratio, the more time the JVM spends in GC, which can lead to higher CPU usage
and longer response times.$g$, $g$<ul>
    <li>check the allocation rate of the application; increasing the heap size can reduce the number of GC cycles
    <li>check the promotion rate and the young generation sizing
    <li>consider a different GC if the response time is the application's issue
</ul>$g$, TIMESTAMPTZ '2026-01-01 00:00:00+00');

INSERT INTO guardian_guards (guard_id, name, enabled, built_in, group_kind, category, result_type, target_frame, matching_type, info_threshold, warning_threshold, matcher_spec, preconditions, summary_noun, explanation, solution, created_at) VALUES
    ('cputime-logback', $g$Logback CPU Overhead$g$, true, true, 'CPU_TIME_SAMPLE', 'APPLICATION', 'SAMPLES', 'JAVA', 'FULL_MATCH', 0.02, 0.03, '{"anchor":{"type":"Predicate","op":"PREFIX","value":"ch.qos.logback"}}', NULL, $g$the logging$g$, $g$Extensive logging can cause significant overhead in allocation and CPU usage. Some application
with a lower number of transactions/requests can log even detailed information, however, when the
application is under a heavy load, the logging can become a bottleneck.$g$, $g$<ul>
    <li>Consider whether the logging is necessary, and difference between the logging levels (INFO, DEBUG, ..)
    <li>Use templating for the log messages to avoid the string concatenation (even if the log level is not enabled)
</ul>$g$, TIMESTAMPTZ '2026-01-01 00:00:00+00'),
    ('cputime-log4j', $g$Log4j CPU Overhead$g$, true, true, 'CPU_TIME_SAMPLE', 'APPLICATION', 'SAMPLES', 'JAVA', 'FULL_MATCH', 0.02, 0.03, '{"anchor":{"type":"Predicate","op":"PREFIX","value":"org.apache.logging.log4j."}}', NULL, $g$the logging$g$, $g$Extensive logging using Log4j2 can cause significant overhead in allocation and CPU usage.
Some applications with a lower number of transactions/requests can log even detailed information,
however, when the application is under a heavy load, the logging can become a bottleneck.$g$, $g$<ul>
    <li>Consider whether the logging is necessary, and difference between the logging levels (INFO, DEBUG, ..)
    <li>Use parameterized messages to avoid string concatenation when the log level is not enabled
    <li>Consider using asynchronous appenders to reduce the impact on the application thread
</ul>$g$, TIMESTAMPTZ '2026-01-01 00:00:00+00'),
    ('cputime-hashmap', $g$HashMap Collisions$g$, true, true, 'CPU_TIME_SAMPLE', 'APPLICATION', 'SAMPLES', 'JAVA', 'FULL_MATCH', 0.02, 0.04, '{"anchor":{"type":"AnyOf","of":[{"type":"Predicate","op":"SUFFIX","value":"Map$TreeNode#getTreeNode"},{"type":"Predicate","op":"SUFFIX","value":"Map$TreeNode#putTreeVal"},{"type":"Predicate","op":"SUFFIX","value":"Map$TreeNode#findTreeNode"}]}}', NULL, $g$hash collisions$g$, $g$Key collision is a common issue in HashMaps. It can lead to performance degradation
because the time complexity of the operations increases from O(1) to O(n). The keys that have
the same hashcode are stored in a linked list (it often uses a balanced tree for a bucket with
a small number of collisions - JEP 180, then the linked list takes a place).
The more collisions, the longer the time to find the key because the list
needs to be iterated one item after another.
<br>
The Guard keeps an eye only on hash maps that are implemented in OpenJDK.$g$, $g$The solution is to reduce the number of collisions. It can be achieved by:
<ul>
    <li>Implementing better hashCode() and equals() methods
    <li>Using a different data structure
</ul>$g$, TIMESTAMPTZ '2026-01-01 00:00:00+00'),
    ('cputime-regex', $g$Regular Expressions$g$, true, true, 'CPU_TIME_SAMPLE', 'APPLICATION', 'SAMPLES', 'JAVA', 'FULL_MATCH', 0.02, 0.04, '{"anchor":{"type":"AnyOf","of":[{"type":"Predicate","op":"PREFIX","value":"java.util.regex.Matcher"},{"type":"Predicate","op":"PREFIX","value":"java.util.regex.Pattern"}]}}', NULL, $g$regular expressions$g$, $g$Regular expressions are used to match patterns in strings. They are powerful, but they can be slow.
Sometimes its inevitable to use them, but they should be used with caution.
This guard should help you to identify the places where regular expressions are used too often.
<br>
Especially <b>java.util.Pattern#compile</b> should be used only once for a pattern and avoid compiling it
every iteration.$g$, $g$<ul>
    <li>Identify the places where the regular expressions are used too often and can be replaced by a different approach
    <li>Be careful to String operation where regular expressions are used under the hood
    <li>Use <b>java.util.Pattern#compile</b> only once for a pattern and avoid compiling it every iteration
    <li>Optimize the regular expressions
</ul>$g$, TIMESTAMPTZ '2026-01-01 00:00:00+00'),
    ('cputime-classloading', $g$Class Loading Overhead$g$, true, true, 'CPU_TIME_SAMPLE', 'APPLICATION', 'SELF_SAMPLES', 'JAVA', 'FULL_MATCH', 0.03, 0.05, '{"anchor":{"type":"AnyOf","of":[{"type":"Predicate","op":"PREFIX","value":"java.lang.ClassLoader#loadClass"},{"type":"Predicate","op":"PREFIX","value":"java.lang.Class#forName"}]}}', NULL, $g$class loading activity$g$, $g$Excessive dynamic class loading can cause significant CPU overhead. This often occurs with
heavy use of reflection, dynamic proxies, bytecode generation frameworks (e.g., CGLIB, Byte Buddy),
or OSGi-style modular class loading. Each class load involves I/O, verification, and linking steps
that consume CPU cycles.$g$, $g$<ul>
    <li>Check if classes are being loaded repeatedly instead of being cached
    <li>Reduce reliance on reflection-heavy frameworks or configure them to cache generated classes
    <li>Consider using static compilation or ahead-of-time class generation where possible
    <li>Review usage of Class.forName() and ensure it is not called in hot paths
</ul>$g$, TIMESTAMPTZ '2026-01-01 00:00:00+00'),
    ('cputime-reflection', $g$Reflection Overhead$g$, true, true, 'CPU_TIME_SAMPLE', 'APPLICATION', 'SELF_SAMPLES', 'JAVA', 'FULL_MATCH', 0.03, 0.05, '{"anchor":{"type":"AnyOf","of":[{"type":"Predicate","op":"PREFIX","value":"java.lang.reflect."},{"type":"Predicate","op":"PREFIX","value":"jdk.internal.reflect."}]}}', NULL, $g$reflection activity$g$, $g$Heavy use of Java reflection (Method.invoke, Field access, Constructor.newInstance) can cause
significant CPU overhead. Reflection bypasses compile-time optimizations and requires additional
security checks, type resolution, and boxing/unboxing of arguments at runtime.$g$, $g$<ul>
    <li>Replace reflection with direct method calls or MethodHandle where possible
    <li>Cache Method, Field, and Constructor objects instead of looking them up repeatedly
    <li>Consider using code generation (e.g., Byte Buddy) to replace hot reflective paths
    <li>Review serialization frameworks that may use reflection internally
</ul>$g$, TIMESTAMPTZ '2026-01-01 00:00:00+00'),
    ('cputime-serialization', $g$Java Serialization Overhead$g$, true, true, 'CPU_TIME_SAMPLE', 'APPLICATION', 'SELF_SAMPLES', 'JAVA', 'FULL_MATCH', 0.03, 0.05, '{"anchor":{"type":"AnyOf","of":[{"type":"Predicate","op":"PREFIX","value":"java.io.ObjectOutputStream#writeObject"},{"type":"Predicate","op":"PREFIX","value":"java.io.ObjectInputStream#readObject"}]}}', NULL, $g$Java serialization activity$g$, $g$Java's built-in serialization (ObjectOutputStream/ObjectInputStream) is known to be slow and
resource-intensive. It uses reflection, generates significant temporary objects, and performs
complex graph traversal. This overhead is especially noticeable in high-throughput systems
that serialize/deserialize data frequently.$g$, $g$<ul>
    <li>Consider replacing Java serialization with faster alternatives (Protocol Buffers, Kryo, Jackson, etc.)
    <li>Implement Externalizable instead of Serializable for fine-grained control
    <li>Reduce the size and complexity of serialized object graphs
    <li>Cache serialized forms when the same objects are serialized repeatedly
</ul>$g$, TIMESTAMPTZ '2026-01-01 00:00:00+00'),
    ('cputime-xml', $g$XML Parsing Overhead$g$, true, true, 'CPU_TIME_SAMPLE', 'APPLICATION', 'SAMPLES', 'JAVA', 'FULL_MATCH', 0.03, 0.05, '{"anchor":{"type":"AnyOf","of":[{"type":"Predicate","op":"PREFIX","value":"javax.xml."},{"type":"Predicate","op":"PREFIX","value":"com.sun.xml."},{"type":"Predicate","op":"PREFIX","value":"com.sun.org.apache.xerces."}]}}', NULL, $g$XML parsing activity$g$, $g$XML processing (DOM, SAX, StAX) can be CPU-intensive, especially when parsing large documents
or when XML parsing is used in hot paths. DOM parsing in particular loads the entire document
into memory and builds an object tree, which is both CPU and memory intensive.$g$, $g$<ul>
    <li>Use StAX (streaming) parsing instead of DOM for large documents
    <li>Consider replacing XML with a more efficient format (JSON, Protocol Buffers)
    <li>Cache parsed XML documents if the same content is parsed repeatedly
    <li>Use XML binding frameworks (JAXB) with pre-compiled schemas for better performance
</ul>$g$, TIMESTAMPTZ '2026-01-01 00:00:00+00'),
    ('cputime-json', $g$JSON Processing Overhead$g$, true, true, 'CPU_TIME_SAMPLE', 'APPLICATION', 'SAMPLES', 'JAVA', 'FULL_MATCH', 0.03, 0.05, '{"anchor":{"type":"AnyOf","of":[{"type":"Predicate","op":"PREFIX","value":"com.fasterxml.jackson."},{"type":"Predicate","op":"PREFIX","value":"com.google.gson."}]}}', NULL, $g$JSON processing activity$g$, $g$JSON parsing and serialization (Jackson, Gson) can consume significant CPU when processing
large payloads or when used in high-throughput paths. Object mapping through reflection,
type resolution, and string processing all contribute to the overhead.$g$, $g$<ul>
    <li>Use streaming APIs (JsonParser/JsonGenerator) instead of tree model for large payloads
    <li>Reuse ObjectMapper instances (they are thread-safe in Jackson)
    <li>Consider using Jackson afterburner/blackbird module for faster serialization
    <li>Reduce the size of serialized objects by excluding unnecessary fields
</ul>$g$, TIMESTAMPTZ '2026-01-01 00:00:00+00'),
    ('cputime-exception', $g$Exception Overhead$g$, true, true, 'CPU_TIME_SAMPLE', 'APPLICATION', 'SAMPLES', 'JAVA', 'FULL_MATCH', 0.03, 0.05, '{"anchor":{"type":"AnyOf","of":[{"type":"Predicate","op":"SUFFIX","value":"Throwable#<init>"},{"type":"Predicate","op":"SUFFIX","value":"Throwable#fillInStackTrace"}]}}', NULL, $g$exception creation activity$g$, $g$Excessive exception creation and handling can cause significant CPU overhead. The most expensive
part is fillInStackTrace(), which walks the entire call stack to capture the stack trace.
Using exceptions for control flow or creating exceptions in hot paths leads to performance degradation.$g$, $g$<ul>
    <li>Avoid using exceptions for control flow (e.g., catching NumberFormatException instead of validating input)
    <li>Consider pre-allocated singleton exceptions with overridden fillInStackTrace() for expected errors
    <li>Use Optional or error codes instead of exceptions for expected failure cases
    <li>Check the flamegraph to identify which exception types are created most frequently
</ul>$g$, TIMESTAMPTZ '2026-01-01 00:00:00+00'),
    ('cputime-stringconcat', $g$String Concatenation Overhead$g$, true, true, 'CPU_TIME_SAMPLE', 'APPLICATION', 'SAMPLES', 'JAVA', 'FULL_MATCH', 0.03, 0.05, '{"anchor":{"type":"AnyOf","of":[{"type":"Predicate","op":"PREFIX","value":"java.lang.StringBuilder#append"},{"type":"Predicate","op":"PREFIX","value":"java.lang.StringBuffer#append"}]}}', NULL, $g$string concatenation activity$g$, $g$Excessive string building via StringBuilder or StringBuffer can indicate inefficient string
construction patterns, such as concatenation in loops. Each append may trigger internal array
resizing and copying, which consumes CPU cycles and generates garbage.$g$, $g$<ul>
    <li>Pre-size StringBuilder with an estimated capacity to avoid repeated resizing
    <li>Use String.join() or StringJoiner for joining collections
    <li>Consider using String.format() or MessageFormat for complex string construction
    <li>Replace StringBuffer with StringBuilder if thread-safety is not required
</ul>$g$, TIMESTAMPTZ '2026-01-01 00:00:00+00'),
    ('cputime-threadsync', $g$Thread Synchronization Overhead$g$, true, true, 'CPU_TIME_SAMPLE', 'APPLICATION', 'SAMPLES', 'ALL', 'FULL_MATCH', 0.03, 0.05, '{"anchor":{"type":"AnyOf","of":[{"type":"Predicate","op":"PREFIX","value":"jdk.internal.misc.Unsafe#park"},{"type":"Predicate","op":"SUFFIX","value":"ObjectMonitor::enter"}]}}', NULL, $g$thread synchronization activity$g$, $g$Heavy lock contention visible on the CPU profile indicates threads are spending time
competing for locks. This includes both Java-level synchronized blocks (ObjectMonitor::enter)
and explicit Lock implementations using Unsafe.park. High contention reduces parallelism
and can become a scalability bottleneck.$g$, $g$<ul>
    <li>Reduce the scope of synchronized blocks to the minimum necessary
    <li>Consider using concurrent data structures (ConcurrentHashMap, etc.) instead of synchronized collections
    <li>Use ReadWriteLock when reads are more frequent than writes
    <li>Consider lock-free algorithms or atomic operations for simple counters and flags
</ul>$g$, TIMESTAMPTZ '2026-01-01 00:00:00+00'),
    ('cputime-crypto', $g$Crypto/TLS Overhead$g$, true, true, 'CPU_TIME_SAMPLE', 'APPLICATION', 'SAMPLES', 'JAVA', 'FULL_MATCH', 0.03, 0.05, '{"anchor":{"type":"AnyOf","of":[{"type":"Predicate","op":"PREFIX","value":"javax.crypto."},{"type":"Predicate","op":"PREFIX","value":"sun.security.ssl."},{"type":"Predicate","op":"PREFIX","value":"sun.security.provider."}]}}', NULL, $g$crypto/TLS activity$g$, $g$Cryptographic operations and TLS processing can consume significant CPU, especially during
TLS handshakes, bulk encryption/decryption, and certificate validation. This is often seen
in applications with many short-lived HTTPS connections or heavy payload encryption.$g$, $g$<ul>
    <li>Enable TLS session resumption and session tickets to reduce handshake overhead
    <li>Use HTTP/2 or connection pooling to reduce the number of TLS handshakes
    <li>Consider hardware acceleration for crypto operations (AES-NI is usually enabled by default)
    <li>Review cipher suite configuration - prefer modern, efficient algorithms
</ul>$g$, TIMESTAMPTZ '2026-01-01 00:00:00+00'),
    ('cputime-compress', $g$Compression Overhead$g$, true, true, 'CPU_TIME_SAMPLE', 'APPLICATION', 'SAMPLES', 'JAVA', 'FULL_MATCH', 0.03, 0.05, '{"anchor":{"type":"AnyOf","of":[{"type":"Predicate","op":"PREFIX","value":"java.util.zip."},{"type":"Predicate","op":"PREFIX","value":"java.util.jar."}]}}', NULL, $g$compression/decompression activity$g$, $g$Compression and decompression (ZIP, GZIP, JAR) operations are CPU-intensive by nature.
This overhead is often seen in applications that compress HTTP responses, process ZIP archives,
or load resources from JAR files frequently.$g$, $g$<ul>
    <li>Consider whether compression is necessary for all responses/data
    <li>Adjust compression level (lower levels trade compression ratio for speed)
    <li>Cache compressed/decompressed results when the same data is processed repeatedly
    <li>Consider using faster compression algorithms (e.g., LZ4, Snappy) instead of DEFLATE
</ul>$g$, TIMESTAMPTZ '2026-01-01 00:00:00+00'),
    ('cputime-finalizer', $g$Finalizer/Cleaner Overhead$g$, true, true, 'CPU_TIME_SAMPLE', 'APPLICATION', 'SAMPLES', 'JAVA', 'FULL_MATCH', 0.01, 0.03, '{"anchor":{"type":"AnyOf","of":[{"type":"Predicate","op":"PREFIX","value":"java.lang.ref.Finalizer"},{"type":"Predicate","op":"PREFIX","value":"jdk.internal.ref.Cleaner"},{"type":"Predicate","op":"PREFIX","value":"java.lang.ref.ReferenceQueue"}]}}', NULL, $g$finalization / cleaner machinery$g$, $g$CPU spent inside java.lang.ref.Finalizer / jdk.internal.ref.Cleaner signals that reference-queue
processing is material. Finalization in particular has been a well-known performance hazard since
JDK 1.0 - every finalizable object costs two GC cycles (one to queue, one after run()) and serialises
through a single Finalizer thread. Cleaner is strictly better but can still choke if cleanable
resources are churned at high rate.$g$, $g$<ul>
    <li>Replace any remaining <code>finalize()</code> methods with try-with-resources + <code>Cleaner</code>
    <li>Avoid creating Cleaner-backed resources in tight loops - pool or reuse where feasible
    <li>If the flame graph shows Finalizer thread saturation, migrate the offending class to explicit close()
    <li>Confirm no library you depend on still relies on finalization (common in legacy JDBC drivers and old NIO wrappers)
</ul>$g$, TIMESTAMPTZ '2026-01-01 00:00:00+00'),
    ('cputime-jit', $g$JIT Compilation$g$, true, true, 'CPU_TIME_SAMPLE', 'JIT', 'SAMPLES', 'JVM', 'SINGLE_MATCH', 0.15, 0.2, '{"anchor":{"type":"AnyOf","of":[{"type":"Predicate","op":"EQUALS","value":"JavaThread::thread_main_inner"},{"type":"Predicate","op":"EQUALS","value":"CompileBroker::compiler_thread_loop"}]}}', '{"eventSource":"ASYNC_PROFILER"}', $g$JIT compilation$g$, $g$The JIT compilation ratio is a metric that helps to understand how much time the JVM spends on
compiling the code. The higher the ratio, the more time the JVM spends on the compilation process.
This can lead to higher CPU usage and longer response times. <br>
There are multiple reasons why the ratio can be higher than expected value:
<ul>
    <li>higher compilation time is usually observed at the start of the application (Warm-up period)
    <li>higher compilation time can be caused by the change of the application's behavior
    (JIT needs to compile or recompile additional classes/methods)
    <li>high number of deoptimizations can lead to the recompilation of the code
</ul>$g$, $g$To reduce the JIT compilation ratio, you can try to:
<ul>
    <li>capture a longer recording of the application with JIT compilation activity in the steady state
    <li>check the application's behavior and try to reduce the number of recompilations, or loading new classes
    <li>check the number of deoptimizations
</ul>$g$, TIMESTAMPTZ '2026-01-01 00:00:00+00'),
    ('cputime-deopt', $g$JIT Deoptimization$g$, true, true, 'CPU_TIME_SAMPLE', 'JIT', 'SAMPLES', 'JVM', 'SINGLE_MATCH', 0.03, 0.05, '{"anchor":{"type":"Predicate","op":"PREFIX","value":"Deoptimization::"}}', '{"eventSource":"ASYNC_PROFILER"}', $g$JIT deoptimization$g$, $g$JIT deoptimization occurs when the JVM invalidates previously compiled code because an assumption
made during compilation turns out to be wrong. This forces the JVM to fall back to interpreted
execution and potentially recompile the code. Frequent deoptimization causes CPU overhead and
can indicate unstable code patterns such as polymorphic call sites or speculative optimizations
that keep failing.$g$, $g$<ul>
    <li>Check for class hierarchy changes that invalidate compiled code
    <li>Look for uncommon traps (type checks, null checks, range checks) that trigger deoptimization
    <li>Reduce polymorphism at hot call sites (prefer monomorphic or bimorphic dispatch)
    <li>Use -XX:+TraceDeoptimization to identify specific deoptimization reasons
</ul>$g$, TIMESTAMPTZ '2026-01-01 00:00:00+00'),
    ('cputime-safepoint', $g$Safepoint Overhead$g$, true, true, 'CPU_TIME_SAMPLE', 'JIT', 'SAMPLES', 'JVM', 'SINGLE_MATCH', 0.03, 0.05, '{"anchor":{"type":"Predicate","op":"PREFIX","value":"SafepointSynchronize::"}}', '{"eventSource":"ASYNC_PROFILER"}', $g$safepoint synchronization$g$, $g$VM safepoints are points where all Java threads are stopped to allow the JVM to perform
operations that require a consistent view of the heap (e.g., GC, deoptimization, biased lock
revocation). The synchronization overhead includes the time to bring all threads to a safepoint
and the time spent at the safepoint itself. Excessive safepoint overhead indicates the JVM is
spending too much time coordinating thread stops.$g$, $g$<ul>
    <li>Identify the VM operations triggering safepoints (GC, deoptimization, etc.)
    <li>Check for counted loops without safepoint polls (use -XX:+UseCountedLoopSafepoints if needed)
    <li>Reduce the frequency of full GC pauses which require safepoints
    <li>Use -Xlog:safepoint to diagnose safepoint timing details
</ul>$g$, TIMESTAMPTZ '2026-01-01 00:00:00+00'),
    ('cputime-vmop', $g$VM Operation Overhead$g$, true, true, 'CPU_TIME_SAMPLE', 'JIT', 'SAMPLES', 'JVM', 'SINGLE_MATCH', 0.03, 0.05, '{"anchor":{"type":"Predicate","op":"EQUALS","value":"VM_Operation::evaluate"}}', '{"eventSource":"ASYNC_PROFILER"}', $g$VM operations$g$, $g$VM operations are internal JVM tasks that often require a safepoint to execute. Beyond GC
(which is tracked separately), these include operations like class redefinition, biased lock
revocation, thread dump generation, and code cache management. High VM operation overhead
indicates the JVM is spending significant time on internal housekeeping rather than running
application code.$g$, $g$<ul>
    <li>Check if monitoring tools are triggering frequent thread dumps or heap inspections
    <li>Review biased locking usage (disabled by default since JDK 15)
    <li>Look for frequent class redefinition from agents or instrumentation frameworks
    <li>Examine if code cache is being flushed frequently
</ul>$g$, TIMESTAMPTZ '2026-01-01 00:00:00+00'),
    ('cputime-gc-serial', $g$Serial GC$g$, true, true, 'CPU_TIME_SAMPLE', 'GARBAGE_COLLECTION', 'SAMPLES', 'JVM', 'SINGLE_MATCH', 0.07, 0.1, '{"anchor":{"type":"Predicate","op":"EQUALS","value":"VM_GenCollectForAllocation::doit"}}', '{"eventSource":"ASYNC_PROFILER","garbageCollectorType":"SERIAL"}', $g$the Serial GC$g$, $g$The GC ratio is a metric that helps to understand how much time the JVM spends on
collecting the garbage. The higher the ratio, the more time the JVM spends in GC.
This can lead to higher CPU usage and longer response times because of the
stop-the-world nature of Serial GC.
<ul>
    <li>high allocation rate caused by creating new objects
    <li>promotion of the objects to old generation
</ul>$g$, $g$CPU is not the main problem of Serial GC, but it very often leads to very high response time.
Try to check this out:
<ul>
    <li>SerialGC is convenient for very small heaps and devices, isn't SerialGC just misconfiguration (it might be a JVM default in smaller containers)?
    <li>check whether whether young generation is big enough to handle short-lived objects
    <li>consider a different GC if the response time is the application's issue
</ul>$g$, TIMESTAMPTZ '2026-01-01 00:00:00+00'),
    ('cputime-gc-parallel', $g$Parallel GC$g$, true, true, 'CPU_TIME_SAMPLE', 'GARBAGE_COLLECTION', 'SAMPLES', 'JVM', 'SINGLE_MATCH', 0.07, 0.1, '{"anchor":{"type":"Predicate","op":"EQUALS","value":"Thread::call_run"},"traversal":{"type":"Descend","steps":[{"type":"ByName","frameName":"WorkerThread::run"},{"type":"ByMatcher","base":{"type":"Predicate","op":"EQUALS","value":"VM_Operation::evaluate"},"target":{"type":"Predicate","op":"PREFIX","value":"VM_ParallelGC"}}]}}', '{"eventSource":"ASYNC_PROFILER","garbageCollectorType":"PARALLEL"}', $g$the Parallel GC$g$, $g$The GC ratio is a metric that helps to understand how much time the JVM spends on collecting the
garbage. The higher the ratio, the more time the JVM spends in GC, which can lead to higher CPU usage
and longer response times.$g$, $g$<ul>
    <li>check the allocation rate of the application; increasing the heap size can reduce the number of GC cycles
    <li>check the promotion rate and the young generation sizing
    <li>consider a different GC if the response time is the application's issue
</ul>$g$, TIMESTAMPTZ '2026-01-01 00:00:00+00'),
    ('cputime-gc-g1', $g$G1 GC$g$, true, true, 'CPU_TIME_SAMPLE', 'GARBAGE_COLLECTION', 'SAMPLES', 'JVM', 'SINGLE_MATCH', 0.07, 0.1, '{"anchor":{"type":"Predicate","op":"EQUALS","value":"Thread::call_run"},"traversal":{"type":"Descend","steps":[{"type":"ByName","frameName":"ConcurrentGCThread::run"},{"type":"ByName","frameName":"WorkerThread::run"},{"type":"ByMatcher","base":{"type":"Predicate","op":"EQUALS","value":"VM_Operation::evaluate"},"target":{"type":"Predicate","op":"PREFIX","value":"VM_G1"}}]}}', '{"eventSource":"ASYNC_PROFILER","garbageCollectorType":"G1"}', $g$the G1GC$g$, $g$The GC ratio is a metric that helps to understand how much time the JVM spends on
collecting the garbage. The higher the ratio, the more time the JVM spends in GC.
G1GC is a concurrent garbage collector but it still has a stop-the-world phase
which can lead to longer response times. <br>
There are multiple reasons why the ratio can be higher than expected value:
<ul>
    <li>high allocation rate caused by creating new objects
    <li>promotion of the objects to old generation
    <li>IHOP (Initiating Heap Occupancy Percent) is not set correctly and concurrency marking start to often
    <li>too small heap size for the given application workload
    <li>huge number of cross-region references leading to higher remember sets scanning and processing
</ul>$g$, $g$<ul>
    <li>check the allocation rate of the application, increasing the heap size can help to reduce the number of GC
    <li>check the promotion rate, bigger young generation can reduce the number of concurrent cycles
    <li>check the IHOP value, it should be set to the value that allows the GC to finish the marking phase before the heap is full
</ul>$g$, TIMESTAMPTZ '2026-01-01 00:00:00+00'),
    ('cputime-gc-shenandoah', $g$Shenandoah GC$g$, true, true, 'CPU_TIME_SAMPLE', 'GARBAGE_COLLECTION', 'SAMPLES', 'JVM', 'SINGLE_MATCH', 0.07, 0.1, '{"anchor":{"type":"Predicate","op":"EQUALS","value":"Thread::call_run"},"traversal":{"type":"Descend","steps":[{"type":"ByName","frameName":"ConcurrentGCThread::run"},{"type":"ByName","frameName":"WorkerThread::run"},{"type":"ByMatcher","base":{"type":"Predicate","op":"EQUALS","value":"VM_Operation::evaluate"},"target":{"type":"Predicate","op":"PREFIX","value":"VM_Shenandoah"}}]}}', '{"eventSource":"ASYNC_PROFILER","garbageCollectorType":"SHENANDOAH"}', $g$the Shenandoah GC$g$, $g$The GC ratio is a metric that helps to understand how much time the JVM spends on collecting the
garbage. The higher the ratio, the more time the JVM spends in GC, which can lead to higher CPU usage
and longer response times.$g$, $g$<ul>
    <li>check the allocation rate of the application; increasing the heap size can reduce the number of GC cycles
    <li>check the promotion rate and the young generation sizing
    <li>consider a different GC if the response time is the application's issue
</ul>$g$, TIMESTAMPTZ '2026-01-01 00:00:00+00'),
    ('cputime-gc-z', $g$Z GC$g$, true, true, 'CPU_TIME_SAMPLE', 'GARBAGE_COLLECTION', 'SAMPLES', 'JVM', 'SINGLE_MATCH', 0.07, 0.1, '{"anchor":{"type":"Predicate","op":"EQUALS","value":"Thread::call_run"},"traversal":{"type":"Descend","steps":[{"type":"ByName","frameName":"ConcurrentGCThread::run"},{"type":"ByName","frameName":"WorkerThread::run"},{"type":"ByMatcher","base":{"type":"Predicate","op":"EQUALS","value":"VM_Operation::evaluate"},"target":{"type":"Predicate","op":"PREFIX","value":"VM_XOperation"}}]}}', '{"eventSource":"ASYNC_PROFILER","garbageCollectorType":"Z"}', $g$the Z GC$g$, $g$The GC ratio is a metric that helps to understand how much time the JVM spends on collecting the
garbage. The higher the ratio, the more time the JVM spends in GC, which can lead to higher CPU usage
and longer response times.$g$, $g$<ul>
    <li>check the allocation rate of the application; increasing the heap size can reduce the number of GC cycles
    <li>check the promotion rate and the young generation sizing
    <li>consider a different GC if the response time is the application's issue
</ul>$g$, TIMESTAMPTZ '2026-01-01 00:00:00+00'),
    ('cputime-gc-zgen', $g$Z Generational GC$g$, true, true, 'CPU_TIME_SAMPLE', 'GARBAGE_COLLECTION', 'SAMPLES', 'JVM', 'SINGLE_MATCH', 0.07, 0.1, '{"anchor":{"type":"Predicate","op":"EQUALS","value":"Thread::call_run"},"traversal":{"type":"Descend","steps":[{"type":"ByName","frameName":"ConcurrentGCThread::run"},{"type":"ByName","frameName":"WorkerThread::run"},{"type":"ByMatcher","base":{"type":"Predicate","op":"EQUALS","value":"VM_Operation::evaluate"},"target":{"type":"Predicate","op":"PREFIX","value":"VM_ZOperation"}}]}}', '{"eventSource":"ASYNC_PROFILER","garbageCollectorType":"ZGENERATIONAL"}', $g$the Z Generational GC$g$, $g$The GC ratio is a metric that helps to understand how much time the JVM spends on collecting the
garbage. The higher the ratio, the more time the JVM spends in GC, which can lead to higher CPU usage
and longer response times.$g$, $g$<ul>
    <li>check the allocation rate of the application; increasing the heap size can reduce the number of GC cycles
    <li>check the promotion rate and the young generation sizing
    <li>consider a different GC if the response time is the application's issue
</ul>$g$, TIMESTAMPTZ '2026-01-01 00:00:00+00');

INSERT INTO guardian_guards (guard_id, name, enabled, built_in, group_kind, category, result_type, target_frame, matching_type, info_threshold, warning_threshold, matcher_spec, preconditions, summary_noun, explanation, solution, created_at) VALUES
    ('alloc-logback', $g$Logback Allocation Overhead$g$, true, true, 'ALLOCATION', 'APPLICATION', 'WEIGHT', 'JAVA', 'FULL_MATCH', 0.07, 0.1, '{"anchor":{"type":"Predicate","op":"PREFIX","value":"ch.qos.logback"}}', NULL, $g$the logging$g$, $g$Extensive logging can cause significant overhead in allocation and CPU usage. Some application
with a lower number of transactions/requests can log even detailed information, however, when the
application is under a heavy load, the logging can become a bottleneck.$g$, $g$<ul>
    <li>Consider whether the logging is necessary, and difference between the logging levels (INFO, DEBUG, ..)
    <li>Use templating for the log messages to avoid the string concatenation (even if the log level is not enabled)
</ul>$g$, TIMESTAMPTZ '2026-01-01 00:00:00+00'),
    ('alloc-log4j', $g$Log4j Allocation Overhead$g$, true, true, 'ALLOCATION', 'APPLICATION', 'WEIGHT', 'JAVA', 'FULL_MATCH', 0.03, 0.05, '{"anchor":{"type":"Predicate","op":"PREFIX","value":"org.apache.logging.log4j."}}', NULL, $g$Log4j logging$g$, $g$Log4j2 logging operations allocate log event objects, formatted message strings, and
various internal buffers. Under heavy load, these allocations can create significant
GC pressure, especially with synchronous appenders and complex log message formatting.$g$, $g$<ul>
    <li>Use parameterized messages to avoid unnecessary string formatting
    <li>Enable garbage-free logging mode in Log4j2 configuration
    <li>Consider using asynchronous appenders to reduce allocation pressure on application threads
    <li>Review logging levels and reduce unnecessary log statements in hot paths
</ul>$g$, TIMESTAMPTZ '2026-01-01 00:00:00+00'),
    ('alloc-hashmap', $g$HashMap Collision Allocations$g$, true, true, 'ALLOCATION', 'APPLICATION', 'WEIGHT', 'JAVA', 'FULL_MATCH', 0.03, 0.05, '{"anchor":{"type":"AnyOf","of":[{"type":"Predicate","op":"SUFFIX","value":"Map$TreeNode#getTreeNode"},{"type":"Predicate","op":"SUFFIX","value":"Map$TreeNode#putTreeVal"},{"type":"Predicate","op":"SUFFIX","value":"Map$TreeNode#findTreeNode"}]}}', NULL, $g$hash collisions$g$, $g$HashMap key collisions cause TreeNode allocations when buckets are converted from linked lists
to balanced trees (JEP 180). These allocations indicate the hash function produces collisions,
degrading both CPU performance and increasing memory pressure.$g$, $g$<ul>
    <li>Implement better hashCode() methods to reduce collisions
    <li>Consider using a different data structure if collisions are unavoidable
    <li>Pre-size HashMaps to reduce rehashing and tree conversions
</ul>$g$, TIMESTAMPTZ '2026-01-01 00:00:00+00'),
    ('alloc-regex', $g$Regex Allocations$g$, true, true, 'ALLOCATION', 'APPLICATION', 'WEIGHT', 'JAVA', 'FULL_MATCH', 0.03, 0.05, '{"anchor":{"type":"AnyOf","of":[{"type":"Predicate","op":"PREFIX","value":"java.util.regex.Matcher"},{"type":"Predicate","op":"PREFIX","value":"java.util.regex.Pattern"}]}}', NULL, $g$regex operations$g$, $g$Regular expression operations allocate temporary objects during pattern matching, including
internal state arrays and match result objects. Compiling patterns repeatedly (Pattern.compile)
is particularly allocation-heavy.$g$, $g$<ul>
    <li>Cache compiled Pattern instances instead of recompiling them
    <li>Replace regex with simple string operations where possible
    <li>Be careful with String methods that use regex under the hood (split, replaceAll)
</ul>$g$, TIMESTAMPTZ '2026-01-01 00:00:00+00'),
    ('alloc-stringconcat', $g$String Concatenation Allocations$g$, true, true, 'ALLOCATION', 'APPLICATION', 'WEIGHT', 'JAVA', 'FULL_MATCH', 0.03, 0.05, '{"anchor":{"type":"Predicate","op":"PREFIX","value":"java.lang.StringBuilder"}}', NULL, $g$string concatenation$g$, $g$StringBuilder operations allocate internal char/byte arrays that grow dynamically. String
concatenation in loops is particularly wasteful as each iteration may create a new StringBuilder
and its backing array, generating significant garbage.$g$, $g$<ul>
    <li>Pre-size StringBuilder with an estimated capacity to avoid array resizing
    <li>Reuse StringBuilder instances in hot paths (reset with setLength(0))
    <li>Use String.join() or StringJoiner for joining collections
</ul>$g$, TIMESTAMPTZ '2026-01-01 00:00:00+00'),
    ('alloc-exception', $g$Exception Allocations$g$, true, true, 'ALLOCATION', 'APPLICATION', 'WEIGHT', 'JAVA', 'FULL_MATCH', 0.03, 0.05, '{"anchor":{"type":"Predicate","op":"SUFFIX","value":"Throwable#<init>"}}', NULL, $g$exception creation$g$, $g$Exception objects and their stack traces consume memory. Each exception creation allocates
the exception object itself plus a StackTraceElement array captured by fillInStackTrace().
Frequent exception creation generates significant garbage, especially with deep call stacks.$g$, $g$<ul>
    <li>Avoid using exceptions for control flow
    <li>Consider pre-allocated singleton exceptions with overridden fillInStackTrace() for expected errors
    <li>Use Optional or error codes instead of exceptions for expected failure cases
</ul>$g$, TIMESTAMPTZ '2026-01-01 00:00:00+00'),
    ('alloc-boxing', $g$Autoboxing Allocations$g$, true, true, 'ALLOCATION', 'APPLICATION', 'WEIGHT', 'JAVA', 'FULL_MATCH', 0.03, 0.05, '{"anchor":{"type":"AnyOf","of":[{"type":"Predicate","op":"SUFFIX","value":"Integer#valueOf"},{"type":"Predicate","op":"SUFFIX","value":"Long#valueOf"},{"type":"Predicate","op":"SUFFIX","value":"Double#valueOf"},{"type":"Predicate","op":"SUFFIX","value":"Boolean#valueOf"}]}}', NULL, $g$autoboxing$g$, $g$Autoboxing (converting primitives to wrapper objects via Integer.valueOf, Long.valueOf, etc.)
creates heap allocations. While small values are cached (e.g., Integer -128 to 127), values
outside the cache range create new objects on each conversion. This is common when using
primitives with generic collections (List<Integer>) or streams.$g$, $g$<ul>
    <li>Use primitive-specialized collections (e.g., Eclipse Collections, HPPC, or Koloboke)
    <li>Replace Stream operations with primitive streams (IntStream, LongStream, DoubleStream)
    <li>Avoid generic collections with primitive values in hot paths
    <li>Consider increasing the Integer cache range with -XX:AutoBoxCacheMax if applicable
</ul>$g$, TIMESTAMPTZ '2026-01-01 00:00:00+00'),
    ('alloc-collection', $g$Collection Resizing Allocations$g$, true, true, 'ALLOCATION', 'APPLICATION', 'WEIGHT', 'JAVA', 'FULL_MATCH', 0.03, 0.05, '{"anchor":{"type":"Predicate","op":"PREFIX","value":"java.util.Arrays#copyOf"}}', NULL, $g$collection resizing$g$, $g$Collections like ArrayList and HashMap use internal arrays that grow dynamically. When the
array is full, Arrays.copyOf is called to allocate a larger array and copy existing elements.
Frequent resizing indicates collections are being created with insufficient initial capacity,
leading to wasted allocations and copying overhead.$g$, $g$<ul>
    <li>Pre-size collections with an estimated capacity (e.g., new ArrayList<>(expectedSize))
    <li>Use List.of() or Map.of() for immutable collections of known size
    <li>Consider using ArrayDeque instead of ArrayList for queue/stack patterns
    <li>Profile which collection types are resizing most frequently
</ul>$g$, TIMESTAMPTZ '2026-01-01 00:00:00+00');

INSERT INTO guardian_guards (guard_id, name, enabled, built_in, group_kind, category, result_type, target_frame, matching_type, info_threshold, warning_threshold, matcher_spec, preconditions, summary_noun, explanation, solution, created_at) VALUES
    ('wall-logback', $g$Logback Wall-Clock Overhead$g$, true, true, 'WALL_CLOCK', 'APPLICATION', 'SAMPLES', 'JAVA', 'FULL_MATCH', 0.02, 0.03, '{"anchor":{"type":"Predicate","op":"PREFIX","value":"ch.qos.logback"}}', NULL, $g$the logging$g$, $g$Extensive logging can cause significant overhead in allocation and CPU usage. Some application
with a lower number of transactions/requests can log even detailed information, however, when the
application is under a heavy load, the logging can become a bottleneck.$g$, $g$<ul>
    <li>Consider whether the logging is necessary, and difference between the logging levels (INFO, DEBUG, ..)
    <li>Use templating for the log messages to avoid the string concatenation (even if the log level is not enabled)
</ul>$g$, TIMESTAMPTZ '2026-01-01 00:00:00+00'),
    ('wall-log4j', $g$Log4j Wall-Clock Overhead$g$, true, true, 'WALL_CLOCK', 'APPLICATION', 'SAMPLES', 'JAVA', 'FULL_MATCH', 0.02, 0.03, '{"anchor":{"type":"Predicate","op":"PREFIX","value":"org.apache.logging.log4j."}}', NULL, $g$the logging$g$, $g$Extensive logging using Log4j2 can cause significant overhead in allocation and CPU usage.
Some applications with a lower number of transactions/requests can log even detailed information,
however, when the application is under a heavy load, the logging can become a bottleneck.$g$, $g$<ul>
    <li>Consider whether the logging is necessary, and difference between the logging levels (INFO, DEBUG, ..)
    <li>Use parameterized messages to avoid string concatenation when the log level is not enabled
    <li>Consider using asynchronous appenders to reduce the impact on the application thread
</ul>$g$, TIMESTAMPTZ '2026-01-01 00:00:00+00'),
    ('wall-hashmap', $g$HashMap Collisions$g$, true, true, 'WALL_CLOCK', 'APPLICATION', 'SAMPLES', 'JAVA', 'FULL_MATCH', 0.02, 0.04, '{"anchor":{"type":"AnyOf","of":[{"type":"Predicate","op":"SUFFIX","value":"Map$TreeNode#getTreeNode"},{"type":"Predicate","op":"SUFFIX","value":"Map$TreeNode#putTreeVal"},{"type":"Predicate","op":"SUFFIX","value":"Map$TreeNode#findTreeNode"}]}}', NULL, $g$hash collisions$g$, $g$Key collision is a common issue in HashMaps. It can lead to performance degradation
because the time complexity of the operations increases from O(1) to O(n). The keys that have
the same hashcode are stored in a linked list (it often uses a balanced tree for a bucket with
a small number of collisions - JEP 180, then the linked list takes a place).
The more collisions, the longer the time to find the key because the list
needs to be iterated one item after another.
<br>
The Guard keeps an eye only on hash maps that are implemented in OpenJDK.$g$, $g$The solution is to reduce the number of collisions. It can be achieved by:
<ul>
    <li>Implementing better hashCode() and equals() methods
    <li>Using a different data structure
</ul>$g$, TIMESTAMPTZ '2026-01-01 00:00:00+00'),
    ('wall-regex', $g$Regular Expressions$g$, true, true, 'WALL_CLOCK', 'APPLICATION', 'SAMPLES', 'JAVA', 'FULL_MATCH', 0.02, 0.04, '{"anchor":{"type":"AnyOf","of":[{"type":"Predicate","op":"PREFIX","value":"java.util.regex.Matcher"},{"type":"Predicate","op":"PREFIX","value":"java.util.regex.Pattern"}]}}', NULL, $g$regular expressions$g$, $g$Regular expressions are used to match patterns in strings. They are powerful, but they can be slow.
Sometimes its inevitable to use them, but they should be used with caution.
This guard should help you to identify the places where regular expressions are used too often.
<br>
Especially <b>java.util.Pattern#compile</b> should be used only once for a pattern and avoid compiling it
every iteration.$g$, $g$<ul>
    <li>Identify the places where the regular expressions are used too often and can be replaced by a different approach
    <li>Be careful to String operation where regular expressions are used under the hood
    <li>Use <b>java.util.Pattern#compile</b> only once for a pattern and avoid compiling it every iteration
    <li>Optimize the regular expressions
</ul>$g$, TIMESTAMPTZ '2026-01-01 00:00:00+00'),
    ('wall-reflection', $g$Reflection Wall-Clock Overhead$g$, true, true, 'WALL_CLOCK', 'APPLICATION', 'SELF_SAMPLES', 'JAVA', 'FULL_MATCH', 0.03, 0.05, '{"anchor":{"type":"AnyOf","of":[{"type":"Predicate","op":"PREFIX","value":"java.lang.reflect."},{"type":"Predicate","op":"PREFIX","value":"jdk.internal.reflect."}]}}', NULL, $g$reflection activity$g$, $g$Heavy use of Java reflection (Method.invoke, Field access, Constructor.newInstance) can cause
significant CPU overhead. Reflection bypasses compile-time optimizations and requires additional
security checks, type resolution, and boxing/unboxing of arguments at runtime.$g$, $g$<ul>
    <li>Replace reflection with direct method calls or MethodHandle where possible
    <li>Cache Method, Field, and Constructor objects instead of looking them up repeatedly
    <li>Consider using code generation (e.g., Byte Buddy) to replace hot reflective paths
    <li>Review serialization frameworks that may use reflection internally
</ul>$g$, TIMESTAMPTZ '2026-01-01 00:00:00+00'),
    ('wall-exception', $g$Exception Wall-Clock Overhead$g$, true, true, 'WALL_CLOCK', 'APPLICATION', 'SAMPLES', 'JAVA', 'FULL_MATCH', 0.03, 0.05, '{"anchor":{"type":"AnyOf","of":[{"type":"Predicate","op":"SUFFIX","value":"Throwable#<init>"},{"type":"Predicate","op":"SUFFIX","value":"Throwable#fillInStackTrace"}]}}', NULL, $g$exception creation activity$g$, $g$Excessive exception creation and handling can cause significant CPU overhead. The most expensive
part is fillInStackTrace(), which walks the entire call stack to capture the stack trace.
Using exceptions for control flow or creating exceptions in hot paths leads to performance degradation.$g$, $g$<ul>
    <li>Avoid using exceptions for control flow (e.g., catching NumberFormatException instead of validating input)
    <li>Consider pre-allocated singleton exceptions with overridden fillInStackTrace() for expected errors
    <li>Use Optional or error codes instead of exceptions for expected failure cases
    <li>Check the flamegraph to identify which exception types are created most frequently
</ul>$g$, TIMESTAMPTZ '2026-01-01 00:00:00+00'),
    ('wall-crypto', $g$Crypto/TLS Wall-Clock Overhead$g$, true, true, 'WALL_CLOCK', 'APPLICATION', 'SAMPLES', 'JAVA', 'FULL_MATCH', 0.03, 0.05, '{"anchor":{"type":"AnyOf","of":[{"type":"Predicate","op":"PREFIX","value":"javax.crypto."},{"type":"Predicate","op":"PREFIX","value":"sun.security.ssl."},{"type":"Predicate","op":"PREFIX","value":"sun.security.provider."}]}}', NULL, $g$crypto/TLS activity$g$, $g$Cryptographic operations and TLS processing can consume significant CPU, especially during
TLS handshakes, bulk encryption/decryption, and certificate validation. This is often seen
in applications with many short-lived HTTPS connections or heavy payload encryption.$g$, $g$<ul>
    <li>Enable TLS session resumption and session tickets to reduce handshake overhead
    <li>Use HTTP/2 or connection pooling to reduce the number of TLS handshakes
    <li>Consider hardware acceleration for crypto operations (AES-NI is usually enabled by default)
    <li>Review cipher suite configuration - prefer modern, efficient algorithms
</ul>$g$, TIMESTAMPTZ '2026-01-01 00:00:00+00'),
    ('wall-classloading', $g$Class Loading Wall-Clock Overhead$g$, true, true, 'WALL_CLOCK', 'APPLICATION', 'SELF_SAMPLES', 'JAVA', 'FULL_MATCH', 0.03, 0.05, '{"anchor":{"type":"AnyOf","of":[{"type":"Predicate","op":"PREFIX","value":"java.lang.ClassLoader#loadClass"},{"type":"Predicate","op":"PREFIX","value":"java.lang.Class#forName"}]}}', NULL, $g$class loading activity$g$, $g$Excessive dynamic class loading can cause significant CPU overhead. This often occurs with
heavy use of reflection, dynamic proxies, bytecode generation frameworks (e.g., CGLIB, Byte Buddy),
or OSGi-style modular class loading. Each class load involves I/O, verification, and linking steps
that consume CPU cycles.$g$, $g$<ul>
    <li>Check if classes are being loaded repeatedly instead of being cached
    <li>Reduce reliance on reflection-heavy frameworks or configure them to cache generated classes
    <li>Consider using static compilation or ahead-of-time class generation where possible
    <li>Review usage of Class.forName() and ensure it is not called in hot paths
</ul>$g$, TIMESTAMPTZ '2026-01-01 00:00:00+00'),
    ('wall-threadsync', $g$Thread Synchronization Wall-Clock Overhead$g$, true, true, 'WALL_CLOCK', 'APPLICATION', 'SAMPLES', 'ALL', 'FULL_MATCH', 0.03, 0.05, '{"anchor":{"type":"AnyOf","of":[{"type":"Predicate","op":"PREFIX","value":"jdk.internal.misc.Unsafe#park"},{"type":"Predicate","op":"SUFFIX","value":"ObjectMonitor::enter"}]}}', NULL, $g$thread synchronization activity$g$, $g$Heavy lock contention visible on the CPU profile indicates threads are spending time
competing for locks. This includes both Java-level synchronized blocks (ObjectMonitor::enter)
and explicit Lock implementations using Unsafe.park. High contention reduces parallelism
and can become a scalability bottleneck.$g$, $g$<ul>
    <li>Reduce the scope of synchronized blocks to the minimum necessary
    <li>Consider using concurrent data structures (ConcurrentHashMap, etc.) instead of synchronized collections
    <li>Use ReadWriteLock when reads are more frequent than writes
    <li>Consider lock-free algorithms or atomic operations for simple counters and flags
</ul>$g$, TIMESTAMPTZ '2026-01-01 00:00:00+00');

INSERT INTO guardian_guards (guard_id, name, enabled, built_in, group_kind, category, result_type, target_frame, matching_type, info_threshold, warning_threshold, matcher_spec, preconditions, summary_noun, explanation, solution, created_at) VALUES
    ('blocking-dbpool', $g$DB Connection Pool Blocking$g$, true, true, 'BLOCKING', 'APPLICATION', 'WEIGHT', 'JAVA', 'FULL_MATCH', 0.03, 0.05, '{"anchor":{"type":"AnyOf","of":[{"type":"Predicate","op":"PREFIX","value":"com.zaxxer.hikari."},{"type":"Predicate","op":"PREFIX","value":"org.apache.commons.dbcp2."},{"type":"Predicate","op":"PREFIX","value":"com.mchange.v2.c3p0."},{"type":"Predicate","op":"PREFIX","value":"org.apache.tomcat.jdbc.pool."}]}}', NULL, $g$database connections$g$, $g$Threads are blocked waiting to acquire a database connection from the connection pool.
This indicates the pool is exhausted - all connections are in use and new requests must wait.
This is a common bottleneck in database-heavy applications under load.$g$, $g$<ul>
    <li>Increase the connection pool size if the database can handle more connections
    <li>Optimize slow database queries to release connections faster
    <li>Reduce connection hold time by closing connections promptly (use try-with-resources)
    <li>Consider connection pool metrics to understand peak usage and wait times
</ul>$g$, TIMESTAMPTZ '2026-01-01 00:00:00+00'),
    ('blocking-lock', $g$Lock Contention Blocking$g$, true, true, 'BLOCKING', 'APPLICATION', 'WEIGHT', 'JAVA', 'FULL_MATCH', 0.03, 0.05, '{"anchor":{"type":"AnyOf","of":[{"type":"Predicate","op":"PREFIX","value":"java.util.concurrent.locks.ReentrantLock"},{"type":"Predicate","op":"PREFIX","value":"java.util.concurrent.locks.AbstractQueuedSynchronizer"}]}}', NULL, $g$locks$g$, $g$Threads are blocked waiting to acquire ReentrantLock or other AbstractQueuedSynchronizer-based
locks. This indicates lock contention where multiple threads compete for the same lock,
reducing parallelism and increasing response times.$g$, $g$<ul>
    <li>Reduce the scope of locked sections to minimize hold time
    <li>Consider using ReadWriteLock when reads are more frequent than writes
    <li>Use concurrent data structures instead of explicit locking where possible
    <li>Consider lock-free algorithms or StampedLock for read-heavy workloads
</ul>$g$, TIMESTAMPTZ '2026-01-01 00:00:00+00'),
    ('blocking-io', $g$I/O Blocking$g$, true, true, 'BLOCKING', 'APPLICATION', 'WEIGHT', 'JAVA', 'FULL_MATCH', 0.03, 0.05, '{"anchor":{"type":"AnyOf","of":[{"type":"Predicate","op":"PREFIX","value":"java.net.Socket"},{"type":"Predicate","op":"PREFIX","value":"sun.nio.ch."},{"type":"Predicate","op":"PREFIX","value":"java.io.FileInputStream"},{"type":"Predicate","op":"PREFIX","value":"java.io.FileOutputStream"}]}}', NULL, $g$I/O operations$g$, $g$Threads are blocked waiting for I/O operations to complete, including socket reads/writes
and file I/O. This is expected for I/O-bound applications, but excessive blocking can
indicate slow network connections, disk I/O bottlenecks, or missing timeouts.$g$, $g$<ul>
    <li>Use non-blocking I/O (NIO) or asynchronous I/O for high-concurrency scenarios
    <li>Configure appropriate timeouts for socket operations
    <li>Use buffered I/O streams to reduce the number of system calls
    <li>Consider using virtual threads (Java 21+) for I/O-bound workloads
</ul>$g$, TIMESTAMPTZ '2026-01-01 00:00:00+00'),
    ('blocking-http', $g$HTTP Client Blocking$g$, true, true, 'BLOCKING', 'APPLICATION', 'WEIGHT', 'JAVA', 'FULL_MATCH', 0.03, 0.05, '{"anchor":{"type":"AnyOf","of":[{"type":"Predicate","op":"PREFIX","value":"java.net.http."},{"type":"Predicate","op":"PREFIX","value":"org.apache.http."},{"type":"Predicate","op":"PREFIX","value":"okhttp3."}]}}', NULL, $g$HTTP responses$g$, $g$Threads are blocked waiting for HTTP client operations to complete. This includes waiting
for connection establishment, TLS handshakes, and response data. High blocking time indicates
slow downstream services or insufficient connection pooling.$g$, $g$<ul>
    <li>Use asynchronous HTTP client APIs to avoid blocking threads
    <li>Configure connection pooling and keep-alive to reuse connections
    <li>Set appropriate connection and read timeouts
    <li>Consider using circuit breakers for unreliable downstream services
</ul>$g$, TIMESTAMPTZ '2026-01-01 00:00:00+00'),
    ('blocking-logback', $g$Logback Blocking$g$, true, true, 'BLOCKING', 'APPLICATION', 'WEIGHT', 'JAVA', 'FULL_MATCH', 0.03, 0.05, '{"anchor":{"type":"Predicate","op":"PREFIX","value":"ch.qos.logback"}}', NULL, $g$Logback logging$g$, $g$Threads are blocked waiting on Logback synchronous appenders. By default, Logback uses
synchronous appenders that hold a lock while writing to the output. Under heavy logging
load, this creates contention as multiple threads compete for the appender lock.$g$, $g$<ul>
    <li>Use AsyncAppender to decouple log production from log writing
    <li>Reduce logging verbosity in hot paths
    <li>Consider using a lock-free logging framework or appender
    <li>Review whether all log statements are necessary at the current level
</ul>$g$, TIMESTAMPTZ '2026-01-01 00:00:00+00'),
    ('blocking-log4j', $g$Log4j Blocking$g$, true, true, 'BLOCKING', 'APPLICATION', 'WEIGHT', 'JAVA', 'FULL_MATCH', 0.03, 0.05, '{"anchor":{"type":"Predicate","op":"PREFIX","value":"org.apache.logging.log4j."}}', NULL, $g$Log4j logging$g$, $g$Threads are blocked waiting on Log4j2 synchronous appenders. When using synchronous logging,
the appender holds a lock during I/O operations, causing thread contention under heavy
logging load.$g$, $g$<ul>
    <li>Use AsyncLogger or AsyncAppender for non-blocking log writing
    <li>Configure the LMAX Disruptor-based async logging for best performance
    <li>Reduce logging verbosity in hot paths
    <li>Consider using RandomAccessFileAppender for better I/O performance
</ul>$g$, TIMESTAMPTZ '2026-01-01 00:00:00+00');

