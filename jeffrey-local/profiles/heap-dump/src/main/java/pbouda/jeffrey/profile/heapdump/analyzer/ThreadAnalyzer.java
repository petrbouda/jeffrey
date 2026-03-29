/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
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

import org.netbeans.lib.profiler.heap.GCRoot;
import org.netbeans.lib.profiler.heap.Heap;
import org.netbeans.lib.profiler.heap.Instance;
import org.netbeans.lib.profiler.heap.JavaClass;
import org.netbeans.lib.profiler.heap.JavaFrameGCRoot;
import org.netbeans.lib.profiler.heap.PrimitiveArrayInstance;
import org.netbeans.lib.profiler.heap.ThreadObjectGCRoot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.profile.heapdump.model.HeapThreadInfo;
import pbouda.jeffrey.profile.heapdump.model.StackFrameLocal;
import pbouda.jeffrey.profile.heapdump.model.ThreadStackFrame;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

        List<Instance> instances = (List<Instance>) threadClass.getInstances();
        for (Instance instance : instances) {
            try {
                HeapThreadInfo threadInfo = extractThreadInfo(instance, includeRetainedSize);
                threads.add(threadInfo);
            } catch (Exception e) {
                LOG.debug("Failed to extract thread info for instance: id={}", instance.getInstanceId(), e);
            }
        }

        return threads;
    }

    /**
     * Analyze the stack trace of a specific thread, collecting local variable references per frame.
     *
     * @param heap             the loaded heap dump
     * @param threadObjectId   the object ID of the Thread instance
     * @param compressedOops   whether compressed oops correction should be applied to sizes
     * @return list of stack frames with their local variable references
     */
    @SuppressWarnings("unchecked")
    public List<ThreadStackFrame> analyzeThreadStack(Heap heap, long threadObjectId, boolean compressedOops) {
        // Find the ThreadObjectGCRoot for this thread
        ThreadObjectGCRoot threadGCRoot = findThreadGCRoot(heap, threadObjectId);
        if (threadGCRoot == null) {
            LOG.debug("No thread GC root found for thread: objectId={}", threadObjectId);
            return List.of();
        }

        StackTraceElement[] stackTrace = threadGCRoot.getStackTrace();
        if (stackTrace == null || stackTrace.length == 0) {
            LOG.debug("No stack trace available for thread: objectId={}", threadObjectId);
            return List.of();
        }

        // Collect JavaFrameGCRoot instances grouped by frame number for this thread
        Map<Integer, List<JavaFrameGCRoot>> frameRootsByIndex = new HashMap<>();
        Collection<GCRoot> gcRoots = (Collection<GCRoot>) heap.getGCRoots();
        for (GCRoot root : gcRoots) {
            if (root instanceof JavaFrameGCRoot jfRoot) {
                ThreadObjectGCRoot ownerThread = jfRoot.getThreadGCRoot();
                if (ownerThread != null && ownerThread.getInstance() != null
                        && ownerThread.getInstance().getInstanceId() == threadObjectId) {
                    int frameNumber = jfRoot.getFrameNumber();
                    frameRootsByIndex.computeIfAbsent(frameNumber, k -> new ArrayList<>()).add(jfRoot);
                }
            }
        }

        // Build ThreadStackFrame for each stack trace element
        List<ThreadStackFrame> frames = new ArrayList<>(stackTrace.length);
        for (int i = 0; i < stackTrace.length; i++) {
            StackTraceElement ste = stackTrace[i];
            List<StackFrameLocal> locals = new ArrayList<>();

            List<JavaFrameGCRoot> frameRoots = frameRootsByIndex.getOrDefault(i, List.of());
            for (JavaFrameGCRoot frameRoot : frameRoots) {
                Instance localInstance = frameRoot.getInstance();
                if (localInstance == null) {
                    continue;
                }

                long shallowSize = CompressedOopsCorrector.correctedShallowSize(localInstance, compressedOops);
                locals.add(new StackFrameLocal(
                        localInstance.getInstanceId(),
                        localInstance.getJavaClass().getName(),
                        null,
                        shallowSize
                ));
            }

            frames.add(new ThreadStackFrame(
                    ste.getClassName(),
                    ste.getMethodName(),
                    ste.getFileName(),
                    ste.getLineNumber(),
                    locals
            ));
        }

        LOG.debug("Thread stack analyzed: objectId={} frames={} totalLocals={}",
                threadObjectId, frames.size(),
                frames.stream().mapToInt(f -> f.locals().size()).sum());

        return frames;
    }

    /**
     * Find the ThreadObjectGCRoot associated with a specific thread instance.
     */
    @SuppressWarnings("unchecked")
    private ThreadObjectGCRoot findThreadGCRoot(Heap heap, long threadObjectId) {
        Collection<GCRoot> gcRoots = (Collection<GCRoot>) heap.getGCRoots();
        for (GCRoot root : gcRoots) {
            if (root instanceof ThreadObjectGCRoot threadRoot) {
                Instance threadInstance = threadRoot.getInstance();
                if (threadInstance != null && threadInstance.getInstanceId() == threadObjectId) {
                    return threadRoot;
                }
            }
        }
        return null;
    }

    private HeapThreadInfo extractThreadInfo(Instance threadInstance, boolean includeRetainedSize) {
        String name = getStringFieldValue(threadInstance, "name");
        if (name == null) {
            name = "unnamed-" + threadInstance.getInstanceId();
        }

        boolean daemon = getBooleanFieldValue(threadInstance, "daemon");
        int priority = getIntFieldValue(threadInstance, "priority", 5);

        Long retainedSize = null;
        if (includeRetainedSize) {
            long retained = threadInstance.getRetainedSize();
            retainedSize = retained > 0 ? retained : null;
        }

        return new HeapThreadInfo(
                threadInstance.getInstanceId(),
                name,
                daemon,
                priority,
                retainedSize
        );
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
