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

package pbouda.jeffrey.profile.heapdump.analyzer;

import org.netbeans.lib.profiler.heap.Heap;
import org.netbeans.lib.profiler.heap.Instance;
import org.netbeans.lib.profiler.heap.JavaClass;
import org.netbeans.modules.profiler.oql.engine.api.OQLEngine;
import org.netbeans.modules.profiler.oql.engine.api.OQLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.profile.heapdump.model.OQLQueryRequest;
import pbouda.jeffrey.profile.heapdump.model.OQLQueryResult;
import pbouda.jeffrey.profile.heapdump.model.OQLResultEntry;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Executes OQL queries against a heap dump with support for pagination.
 */
public class OQLQueryExecutor {

    private static final Logger LOG = LoggerFactory.getLogger(OQLQueryExecutor.class);
    private static final int MAX_RESULTS = 10000;

    /**
     * Execute an OQL query against the heap.
     */
    public OQLQueryResult execute(Heap heap, OQLQueryRequest request) {
        long startTime = System.currentTimeMillis();

        try {
            OQLEngine engine = new OQLEngine(heap);
            List<Object> collected = collectResults(engine, request);

            int effectiveLimit = Math.min(request.limit(), MAX_RESULTS);
            boolean hasMore = collected.size() > effectiveLimit;
            List<Object> results = hasMore ? collected.subList(0, effectiveLimit) : collected;

            InstanceValueFormatter formatter = new InstanceValueFormatter(engine);
            List<OQLResultEntry> entries = results.stream()
                    .map(obj -> toEntry(obj, engine, formatter, request.includeRetainedSize()))
                    .toList();

            long executionTime = System.currentTimeMillis() - startTime;
            LOG.debug("OQL query executed: query={} results={} timeMs={}",
                    truncate(request.query(), 100), entries.size(), executionTime);

            return OQLQueryResult.success(entries, collected.size(), hasMore, executionTime);

        } catch (OQLException e) {
            LOG.warn("OQL query failed: query={} error={}", truncate(request.query(), 100), e.getMessage());
            return OQLQueryResult.error("Query error: " + e.getMessage(), elapsed(startTime));
        } catch (Exception e) {
            LOG.error("Unexpected error executing OQL query: query={}", truncate(request.query(), 100), e);
            return OQLQueryResult.error("Unexpected error: " + e.getMessage(), elapsed(startTime));
        }
    }

    /**
     * Validate an OQL query without executing it.
     */
    public String validateQuery(Heap heap, String query) {
        try {
            new OQLEngine(heap).executeQuery(query, obj -> true);
            return null;
        } catch (OQLException e) {
            return e.getMessage();
        } catch (Exception e) {
            return "Unexpected error: " + e.getMessage();
        }
    }

    private List<Object> collectResults(OQLEngine engine, OQLQueryRequest request) throws OQLException {
        List<Object> collected = new ArrayList<>();
        AtomicInteger count = new AtomicInteger(0);
        int effectiveLimit = Math.min(request.limit(), MAX_RESULTS);
        int offset = request.offset();

        engine.executeQuery(request.query(), obj -> {
            if (count.incrementAndGet() <= offset) {
                return false; // skip
            }
            if (collected.size() > effectiveLimit) {
                return true; // stop
            }
            collected.add(obj);
            return false;
        });

        return collected;
    }

    @SuppressWarnings("unchecked")
    private OQLResultEntry toEntry(Object obj, OQLEngine engine, InstanceValueFormatter formatter, boolean includeRetained) {
        if (obj instanceof Instance instance) {
            String value = formatter.formatAsString(instance);
            Long retained = includeRetained ? calculateRetainedSize(engine, instance.getInstanceId()) : null;

            return retained != null
                    ? OQLResultEntry.ofInstanceWithRetained(instance.getInstanceId(), instance.getJavaClass().getName(), value, instance.getSize(), retained)
                    : OQLResultEntry.ofInstance(instance.getInstanceId(), instance.getJavaClass().getName(), value, instance.getSize());
        }
        if (obj instanceof JavaClass javaClass) {
            String className = javaClass.getName();
            long instanceCount = javaClass.getInstancesCount();
            long totalSize = javaClass.getAllInstancesSize();
            String value = String.format("instances=%d, size=%d", instanceCount, totalSize);
            return OQLResultEntry.ofJavaClass(javaClass.getJavaClassId(), className, value, totalSize);
        }
        // Handle JavaScript objects from OQL (ScriptObjectMirror implements Map)
        if (obj instanceof Map<?, ?> map) {
            String value = formatJsObject((Map<String, Object>) map, formatter, 0);
            return OQLResultEntry.ofValue(value);
        }
        return OQLResultEntry.ofValue(obj != null ? obj.toString() : "null");
    }

    private static final int MAX_FORMAT_DEPTH = 3;

    /**
     * Format a JavaScript object (from OQL) as a readable string.
     * Recursively formats nested objects and Instance references with depth limiting.
     */
    @SuppressWarnings("unchecked")
    private String formatJsObject(Map<String, Object> map, InstanceValueFormatter formatter, int depth) {
        if (depth > MAX_FORMAT_DEPTH) {
            return "{...}";
        }
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String key = entry.getKey();
            // Skip internal JavaScript properties and functions
            if (key.startsWith("__") || key.equals("constructor")) {
                continue;
            }
            if (!first) sb.append(", ");
            first = false;
            sb.append(key).append(": ");
            sb.append(formatJsValue(entry.getValue(), formatter, depth + 1));
        }
        sb.append("}");
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private String formatJsValue(Object value, InstanceValueFormatter formatter, int depth) {
        if (value == null) {
            return "null";
        }
        if (depth > MAX_FORMAT_DEPTH) {
            return "...";
        }
        try {
            if (value instanceof Instance instance) {
                return formatter.formatAsString(instance);
            }
            if (value instanceof JavaClass javaClass) {
                return javaClass.getName();
            }
            if (value instanceof Map<?, ?> nestedMap) {
                return formatJsObject((Map<String, Object>) nestedMap, formatter, depth);
            }
            if (value instanceof List<?> list) {
                StringBuilder sb = new StringBuilder("[");
                for (int i = 0; i < Math.min(list.size(), 10); i++) {
                    if (i > 0) sb.append(", ");
                    sb.append(formatJsValue(list.get(i), formatter, depth + 1));
                }
                if (list.size() > 10) {
                    sb.append(", ... (").append(list.size() - 10).append(" more)");
                }
                sb.append("]");
                return sb.toString();
            }
            // For Numbers, Strings, Booleans - use toString directly
            if (value instanceof Number || value instanceof Boolean || value instanceof String) {
                return value.toString();
            }
            // For other types, get class name to avoid calling potentially problematic toString()
            return value.getClass().getSimpleName() + "@" + Integer.toHexString(System.identityHashCode(value));
        } catch (Exception e) {
            LOG.trace("Failed to format JS value: type={}", value.getClass().getName(), e);
            return "<error>";
        }
    }

    private long calculateRetainedSize(OQLEngine engine, long objectId) {
        AtomicLong size = new AtomicLong(0);
        try {
            engine.executeQuery("select rsizeof(heap.findObject(" + objectId + "))", result -> {
                if (result instanceof Number n) size.set(n.longValue());
                return true;
            });
        } catch (OQLException e) {
            LOG.debug("Failed to calculate retained size: objectId={} error={}", objectId, e.getMessage());
        }
        return size.get();
    }

    private long elapsed(long startTime) {
        return System.currentTimeMillis() - startTime;
    }

    private String truncate(String str, int maxLen) {
        return str.length() > maxLen ? str.substring(0, maxLen) + "..." : str;
    }
}
