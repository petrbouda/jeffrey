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
package cafe.jeffrey.profile.heapdump.parser;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    void throwsWhenHprofMissing(@TempDir Path tmp) {
        Path missing = tmp.resolve("nope.hprof");
        assertThrows(IOException.class, () -> HeapDumpSession.openOrBuild(missing, CLOCK));
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
