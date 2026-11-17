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

package cafe.jeffrey.profile.manager.heapdump;

import cafe.jeffrey.profile.common.pipeline.PipelineDefinition;

import java.util.List;

/**
 * The heap-dump initialization pipeline: what a run does, in the order it does it.
 *
 * <p>Stage ids are opaque strings shared with the frontend's timeline definition, which owns the labels
 * and the grouping into phases.</p>
 */
public final class HeapDumpStages {

    public static final String PIPELINE_ID = "heap-dump-init";

    public static final String LOAD = "load";
    public static final String PARSE = "parse";
    public static final String INDEX = "index";
    public static final String STRINGS = "strings";
    public static final String DOMINATOR = "dominator";
    public static final String THREADS = "threads";
    public static final String BIGGEST = "biggest";
    public static final String COLLECTIONS = "collections";
    public static final String LEAKS = "leaks";
    public static final String CLASSLOADERS = "classloaders";
    public static final String BIGGEST_COLLECTIONS = "biggest-collections";
    public static final String CONSUMERS = "consumers";
    public static final String DUPLICATES = "duplicates";

    /**
     * The {@code index} build ({@code manager.initialize}) is one atomic operation internally composed
     * of measured sub-phases; it is surfaced to the timeline as three stages by grouping those
     * sub-phases. The stage ids run in this order.
     */
    public static final List<String> INDEX_GROUP = List.of(LOAD, PARSE, INDEX);

    /**
     * The stages that compute an answer rather than build the index — everything a reader can ask for
     * one at a time. {@code DOMINATOR} is among them because retained sizes are exactly that kind of
     * answer, and several of the others cannot run until it has.
     */
    public static final List<String> REPORTS = List.of(
            STRINGS, DOMINATOR, THREADS, BIGGEST, COLLECTIONS, LEAKS, CLASSLOADERS,
            BIGGEST_COLLECTIONS, CONSUMERS, DUPLICATES);

    public static final PipelineDefinition DEFINITION = new PipelineDefinition(
            PIPELINE_ID,
            List.of(LOAD, PARSE, INDEX, STRINGS, DOMINATOR, THREADS, BIGGEST, COLLECTIONS, LEAKS,
                    CLASSLOADERS, BIGGEST_COLLECTIONS, CONSUMERS, DUPLICATES));

    private HeapDumpStages() {
    }
}
