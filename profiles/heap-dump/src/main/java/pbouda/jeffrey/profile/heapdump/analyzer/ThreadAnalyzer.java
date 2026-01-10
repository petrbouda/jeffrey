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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.profile.heapdump.model.HeapThreadInfo;

import java.util.ArrayList;
import java.util.List;

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
    @SuppressWarnings("unchecked")
    public List<HeapThreadInfo> analyze(Heap heap) {
        List<HeapThreadInfo> threads = new ArrayList<>();

        JavaClass threadClass = heap.getJavaClassByName(THREAD_CLASS);
        if (threadClass == null) {
            LOG.warn("Thread class not found in heap dump");
            return threads;
        }

        List<Instance> instances = (List<Instance>) threadClass.getInstances();
        for (Instance instance : instances) {
            try {
                HeapThreadInfo threadInfo = extractThreadInfo(instance);
                if (threadInfo != null) {
                    threads.add(threadInfo);
                }
            } catch (Exception e) {
                LOG.debug("Failed to extract thread info for instance: id={}", instance.getInstanceId(), e);
            }
        }

        return threads;
    }

    private HeapThreadInfo extractThreadInfo(Instance threadInstance) {
        String name = getStringFieldValue(threadInstance, "name");
        if (name == null) {
            name = "unnamed-" + threadInstance.getInstanceId();
        }

        boolean daemon = getBooleanFieldValue(threadInstance, "daemon");
        int priority = getIntFieldValue(threadInstance, "priority", 5);

        return new HeapThreadInfo(
                threadInstance.getInstanceId(),
                name,
                daemon,
                priority
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
        // Check if this is actually a String class
        if (!"java.lang.String".equals(stringInstance.getJavaClass().getName())) {
            return stringInstance.toString();
        }

        // Get the value field (char[] or byte[] depending on JDK version)
        Object valueField = stringInstance.getValueOfField("value");
        if (valueField instanceof List<?> valueArray) {
            // JDK 9+: byte[] with coder, or JDK 8: char[]
            Object coderField = stringInstance.getValueOfField("coder");
            boolean isLatin1 = coderField == null || (coderField instanceof Number n && n.intValue() == 0);

            StringBuilder sb = new StringBuilder();
            if (isLatin1) {
                // LATIN1 encoding (1 byte per char) or old char[] format
                for (Object val : valueArray) {
                    if (val instanceof Character c) {
                        sb.append(c);
                    } else if (val instanceof Number n) {
                        sb.append((char) (n.intValue() & 0xFF));
                    }
                }
            } else {
                // UTF16 encoding (2 bytes per char)
                byte[] bytes = new byte[valueArray.size()];
                int i = 0;
                for (Object val : valueArray) {
                    if (val instanceof Number n) {
                        bytes[i++] = n.byteValue();
                    }
                }
                // Convert UTF-16 bytes to string
                for (int j = 0; j < bytes.length - 1; j += 2) {
                    int ch = ((bytes[j] & 0xFF) << 8) | (bytes[j + 1] & 0xFF);
                    sb.append((char) ch);
                }
            }
            return sb.toString();
        }
        return "Thread-" + stringInstance.getInstanceId();
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
