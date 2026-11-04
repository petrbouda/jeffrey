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
import cafe.jeffrey.test.recording.CheckoutRecording;
import cafe.jeffrey.test.recording.CheckoutRecording.HeapFigures;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A real heap dump, end to end: the checkout workload's, taken as its candidate run ended, imported,
 * prepared — indexed, its dominator tree built, its reports computed — and read by the heap tools.
 * Every figure is held to what an independent HPROF reader finds in the file ({@link HeapFigures}).
 *
 * <p>The workload keeps one order in twenty forever in its audit log: the leak the dump was taken to
 * show.
 */
class HeapRecordingIntegrationTest extends CheckoutVolumeMicroscope {

    private static final String AUDIT_LOG = "com.acme.checkout.Checkout$AuditLog";
    private static final String COMPLETED = "COMPLETED";
    private static final String JAVA_FRAME = "Java frame";
    private static final String MAIN_THREAD = "main";
    private static final String ORDERS_HELD = "5,822 objects";

    /** HPROF names a primitive array's type without dumping it as a class: one each for the eight primitives. */
    private static final int PRIMITIVE_ARRAY_TYPES = 8;

    /** The largest page heap_oql hands back. */
    private static final int OQL_PAGE = 100;
    /** How many pages of the promo orders are read: one past the first proves the cursor. */
    private static final int PROMO_PAGES = 2;

    private static final Duration PREPARATION = Duration.ofMinutes(5);
    private static final Duration POLL = Duration.ofSeconds(1);

    private static final Path RUN = Path.of("target", "heap-it", UUID.randomUUID().toString()).toAbsolutePath();

    /** The dump's profile, imported and prepared once for every test here. */
    private static String heapProfile;

    @Nested
    class Preparation {

        /** Imported, the dump is a heap-dump profile, and preparing it runs every stage to completion. */
        @Test
        void everyStageCompletes() {
            JsonNode status = microscope.answer("heap_status", pid());

            assertEquals(COMPLETED, status.path("state").asString(), status::toString);
            status.path("stages").forEach(stage ->
                    assertEquals(COMPLETED, stage.path("status").asString(), stage::toString));
        }
    }

    /** What the dump holds, counted as the reader counts it. */
    @Nested
    class Contents {

        /** Every instance, object array and primitive array is live in the summary; every class dumped is a class. */
        @Test
        void theSummaryCountsEveryObjectAndClassTheFileHolds() {
            JsonNode summary = microscope.answer("heap_getHeapSummary", pid());
            HeapFigures figures = CheckoutRecording.HEAP_FIGURES;

            assertEquals(figures.instances() + figures.objectArrays() + figures.primitiveArrays(),
                    summary.path("totalLiveInstances").asInt());
            assertEquals(figures.classes() + PRIMITIVE_ARRAY_TYPES, summary.path("classCount").asInt());
            assertFalse(summary.path("truncated").asBoolean());
            assertEquals(0, summary.path("warningCount").asInt());
            assertEquals(0, summary.path("reportsNotBuilt").size(), summary::toString);
        }

        @Test
        void theHistogramCountsEveryOrder() {
            JsonNode histogram = microscope.answer("heap_getClassHistogram", pid().put("topN", 20).put("sortBy", "COUNT"));
            int orders = -1;
            for (JsonNode row : histogram.path("classes")) {
                if (CheckoutRecording.HEAP_FIGURES.orderClass().equals(row.path("className").asString())) {
                    orders = row.path("instanceCount").asInt();
                }
            }

            assertEquals(CheckoutRecording.HEAP_FIGURES.orders(), orders, histogram::toString);
        }

        /** OQL counts the orders. */
        @Test
        void oqlCountsTheOrders() {
            JsonNode counted = microscope.answer("heap_oql",
                    pid().put("query", "SELECT count(*) FROM " + CheckoutRecording.HEAP_FIGURES.orderClass()));

            assertEquals(String.valueOf(CheckoutRecording.HEAP_FIGURES.orders()),
                    counted.path("rows").path(0).path("value").asString().replaceAll("\\D", ""), counted::toString);
        }

        /**
         * Planned to SQL, every order read page by page through the cursor to the end: each one once,
         * the count unknown until the page that ends them says it.
         */
        @Test
        void oqlPagesThroughEveryOrder() {
            int all = CheckoutRecording.HEAP_FIGURES.orders();
            int pagesTheyFill = (all + OQL_PAGE - 1) / OQL_PAGE;
            // One page more than the orders fill: a cursor that never ends fails on the count, not the heap.
            List<JsonNode> pages = oqlPages("SELECT o FROM " + CheckoutRecording.HEAP_FIGURES.orderClass() + " o",
                    pagesTheyFill + 1);
            Set<String> orders = new HashSet<>();
            int read = 0;
            for (JsonNode page : pages) {
                for (JsonNode row : page.path("rows")) {
                    assertEquals(CheckoutRecording.HEAP_FIGURES.orderClass(), row.path("className").asString());
                    orders.add(row.path("objectId").asString());
                    read++;
                }
            }

            assertEquals(pagesTheyFill, pages.size(), "pages until the last one ends them");
            assertEquals(all, read, "rows read across the pages");
            assertEquals(all, orders.size(), "each order once");
            JsonNode last = pages.getLast();
            assertFalse(last.path("hasMore").asBoolean(), last::toString);
            assertEquals(all, last.path("totalCount").asInt(), last::toString);
        }

        /**
         * Walked in Java, the promo orders selected by their coupon's text: a page past the first proves
         * the cursor (every page re-reads the orders before it, so all of them would only repeat it) —
         * two full pages, none twice, every row a promo order as SQL over the index finds them.
         */
        @Test
        void oqlSelectsThePromoOrdersByTheirCouponsTextPageAfterPage() {
            List<JsonNode> pages = oqlPages("SELECT o FROM " + CheckoutRecording.HEAP_FIGURES.orderClass() + " o"
                    + " WHERE toString(o.coupon) LIKE \"SPRING-.*\"", PROMO_PAGES);
            Set<String> orders = new HashSet<>();
            for (JsonNode page : pages) {
                assertEquals(OQL_PAGE, page.path("rows").size(), page::toString);
                page.path("rows").forEach(row -> orders.add(row.path("objectId").asString()));
            }

            assertEquals(PROMO_PAGES, pages.size());
            assertEquals(PROMO_PAGES * OQL_PAGE, orders.size(), "no promo order on two pages");
            JsonNode promo = microscope.answer("heap_executeQuery", pid().put("query",
                    promoOrdersSql("count(DISTINCT r.source_id)") + " AND r.source_id IN (" + String.join(", ", orders) + ")"));
            assertEquals(String.valueOf(orders.size()), promo.path("rows").path(0).path(0).asString(), promo::toString);
        }

        /** SQL over the index holds as many objects as the summary reports. */
        @Test
        void theIndexHoldsEveryObject() {
            JsonNode count = microscope.answer("heap_executeQuery", pid().put("query", "SELECT count(*) FROM instance"));
            HeapFigures figures = CheckoutRecording.HEAP_FIGURES;

            assertEquals(String.valueOf(figures.instances() + figures.objectArrays() + figures.primitiveArrays()),
                    count.path("rows").path(0).path(0).asString());
        }
    }

    /** What holds the heap: the audit log, which keeps every order it is given. */
    @Nested
    class Retention {

        /**
         * The audit log is the one suspect: held from {@code main}'s frame, its every order under one
         * array — the accumulation point — and the orders its top contributors.
         */
        @Test
        void theAuditLogIsTheLeakSuspect() {
            JsonNode suspects = microscope.answer("heap_getLeakSuspects", pid()).path("suspects");
            JsonNode suspect = suspects.path(0);

            assertEquals(AUDIT_LOG, suspect.path("className").asString(), suspects::toString);
            assertEquals(1, suspect.path("instanceCount").asInt());
            assertEquals(JAVA_FRAME, suspect.path("gcRootPath").path("rootType").asString());
            assertEquals(MAIN_THREAD, suspect.path("gcRootPath").path("threadName").asString());
            assertTrue(suspect.path("accumulationPoint").asString().contains(ORDERS_HELD), suspect::toString);
            JsonNode contributor = suspect.path("topContributors").path(0);
            assertEquals(CheckoutRecording.HEAP_FIGURES.orderClass(), contributor.path("className").asString());
            assertEquals(CheckoutRecording.HEAP_FIGURES.orders(), contributor.path("instanceCount").asInt());
        }

        /**
         * The dominator tree's heaviest root is the same audit log, retaining what the suspect does; no
         * root retains less than itself, and together they retain no more than the heap.
         */
        @Test
        void theDominatorTreesHeaviestRootIsTheAuditLog() {
            JsonNode roots = microscope.answer("heap_getDominatorTreeRoots", pid());
            JsonNode suspect = microscope.answer("heap_getLeakSuspects", pid()).path("suspects").path(0);
            JsonNode heaviest = roots.path("roots").path(0);

            assertEquals(AUDIT_LOG, heaviest.path("className").asString());
            assertEquals(suspect.path("objectId").asString(), heaviest.path("objectId").asString());
            assertEquals(suspect.path("retainedBytes").asLong(), heaviest.path("retainedBytes").asLong());
            long retained = 0;
            for (JsonNode root : roots.path("roots")) {
                assertTrue(root.path("retainedBytes").asLong() >= root.path("shallowBytes").asLong(), root::toString);
                retained += root.path("retainedBytes").asLong();
            }
            assertTrue(retained <= roots.path("totalHeapBytes").asLong(), () -> "Roots retain more than the heap");
        }
    }

    /** The {@link ToolSweep} over the dump's profile: every heap tool answers. */
    @Nested
    class Sweep {

        @Test
        void everyHeapToolAnswers() {
            ToolSweep.assertEveryToolAnswersAHeapDump(microscope, profile());
        }
    }

    /**
     * The query's pages through its cursor, up to {@code most} of them: every page but the last says
     * more follow and leaves the count unknown.
     */
    private List<JsonNode> oqlPages(String query, int most) {
        List<JsonNode> pages = new ArrayList<>();
        String cursor = null;
        do {
            ObjectNode arguments = pid().put("query", query).put("limit", OQL_PAGE);
            if (cursor != null) {
                arguments.put("cursor", cursor);
            }
            JsonNode page = microscope.answer("heap_oql", arguments);
            pages.add(page);
            if (page.path("hasMore").asBoolean()) {
                assertEquals(OQL_PAGE, page.path("rows").size(), page::toString);
                assertTrue(page.path("totalCount").isNull(), page::toString);
                cursor = page.path("nextCursor").asString();
            } else {
                cursor = null;
            }
        } while (cursor != null && pages.size() < most);
        return pages;
    }

    /** SQL over the index selecting the orders whose coupon's text starts as a promotion's. */
    private static String promoOrdersSql(String select) {
        return "SELECT " + select + " FROM instance i JOIN class c ON c.class_id = i.class_id"
                + " JOIN outbound_ref r ON r.source_id = i.instance_id"
                + " JOIN string_content s ON s.instance_id = r.target_id"
                + " WHERE c.name = '" + CheckoutRecording.HEAP_FIGURES.orderClass() + "'"
                + " AND s.content LIKE 'SPRING-%'";
    }

    private ObjectNode pid() {
        return Json.createObject().put("profileId", profile());
    }

    /** The dump imported from its file and prepared, the first time a test asks for it. */
    private String profile() {
        synchronized (HeapRecordingIntegrationTest.class) {
            if (heapProfile == null) {
                JsonNode imported = microscope.answer("recordings_analyzeFile",
                        Json.createObject().put("path", dumpFile().toString()));
                assertEquals("READY", imported.path("status").asString(), imported::toString);
                assertEquals("HEAP_DUMP", imported.path("eventSource").asString());
                String profileId = imported.path("profileId").asString();
                ObjectNode arguments = Json.createObject().put("profileId", profileId);
                microscope.answer("heap_prepare", arguments.deepCopy());
                await().atMost(PREPARATION).pollInterval(POLL).until(() -> !microscope.answer("heap_status",
                        arguments.deepCopy()).path("running").asBoolean());
                heapProfile = profileId;
            }
            return heapProfile;
        }
    }

    private static Path dumpFile() {
        try {
            Files.createDirectories(RUN);
            return Files.write(RUN.resolve(CheckoutRecording.HEAP_DUMP_FILE), CheckoutRecording.heapDump());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
