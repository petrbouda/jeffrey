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
package cafe.jeffrey.profile.heapdump.parser;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import cafe.jeffrey.profile.heapdump.model.IndexBuildProgressListener;
import cafe.jeffrey.profile.common.pipeline.SubPhaseTiming;
import cafe.jeffrey.profile.heapdump.persistence.HeapDumpIndexPaths;
import cafe.jeffrey.profile.heapdump.persistence.HeapDumpSession;
import cafe.jeffrey.profile.heapdump.view.HprofTag;

class HeapDumpSessionTest {

    private static final Clock CLOCK =
            Clock.fixed(Instant.ofEpochMilli(1L), ZoneOffset.UTC);

    @Test
    void buildsIndexOnFirstOpenAndReusesItOnSecondOpen(@TempDir Path tmp) throws IOException, SQLException {
        Path hprof = simpleDump(tmp, "session.hprof");
        Path indexPath = HeapDumpIndexPaths.indexFor(hprof);
        assertFalse(Files.exists(indexPath), "index doesn't exist before first open");

        try (HeapDumpSession session = HeapDumpSession.openOrBuild(hprof, CLOCK)) {
            assertEquals(hprof, session.hprof().path());
            assertTrue(Files.exists(indexPath), "first open builds the index");
            assertEquals(indexPath, session.indexDbPath());
            // Index built but no dominator yet.
            assertFalse(session.view().hasDominatorTree());
        }

        long firstBuildMtime = Files.getLastModifiedTime(indexPath).toMillis();
        // Second open with no .hprof change — shouldn't rebuild.
        try (HeapDumpSession session = HeapDumpSession.openOrBuild(hprof, CLOCK)) {
            assertEquals(firstBuildMtime, Files.getLastModifiedTime(indexPath).toMillis(),
                    "second open with unchanged hprof reuses the existing index");
            assertTrue(session.view().classCount() >= 1);
        }
    }

    @Test
    void buildDominatorTreeIfNeededIsIdempotent(@TempDir Path tmp) throws IOException, SQLException {
        Path hprof = simpleDump(tmp, "dom.hprof");
        try (HeapDumpSession session = HeapDumpSession.openOrBuild(hprof, CLOCK)) {
            assertFalse(session.view().hasDominatorTree());
            session.buildDominatorTreeIfNeeded();
            assertTrue(session.view().hasDominatorTree());
            // Second call with the same session is a no-op.
            session.buildDominatorTreeIfNeeded();
            assertTrue(session.view().hasDominatorTree());
        }
    }

    /**
     * The tree is two tables loaded one after the other. A process that died between them used to
     * leave a dominator table that counted as a finished tree, and every later build stage skipped
     * the work; the presence check now wants both, and the next build fills them in again.
     */
    @Test
    void aDominatorTableWithoutRetainedSizesDoesNotReadAsATree(@TempDir Path tmp) throws IOException, SQLException {
        Path hprof = simpleDump(tmp, "half.hprof");
        Path indexPath = HeapDumpIndexPaths.indexFor(hprof);
        try (HeapDumpSession session = HeapDumpSession.openOrBuild(hprof, CLOCK)) {
            session.buildDominatorTreeIfNeeded();
            assertTrue(session.view().hasDominatorTree());
        }

        try (Connection conn = DriverManager.getConnection("jdbc:duckdb:" + indexPath.toAbsolutePath());
             Statement stmt = conn.createStatement()) {
            stmt.execute("DELETE FROM retained_size");
        }

        try (HeapDumpSession session = HeapDumpSession.openOrBuild(hprof, CLOCK)) {
            assertFalse(session.view().hasDominatorTree(), "half a load is not a tree");
            session.buildDominatorTreeIfNeeded();
            assertTrue(session.view().hasDominatorTree());
        }
    }

    @Test
    void rebuildsIndexWhenHprofIsNewerThanIndex(@TempDir Path tmp) throws IOException, SQLException {
        Path hprof = simpleDump(tmp, "stale.hprof");
        Path indexPath = HeapDumpIndexPaths.indexFor(hprof);

        try (HeapDumpSession ignored = HeapDumpSession.openOrBuild(hprof, CLOCK)) {
            // initial build done
        }
        // Make the index look older than the .hprof, then bump the hprof's mtime.
        Files.setLastModifiedTime(indexPath, java.nio.file.attribute.FileTime.fromMillis(1_000L));
        Files.setLastModifiedTime(hprof, java.nio.file.attribute.FileTime.fromMillis(2_000L));

        try (HeapDumpSession session = HeapDumpSession.openOrBuild(hprof, CLOCK)) {
            // Rebuild fires; mtime updates beyond the 1_000 we stamped.
            assertTrue(Files.getLastModifiedTime(indexPath).toMillis() > 1_000L);
            assertTrue(session.view().classCount() >= 1);
        }
    }

    @Test
    void rebuildsIndexWhenLeftoverWalSiblingExists(@TempDir Path tmp) throws IOException, SQLException {
        Path hprof = simpleDump(tmp, "wal.hprof");
        Path walPath = HeapDumpIndexPaths.indexWalFor(hprof);

        try (HeapDumpSession ignored = HeapDumpSession.openOrBuild(hprof, CLOCK)) {
            // initial build done
        }
        // Simulate an interrupted build: a leftover WAL means the building
        // connection never ran its close-time checkpoint, so the index content
        // is incomplete and a read-only open would fail replaying it.
        Files.writeString(walPath, "leftover-wal");

        try (HeapDumpSession session = HeapDumpSession.openOrBuild(hprof, CLOCK)) {
            assertFalse(session.lastBuildSubPhases().isEmpty(), "leftover WAL must force a rebuild");
            assertFalse(Files.exists(walPath), "rebuild must remove the leftover WAL");
            assertTrue(session.view().classCount() >= 1);
        }
    }

    @Test
    void throwsWhenHprofMissing(@TempDir Path tmp) {
        Path missing = tmp.resolve("nope.hprof");
        assertThrows(IOException.class, () -> HeapDumpSession.openOrBuild(missing, CLOCK));
    }

    /**
     * A build that dies partway must leave nothing that later reads would mistake for a
     * finished index. Publishing one wedges the dump permanently: every request afterwards
     * fails on the missing {@code dump_metadata} row instead of rebuilding.
     */
    @Test
    void failedBuildLeavesNoIndexBehind(@TempDir Path tmp) throws IOException {
        Path hprof = simpleDump(tmp, "failing.hprof");
        Path indexPath = HeapDumpIndexPaths.indexFor(hprof);

        try (HprofMappedFile file = HprofMappedFile.open(hprof)) {
            assertThrows(IllegalStateException.class, () ->
                    HprofIndex.build(file, indexPath, CLOCK, failAt("walk_pass_b")));
        }

        assertAll(
                () -> assertFalse(Files.exists(indexPath),
                        "a failed build must not publish its half-written index"),
                () -> assertFalse(Files.exists(HeapDumpIndexPaths.indexBuildFor(hprof)),
                        "the scratch database of a failed build must be removed"),
                () -> assertFalse(HeapDumpSession.isIndexUsable(hprof),
                        "the dump must still look unindexed, so the next open rebuilds it"));
    }

    @Test
    void rebuildSucceedsAfterAFailedBuild(@TempDir Path tmp) throws IOException, SQLException {
        Path hprof = simpleDump(tmp, "retry.hprof");
        Path indexPath = HeapDumpIndexPaths.indexFor(hprof);

        try (HprofMappedFile file = HprofMappedFile.open(hprof)) {
            assertThrows(IllegalStateException.class, () ->
                    HprofIndex.build(file, indexPath, CLOCK, failAt("walk_pass_b")));
        }

        try (HeapDumpSession session = HeapDumpSession.openOrBuild(hprof, CLOCK)) {
            assertFalse(session.lastBuildSubPhases().isEmpty(), "the retry must run a real build");
            assertTrue(session.view().classCount() >= 1);
        }
    }

    /** A listener that aborts the build when the named sub-phase starts. */
    private static IndexBuildProgressListener failAt(String subPhaseName) {
        return new IndexBuildProgressListener() {
            @Override
            public void onSubPhaseStarted(String name) {
                if (subPhaseName.equals(name)) {
                    throw new IllegalStateException("injected failure at " + name);
                }
            }

            @Override
            public void onSubPhase(SubPhaseTiming timing) {
                // not needed for this test
            }
        };
    }

    private static Path simpleDump(Path tmp, String name) throws IOException {
        long classId = 0xC001L;
        return SyntheticHprof.create("1.0.2", 8, 0L)
                .string(0xA001L, "X")
                .string(0xA002L, "f")
                .loadClass(1, classId, 0, 0xA001L)
                .heapDumpSegment(seg -> seg
                        .topLevelObjectClassDump(classId, 0xA002L)
                        .gcRoot(HprofTag.Sub.ROOT_STICKY_CLASS, 0x100L)
                        .instanceDump(0x100L, classId, idBytes(0L, 8)))
                .heapDumpEnd()
                .writeTo(tmp, name);
    }

    private static byte[] idBytes(long id, int idSize) {
        try {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            DataOutputStream d = new DataOutputStream(b);
            if (idSize == 4) {
                d.writeInt((int) id);
            } else {
                d.writeLong(id);
            }
            return b.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
