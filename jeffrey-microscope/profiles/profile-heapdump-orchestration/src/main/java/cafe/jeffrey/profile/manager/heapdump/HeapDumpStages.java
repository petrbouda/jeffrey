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

    public static final PipelineDefinition DEFINITION = new PipelineDefinition(
            PIPELINE_ID,
            List.of(LOAD, PARSE, INDEX, STRINGS, DOMINATOR, THREADS, BIGGEST, COLLECTIONS, LEAKS,
                    CLASSLOADERS, BIGGEST_COLLECTIONS, CONSUMERS, DUPLICATES));

    private HeapDumpStages() {
    }
}
