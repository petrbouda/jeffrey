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

import cafe.jeffrey.microscope.model.ProfileInfo;
import cafe.jeffrey.microscope.model.RecordingEventSource;
import cafe.jeffrey.profile.heapdump.persistence.HeapDumpIndexPaths;
import cafe.jeffrey.profile.heapdump.persistence.HeapDumpSession;
import cafe.jeffrey.profile.heapdump.model.ThreadAnalysisReport;
import cafe.jeffrey.profile.manager.heapdump.analysis.ThreadHeapAnalysis;
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
    void legacyReportIsRegeneratedOnAccessWithoutReparsingTheIndex() throws Exception {
        Path dump = legacyThreadReport();

        try (HeapDumpSessionCache cache = new HeapDumpSessionCache(LATER)) {
            HeapDumpManager manager = manager(cache);
            assertTrue(manager.isCacheReady());
            assertFalse(manager.threadAnalysisExists());

            ThreadAnalysisReport migrated = manager.getThreadAnalysis();

            assertNotNull(migrated, "opening an existing report must repair its stale cache");
            assertTrue(migrated.totalRetainedSize() > 0);
            assertTrue(manager.threadAnalysisExists());
            assertFalse(Files.exists(directory.resolve("thread-analysis.json")));
            cache.withSession(dump, session -> {
                assertEquals(CREATED.toEpochMilli(), session.view().metadata().parsedAtMs(),
                        "the valid heap index must be reused");
                return null;
            });
        }
        try (HeapDumpSessionCache restarted = new HeapDumpSessionCache(LATER)) {
            assertTrue(manager(restarted).getThreadAnalysis().totalRetainedSize() > 0,
                    "the migrated report remains readable after a restart");
        }
    }

    @Test
    void preparingThreadsBeforeTheTreeCannotCacheMissingRetainedSizes() throws Exception {
        legacyThreadReport();

        try (HeapDumpSessionCache cache = new HeapDumpSessionCache(LATER)) {
            HeapDumpManager manager = manager(cache);

            manager.runThreadAnalysis();
            ThreadAnalysisReport report = manager.getThreadAnalysis();

            assertNotNull(report.threads().getFirst().retainedSize(),
                    "a versioned thread report must be computed from the current tree");
            assertTrue(report.totalRetainedSize() > 0);
            manager.runComputeDominator();
            assertEquals(manager.getThreads(true).getFirst().retainedSize(),
                    manager.getThreadAnalysis().threads().getFirst().retainedSize());
        }
    }

    @Test
    void anIntentionallyUnpreparedReportIsNotBuiltByARead() throws Exception {
        SyntheticHeapDumps.writeThreadDump(directory, "dump.hprof");
        try (HeapDumpSessionCache cache = new HeapDumpSessionCache(CLOCK)) {
            HeapDumpManager manager = manager(cache);
            manager.initialize(null, phase -> {});

            assertNull(manager.getThreadAnalysis());
            assertFalse(manager.threadAnalysisExists());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"{broken", "null"})
    void aCorruptCurrentReportIsRegeneratedInsteadOfShownAsEmpty(String payload) throws Exception {
        SyntheticHeapDumps.writeThreadDump(directory, "dump.hprof");
        try (HeapDumpSessionCache cache = new HeapDumpSessionCache(CLOCK)) {
            HeapDumpManager manager = manager(cache);
            manager.runThreadAnalysis();
            Path report = directory.resolve(HeapDumpReportStore.storedName(new ThreadHeapAnalysis()));
            Files.writeString(report, payload);

            assertFalse(assertDoesNotThrow(manager::threadAnalysisExists));
            assertTrue(manager.getThreadAnalysis().totalRetainedSize() > 0);
        }
    }

    private Path legacyThreadReport() throws Exception {
        Path dump = SyntheticHeapDumps.writeThreadDump(directory, "dump.hprof");
        ThreadAnalysisReport oldReport;
        try (HeapDumpSessionCache cache = new HeapDumpSessionCache(CLOCK)) {
            HeapDumpManager manager = manager(cache);
            manager.runComputeDominator();
            manager.runThreadAnalysis();
            oldReport = manager.getThreadAnalysis();
        }
        HeapDumpReportStore reports = new HeapDumpReportStore(directory);
        reports.delete(new ThreadHeapAnalysis());
        reports.write("thread-analysis.json", oldReport, "Legacy thread report");
        try (var connection = DriverManager.getConnection("jdbc:duckdb:" + HeapDumpIndexPaths.indexFor(dump));
             var statement = connection.createStatement()) {
            statement.execute("DROP TABLE dominator_build");
        }
        assertTrue(HeapDumpSession.isIndexUsable(dump));
        return dump;
    }

    private HeapDumpManager manager(HeapDumpSessionCache cache) {
        ProfileInfo profile = new ProfileInfo("upgrade-profile", "project", "workspace", "Upgrade",
                RecordingEventSource.HEAP_DUMP, CREATED, CREATED, CREATED, true, false, "recording");
        return new HeapDumpManagerImpl(profile, new HeapDumpFiles(directory), cache, null, null);
    }
}
