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
import org.netbeans.lib.profiler.heap.PrimitiveArrayInstance;
import org.netbeans.modules.profiler.oql.engine.api.OQLEngine;
import org.netbeans.modules.profiler.oql.engine.api.OQLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.profile.heapdump.model.HeapThreadInfo;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Analyzes Thread objects in a heap dump.
 */
public class ThreadAnalyzer {

    private static final Logger LOG = LoggerFactory.getLogger(ThreadAnalyzer.class);
    private static final String THREAD_CLASS = "java.lang.Thread";

    /**
     * Extract information about all Thread instances in the heap.
     *
     * @param heap the loaded heap dump
     * @return list of thread information
     */
    public List<HeapThreadInfo> analyze(Heap heap) {
        return analyze(heap, false);
    }

    /**
     * Extract information about all Thread instances in the heap.
     *
     * @param heap                the loaded heap dump
     * @param includeRetainedSize whether to calculate retained size (expensive operation)
     * @return list of thread information
     */
    @SuppressWarnings("unchecked")
    public List<HeapThreadInfo> analyze(Heap heap, boolean includeRetainedSize) {
        List<HeapThreadInfo> threads = new ArrayList<>();

        JavaClass threadClass = heap.getJavaClassByName(THREAD_CLASS);
        if (threadClass == null) {
            LOG.warn("Thread class not found in heap dump");
            return threads;
        }

        OQLEngine engine = includeRetainedSize ? new OQLEngine(heap) : null;

        List<Instance> instances = (List<Instance>) threadClass.getInstances();
        for (Instance instance : instances) {
            try {
                HeapThreadInfo threadInfo = extractThreadInfo(instance, engine);
                threads.add(threadInfo);
            } catch (Exception e) {
                LOG.debug("Failed to extract thread info for instance: id={}", instance.getInstanceId(), e);
            }
        }

        return threads;
    }

    private HeapThreadInfo extractThreadInfo(Instance threadInstance, OQLEngine engine) {
        String name = getStringFieldValue(threadInstance, "name");
        if (name == null) {
            name = "unnamed-" + threadInstance.getInstanceId();
        }

        boolean daemon = getBooleanFieldValue(threadInstance, "daemon");
        int priority = getIntFieldValue(threadInstance, "priority", 5);

        Long retainedSize = null;
        if (engine != null) {
            retainedSize = calculateRetainedSize(engine, threadInstance.getInstanceId());
        }

        return new HeapThreadInfo(
                threadInstance.getInstanceId(),
                name,
                daemon,
                priority,
                retainedSize
        );
    }

    private Long calculateRetainedSize(OQLEngine engine, long objectId) {
        AtomicLong size = new AtomicLong(0);
        try {
            engine.executeQuery("select rsizeof(heap.findObject(" + objectId + "))", result -> {
                if (result instanceof Number n) {
                    size.set(n.longValue());
                }
                return true;
            });
        } catch (OQLException e) {
            LOG.debug("Failed to calculate retained size: objectId={} error={}", objectId, e.getMessage());
        }
        return size.get() > 0 ? size.get() : null;
    }

    private String getStringFieldValue(Instance instance, String fieldName) {
        Object value = instance.getValueOfField(fieldName);
        if (value instanceof Instance stringInstance) {
            // String object - get its value
            return extractStringValue(stringInstance);
        }
        return value != null ? value.toString() : null;
    }

    private String extractStringValue(Instance stringInstance) {
        if (!"java.lang.String".equals(stringInstance.getJavaClass().getName())) {
            return stringInstance.toString();
        }

        try {
            Object valueField = stringInstance.getValueOfField("value");
            if (!(valueField instanceof PrimitiveArrayInstance array)) {
                return null;
            }

            @SuppressWarnings("unchecked")
            List<String> values = (List<String>) array.getValues();
            if (values.isEmpty()) {
                return "";
            }

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
            LOG.debug("Failed to extract string value: instanceId={}", stringInstance.getInstanceId(), e);
            return null;
        }
    }

    private boolean getBooleanFieldValue(Instance instance, String fieldName) {
        Object value = instance.getValueOfField(fieldName);
        if (value instanceof Boolean b) {
            return b;
        }
        return false;
    }

    private int getIntFieldValue(Instance instance, String fieldName, int defaultValue) {
        Object value = instance.getValueOfField(fieldName);
        if (value instanceof Number n) {
            return n.intValue();
        }
        return defaultValue;
    }
}
