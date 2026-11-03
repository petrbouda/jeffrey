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

package cafe.jeffrey.microscope.core;

import cafe.jeffrey.shared.common.Json;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static cafe.jeffrey.test.recording.PersonsRecording.ALLOCATION_IN_NEW_TLAB;
import static cafe.jeffrey.test.recording.PersonsRecording.DIRECT;
import static cafe.jeffrey.test.recording.PersonsRecording.DOM;
import static cafe.jeffrey.test.recording.PersonsRecording.EXECUTION_SAMPLE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The flamegraphs of the two persons runs, and the difference between them: the one change between
 * the runs is how the service serialises — straight to the output ({@code --efficient.mode=true}) or
 * through Jackson's tree model ({@code false}) — and the comparison has to find it without being
 * told where to look.
 *
 * <p>It finds it in what is allocated, and on the CPU. The DOM run allocates the tree model's
 * types — text nodes and the token buffers a tree is read back through — and the direct run
 * allocates none of them; the comparison ranks them among the DOM run's new allocations, by the very
 * bytes the profile's own events hold. On the CPU, the DOM run spends samples in the tree's nodes and
 * the direct run all but none.
 */
class PersonsFlamegraphIntegrationTest extends PersonsVolumeMicroscope {

    /** The tree model's types the DOM run allocates, with the bytes it allocated of each. */
    private static final Map<String, Long> TREE_MODEL_BYTES = treeModelBytes();

    private static final String TREE_MODEL_SEARCH = "com.fasterxml.jackson.databind.util.TokenBuffer";
    /** The DOM run's allocation samples whose stack passes through a token buffer. */
    private static final int TOKEN_BUFFER_SAMPLES = 169;

    /** The tree model's nodes, and the CPU samples whose stack passes through them in each run. */
    private static final String TREE_NODE_SEARCH = "com.fasterxml.jackson.databind.node";
    private static final int TREE_NODE_CPU_SAMPLES_DOM = 312;
    private static final int TREE_NODE_CPU_SAMPLES_DIRECT = 1;
    /** The streaming generator every response is finally written through. */
    private static final String GENERATOR_SEARCH = "com.fasterxml.jackson.core.json.UTF8JsonGenerator";

    private static final Pattern PRIMARY_TOTAL = Pattern.compile("(?m)^primary_total: (\\d+)$");
    private static final Pattern BASELINE_TOTAL = Pattern.compile("(?m)^baseline_total: (\\d+)");
    private static final Pattern SAMPLES_TOTAL = Pattern.compile("(?m)^samples_total: (\\d+)$");
    private static final Pattern SEARCH_MATCHES = Pattern.compile("(?m)^search_matches: (\\d+)");
    private static final String NEW = "new";
    private static final double BYTES_PER_MIB = 1024 * 1024;

    @Nested
    class TheChangeBetweenTheRuns {

        /** By the profile's own events: the DOM run allocates the tree model, the direct run none of it. */
        @Test
        void onlyTheDomRunAllocatesTheTreeModel() {
            assertEquals(TREE_MODEL_BYTES, treeModelAllocated(profile(DOM)));
            assertEquals(Map.of(), treeModelAllocated(profile(DIRECT)));
        }

        /**
         * Ranked by bytes, against the direct run, every tree-model type is a new allocation of the DOM
         * run, at the bytes the events hold.
         */
        @Test
        void theComparisonRanksTheTreeModelAmongTheNewAllocations() {
            JsonNode movements = microscope.answer("compare_movements",
                    pair().put("eventType", ALLOCATION_IN_NEW_TLAB).put("limit", 15));
            Map<String, List<String>> regressed = regressedRows(movements.path("markdown").asString());

            TREE_MODEL_BYTES.forEach((type, bytes) -> {
                List<String> row = regressed.get(type);
                assertTrue(row != null, () -> type + " is not among the regressed: " + regressed.keySet());
                assertEquals(mib(bytes), row.get(0), type);
                assertEquals(NEW, row.get(row.size() - 1), type);
            });
        }

        /** The same through the graph the user opens: a search for the token buffer lights the DOM run only. */
        @Test
        void aSearchForTheTokenBufferFindsItInTheDomRunOnly() {
            assertEquals(TOKEN_BUFFER_SAMPLES, searchMatches(profile(DOM), ALLOCATION_IN_NEW_TLAB, TREE_MODEL_SEARCH));
            assertEquals(0, searchMatches(profile(DIRECT), ALLOCATION_IN_NEW_TLAB, TREE_MODEL_SEARCH));
        }

        /**
         * On the CPU too: the DOM run spends its samples in the tree model's nodes, the direct run all
         * but none; both write through the streaming generator in the end.
         */
        @Test
        void onTheCpuTheDomRunWorksInTheTreeModel() {
            assertEquals(TREE_NODE_CPU_SAMPLES_DOM,
                    searchMatches(profile(DOM), EXECUTION_SAMPLE, TREE_NODE_SEARCH));
            assertEquals(TREE_NODE_CPU_SAMPLES_DIRECT,
                    searchMatches(profile(DIRECT), EXECUTION_SAMPLE, TREE_NODE_SEARCH));
            assertTrue(searchMatches(profile(DIRECT), EXECUTION_SAMPLE, GENERATOR_SEARCH) > 0);
            assertTrue(searchMatches(profile(DOM), EXECUTION_SAMPLE, GENERATOR_SEARCH) > 0);
        }
    }

    /** Leaving out samples that never ran Java code is the same filter in every answer that offers it. */
    @Nested
    class JavaOnly {

        @Test
        void theExportsAndTheComparisonCountTheSameJavaSamples() {
            int dom = samplesTotal(javaOnlyExport(profile(DOM)));
            int direct = samplesTotal(javaOnlyExport(profile(DIRECT)));
            String comparison = microscope.answer("compare_movements", pair().put("eventType", EXECUTION_SAMPLE)
                    .put("excludeIdle", true).put("excludeNonJava", true)).path("markdown").asString();

            assertEquals(dom, header(PRIMARY_TOTAL, comparison));
            assertEquals(direct, header(BASELINE_TOTAL, comparison));
            int domAll = DOM.counts().executionSamples();
            int directAll = DIRECT.counts().executionSamples();
            assertTrue(dom < domAll, () -> dom + " of " + domAll);
            assertTrue(direct < directAll, () -> direct + " of " + directAll);
        }
    }

    /** The DOM run as the primary, the direct run as its baseline. */
    private ObjectNode pair() {
        return Json.createObject().put("profileId", profile(DOM)).put("baselineProfileId", profile(DIRECT));
    }

    /** Bytes allocated of each tree-model type, by SQL over the profile's allocation events. */
    private Map<String, Long> treeModelAllocated(String profileId) {
        String types = String.join("', '", TREE_MODEL_BYTES.keySet());
        JsonNode result = microscope.answer("jfr_executeQuery", Json.createObject()
                .put("profileId", profileId)
                .put("query", "SELECT weight_entity, sum(weight) FROM events"
                        + " WHERE event_type = '" + ALLOCATION_IN_NEW_TLAB + "' AND weight_entity IN ('" + types + "')"
                        + " GROUP BY weight_entity ORDER BY weight_entity"));
        Map<String, Long> bytes = new LinkedHashMap<>();
        for (JsonNode row : result.path("rows")) {
            bytes.put(row.get(0).asString(), Long.parseLong(row.get(1).asString()));
        }
        return bytes;
    }

    private int searchMatches(String profileId, String eventType, String search) {
        JsonNode export = microscope.answer("flamegraph_export", Json.createObject().put("profileId", profileId)
                .put("eventType", eventType).put("detail", "SUMMARY").put("search", search));
        return header(SEARCH_MATCHES, export.path("markdown").asString());
    }

    private String javaOnlyExport(String profileId) {
        return microscope.answer("flamegraph_export", Json.createObject().put("profileId", profileId)
                .put("eventType", EXECUTION_SAMPLE).put("detail", "SUMMARY")
                .put("excludeIdle", true).put("excludeNonJava", true)).path("markdown").asString();
    }

    private static int samplesTotal(String document) {
        return header(SAMPLES_TOTAL, document);
    }

    private static int header(Pattern pattern, String document) {
        Matcher matcher = pattern.matcher(document);
        assertTrue(matcher.find(), () -> "The document states no " + pattern.pattern());
        return Integer.parseInt(matcher.group(1));
    }

    /**
     * The rows of the comparison's "Regressed" table, by method, each the cells after the method:
     * primary self, baseline self, delta, change.
     */
    private static Map<String, List<String>> regressedRows(String document) {
        int start = document.indexOf("## Regressed");
        int end = document.indexOf("## Improved", start);
        assertTrue(start >= 0 && end > start, "The comparison has no regressed table");
        Map<String, List<String>> rows = new LinkedHashMap<>();
        for (String line : document.substring(start, end).split("\n")) {
            List<String> cells = List.of(line.split("\\|")).stream().map(String::trim).toList();
            if (cells.size() > 5 && !cells.get(1).isEmpty() && !cells.get(1).startsWith("-")
                    && !"method".equals(cells.get(1))) {
                rows.put(cells.get(1), List.of(cells.get(2), cells.get(5)));
            }
        }
        return rows;
    }

    /** How the comparison writes a size in mebibytes: one decimal. */
    private static String mib(long bytes) {
        return String.format(Locale.ROOT, "%.1f MiB", bytes / BYTES_PER_MIB);
    }

    private static Map<String, Long> treeModelBytes() {
        Map<String, Long> bytes = new LinkedHashMap<>();
        bytes.put("com.fasterxml.jackson.databind.node.TextNode", 15_728_610L);
        bytes.put("com.fasterxml.jackson.databind.util.TokenBuffer", 13_631_462L);
        bytes.put("com.fasterxml.jackson.databind.util.TokenBuffer$Parser", 13_631_462L);
        bytes.put("com.fasterxml.jackson.databind.util.TokenBufferReadContext", 11_534_314L);
        return bytes;
    }
}
