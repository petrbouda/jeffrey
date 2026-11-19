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

package cafe.jeffrey.microscope.core.profile.manager.heapdump;

import cafe.jeffrey.microscope.model.ProfileInfo;
import cafe.jeffrey.microscope.model.RecordingEventSource;
import cafe.jeffrey.microscope.core.profile.heapdump.persistence.HeapDumpIndexPaths;
import cafe.jeffrey.microscope.core.profile.heapdump.persistence.HeapDumpSession;
import cafe.jeffrey.microscope.core.profile.heapdump.model.ThreadAnalysisReport;
import cafe.jeffrey.microscope.core.profile.manager.heapdump.analysis.LeakSuspectsAnalysis;
import cafe.jeffrey.microscope.core.profile.manager.heapdump.analysis.ThreadHeapAnalysis;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HeapDumpReportUpgradeTest {

    private static final Instant CREATED = Instant.parse("2026-10-31T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(CREATED, ZoneOffset.UTC);
    private static final Clock LATER = Clock.fixed(CREATED.plusSeconds(60), ZoneOffset.UTC);

    @TempDir
    Path directory;

    @Test
    void legacyReportOverTheCurrentTreeIsRegeneratedOnAccessWithoutReparsingTheIndex() throws Exception {
        Path dump = legacyThreadReport(true);

        try (HeapDumpSessionCache cache = new HeapDumpSessionCache(LATER)) {
            HeapDumpManager manager = manager(cache);
            assertTrue(manager.isCacheReady());
            assertFalse(manager.reportExists(new ThreadHeapAnalysis()));

            ThreadAnalysisReport migrated = manager.report(new ThreadHeapAnalysis()).orElseThrow();

            assertNotNull(migrated, "opening an existing report must repair its stale cache");
            assertTrue(migrated.totalRetainedSize() > 0);
            assertTrue(manager.reportExists(new ThreadHeapAnalysis()));
            assertFalse(Files.exists(directory.resolve("thread-analysis.json")));
            cache.withSession(dump, session -> {
                assertEquals(CREATED.toEpochMilli(), session.view().metadata().parsedAtMs(),
                        "the valid heap index must be reused");
                return null;
            });
        }
        try (HeapDumpSessionCache restarted = new HeapDumpSessionCache(LATER)) {
            assertTrue(manager(restarted).report(new ThreadHeapAnalysis()).orElseThrow().totalRetainedSize() > 0,
                    "the migrated report remains readable after a restart");
        }
    }

    @Test
    void aReadDoesNotBuildTheTreeAReportNeedsButPreparingTheReportDoes() throws Exception {
        Path dump = legacyLeakReport();

        try (HeapDumpSessionCache cache = new HeapDumpSessionCache(LATER)) {
            HeapDumpManager manager = manager(cache);

            assertTrue(manager.report(new LeakSuspectsAnalysis()).isEmpty(), "a page load must not build the dominator tree");
            assertFalse(hasCurrentTree(cache, dump), "the read left the obsolete tree alone");

            manager.runReport(new LeakSuspectsAnalysis());

            assertTrue(manager.report(new LeakSuspectsAnalysis()).isPresent());
            assertTrue(hasCurrentTree(cache, dump), "preparing the report rebuilds the tree it needs");
            cache.withSession(dump, session -> {
                assertEquals(CREATED.toEpochMilli(), session.view().metadata().parsedAtMs(),
                        "from the existing index");
                return null;
            });
        }
    }

    @Test
    void theThreadsAreReadWithoutBuildingTheTreeAndGainRetainedSizesOnceOneIsBuilt() throws Exception {
        Path dump = legacyThreadReport(false);

        try (HeapDumpSessionCache cache = new HeapDumpSessionCache(LATER)) {
            HeapDumpManager manager = manager(cache);

            ThreadAnalysisReport withoutTree = manager.report(new ThreadHeapAnalysis()).orElseThrow();

            assertNotNull(withoutTree, "the threads are a report without the tree");
            assertNull(withoutTree.threads().getFirst().retainedSize(), "unknown, not read from the obsolete tree");
            assertFalse(hasCurrentTree(cache, dump), "reading the threads builds no tree");
            assertTrue(Files.exists(directory.resolve(HeapDumpReportStore.treelessName(new ThreadHeapAnalysis()))));

            manager.runComputeDominator();
            ThreadAnalysisReport withTree = manager.report(new ThreadHeapAnalysis()).orElseThrow();

            assertNotNull(withTree.threads().getFirst().retainedSize(), "recomputed over the tree now there");
            assertEquals(manager.getThreads(true).getFirst().retainedSize(), withTree.threads().getFirst().retainedSize());
            assertFalse(Files.exists(directory.resolve(HeapDumpReportStore.treelessName(new ThreadHeapAnalysis()))),
                    "the copy without the tree is replaced");
        }
    }

    @Test
    void preparingTheThreadsAloneBuildsNoTreeAndCachesNoRetainedSizeFromAnObsoleteOne() throws Exception {
        Path dump = legacyThreadReport(false);

        try (HeapDumpSessionCache cache = new HeapDumpSessionCache(LATER)) {
            HeapDumpManager manager = manager(cache);

            manager.runReport(new ThreadHeapAnalysis());
            ThreadAnalysisReport report = manager.report(new ThreadHeapAnalysis()).orElseThrow();

            assertFalse(hasCurrentTree(cache, dump), "preparing the threads costs no tree build");
            assertNull(report.threads().getFirst().retainedSize(),
                    "an obsolete tree's retained sizes are never cached");
            manager.runComputeDominator();
            assertEquals(manager.getThreads(true).getFirst().retainedSize(),
                    manager.report(new ThreadHeapAnalysis()).orElseThrow().threads().getFirst().retainedSize());
        }
    }

    @Test
    void anIntentionallyUnpreparedReportIsNotBuiltByARead() throws Exception {
        SyntheticHeapDumps.writeThreadDump(directory, "dump.hprof");
        try (HeapDumpSessionCache cache = new HeapDumpSessionCache(CLOCK)) {
            HeapDumpManager manager = manager(cache);
            manager.initialize(null, phase -> {});

            assertTrue(manager.report(new ThreadHeapAnalysis()).isEmpty());
            assertFalse(manager.reportExists(new ThreadHeapAnalysis()));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"{broken", "null"})
    void aCorruptCurrentReportIsRegeneratedInsteadOfShownAsEmpty(String payload) throws Exception {
        SyntheticHeapDumps.writeThreadDump(directory, "dump.hprof");
        try (HeapDumpSessionCache cache = new HeapDumpSessionCache(CLOCK)) {
            HeapDumpManager manager = manager(cache);
            manager.runComputeDominator();
            manager.runReport(new ThreadHeapAnalysis());
            Path report = directory.resolve(HeapDumpReportStore.storedName(new ThreadHeapAnalysis()));
            Files.writeString(report, payload);

            assertFalse(assertDoesNotThrow(() -> manager.reportExists(new ThreadHeapAnalysis())));
            assertTrue(manager.report(new ThreadHeapAnalysis()).orElseThrow().totalRetainedSize() > 0);
        }
    }

    /** A thread report cached before reports were versioned, over the current tree or an unversioned one. */
    private Path legacyThreadReport(boolean keepCurrentTree) throws Exception {
        Path dump = SyntheticHeapDumps.writeThreadDump(directory, "dump.hprof");
        ThreadAnalysisReport oldReport;
        try (HeapDumpSessionCache cache = new HeapDumpSessionCache(CLOCK)) {
            HeapDumpManager manager = manager(cache);
            manager.runComputeDominator();
            manager.runReport(new ThreadHeapAnalysis());
            oldReport = manager.report(new ThreadHeapAnalysis()).orElseThrow();
        }
        HeapDumpReportStore reports = new HeapDumpReportStore(directory);
        reports.delete(new ThreadHeapAnalysis());
        reports.write("thread-analysis.json", oldReport, "Legacy thread report");
        if (!keepCurrentTree) {
            dropTreeVersion(dump);
        }
        assertTrue(HeapDumpSession.isIndexUsable(dump));
        return dump;
    }

    /** A leak-suspects report cached before reports were versioned, over an unversioned tree. */
    private Path legacyLeakReport() throws Exception {
        Path dump = SyntheticHeapDumps.writeThreadDump(directory, "dump.hprof");
        try (HeapDumpSessionCache cache = new HeapDumpSessionCache(CLOCK)) {
            manager(cache).runReport(new LeakSuspectsAnalysis());
        }
        Path current = directory.resolve(HeapDumpReportStore.storedName(new LeakSuspectsAnalysis()));
        Files.move(current, directory.resolve("leak-suspects.json"));
        dropTreeVersion(dump);
        assertFalse(new HeapDumpReportStore(directory).exists(new LeakSuspectsAnalysis()));
        return dump;
    }

    private static void dropTreeVersion(Path dump) throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:duckdb:" + HeapDumpIndexPaths.indexFor(dump));
             var statement = connection.createStatement()) {
            statement.execute("DROP TABLE dominator_build");
        }
    }

    private static boolean hasCurrentTree(HeapDumpSessionCache cache, Path dump) throws Exception {
        return cache.withSession(dump, session -> session.view().hasDominatorTree());
    }

    private HeapDumpManager manager(HeapDumpSessionCache cache) {
        ProfileInfo profile = new ProfileInfo("upgrade-profile", "Upgrade",
                RecordingEventSource.HEAP_DUMP, CREATED, CREATED, CREATED, true, false, "recording");
        return new HeapDumpManagerImpl(profile, new HeapDumpFiles(directory), cache, null, null);
    }
}
