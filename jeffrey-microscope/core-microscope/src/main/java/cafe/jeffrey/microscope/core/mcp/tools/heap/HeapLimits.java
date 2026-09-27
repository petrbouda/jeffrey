/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package cafe.jeffrey.microscope.core.mcp.tools.heap;

import cafe.jeffrey.profile.heapdump.analyzer.heapview.ClassLoaderLeakChainAnalyzer;
import cafe.jeffrey.profile.heapdump.analyzer.heapview.ConsumerReportAnalyzer;
import cafe.jeffrey.profile.heapdump.analyzer.heapview.PathToGCRootAnalyzer;

/**
 * How much of a heap report an answer carries. Each cut is declared in the record it applies to, so a
 * reader never takes the rows it was shown for the whole report.
 */
public final class HeapLimits {

    /** Threads in {@code heap_getThreads}: the ones retaining the most. */
    public static final int THREADS = 200;
    /** Consumers and components in {@code heap_getTopConsumers}: the ones retaining the most. */
    public static final int CONSUMERS = 20;
    /** Consumers the stored report keeps: its own cut, below which nothing is left out. */
    public static final int ENGINE_CONSUMERS = ConsumerReportAnalyzer.DEFAULT_TOP_N;
    /** Suspicious class loaders the stored report traces. */
    public static final int ENGINE_LOADER_CHAINS = ClassLoaderLeakChainAnalyzer.MAX_LOADERS_TO_CHECK;
    /** Hops the path-to-GC-root search walks back from its target. */
    public static final int PATH_SEARCH_HOPS = PathToGCRootAnalyzer.MAX_DEPTH;
    /** Deduplication opportunities in {@code heap_getStringAnalysis}: the biggest savings. */
    public static final int STRING_OPPORTUNITIES = 20;
    /** Characters of a string's content the string report keeps as its preview. */
    public static final int STRING_CONTENT_CHARS = 200;
    /** Classes that contributed to one leak suspect's cluster. */
    public static final int SUSPECT_CONTRIBUTORS = 5;
    /** Class-loader leak chains in {@code heap_getClassLoaderLeakChains}: the ones retaining the most. */
    public static final int LOADER_CHAINS = 25;
    /** Hops of a GC-root path kept at each end when the path is longer than twice this. */
    public static final int PATH_END_STEPS = 20;
    /** Fields of one instance in {@code heap_getInstanceDetail}, in declaration order. */
    public static final int INSTANCE_FIELDS = 200;
    /** Characters of one field value, instance value or OQL value an answer repeats. */
    public static final int VALUE_CHARS = 1_000;
    /** Characters of one object parameter in an instance listing. */
    public static final int PARAMETER_CHARS = 100;

    private HeapLimits() {
    }
}
