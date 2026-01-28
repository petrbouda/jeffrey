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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.profile.heapdump.model.ClassInstanceEntry;
import pbouda.jeffrey.profile.heapdump.model.ClassInstancesResponse;

import java.util.ArrayList;
import java.util.List;

/**
 * Browses instances of a specific class with pagination support.
 */
public class ClassInstanceBrowserAnalyzer {

    private static final Logger LOG = LoggerFactory.getLogger(ClassInstanceBrowserAnalyzer.class);
    private static final int MAX_LIMIT = 500;

    /**
     * Get a paginated list of instances for a given class name.
     *
     * @param heap                the loaded heap dump
     * @param className           fully qualified class name
     * @param limit               max instances per page
     * @param offset              offset for pagination
     * @param includeRetainedSize whether to compute retained size (expensive)
     * @return paginated response with instances
     */
    @SuppressWarnings("unchecked")
    public ClassInstancesResponse browse(Heap heap, String className, int limit, int offset, boolean includeRetainedSize) {
        if (limit <= 0 || limit > MAX_LIMIT) {
            limit = 50;
        }
        if (offset < 0) {
            offset = 0;
        }

        JavaClass javaClass = heap.getJavaClassByName(className);
        if (javaClass == null) {
            LOG.warn("Class not found in heap: className={}", className);
            return new ClassInstancesResponse(className, 0, List.of(), false);
        }

        List<Instance> allInstances = (List<Instance>) javaClass.getInstances();
        int totalInstances = allInstances.size();

        OQLEngine engine = new OQLEngine(heap);
        InstanceValueFormatter formatter = new InstanceValueFormatter(engine);

        int end = Math.min(offset + limit, totalInstances);
        List<ClassInstanceEntry> entries = new ArrayList<>();

        for (int i = offset; i < end; i++) {
            Instance instance = allInstances.get(i);
            Long retainedSize = null;
            if (includeRetainedSize) {
                long retained = instance.getRetainedSize();
                retainedSize = retained > 0 ? retained : null;
            }

            entries.add(new ClassInstanceEntry(
                    instance.getInstanceId(),
                    instance.getSize(),
                    retainedSize,
                    formatter.format(instance)
            ));
        }

        boolean hasMore = end < totalInstances;
        return new ClassInstancesResponse(className, totalInstances, entries, hasMore);
    }
}
