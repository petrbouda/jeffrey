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

import cafe.jeffrey.profile.heapdump.persistence.HeapDumpIndexPaths;
import cafe.jeffrey.profile.manager.additional.AdditionalFilesManager;
import cafe.jeffrey.shared.common.model.ProfileInfo;
import cafe.jeffrey.shared.common.model.RecordingEventSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HeapDumpSessionTemplateTest {

    private static final Clock CLOCK = Clock.fixed(Instant.ofEpochMilli(1L), ZoneOffset.UTC);

    @TempDir
    Path tempDir;

    @Mock
    AdditionalFilesManager additionalFilesManager;

    private final HeapDumpSessionCache cache = new HeapDumpSessionCache(CLOCK);

    @AfterEach
    void closeCache() {
        cache.close();
    }

    @Test
    void executesWorkAgainstGzippedHeapDump(@TempDir Path dumpDir) throws IOException {
        Path gzPath = writeGzippedSyntheticDump(dumpDir);
        when(additionalFilesManager.getHeapDumpPath()).thenReturn(Optional.of(gzPath));
        HeapDumpSessionTemplate template = new HeapDumpSessionTemplate(
                profileInfo(), additionalFilesManager, cache);

        Optional<Long> recordCount = template.execute(
                session -> session.view().metadata().recordCount());

        assertTrue(recordCount.isPresent(), "gzipped dump must be analyzable");
        assertTrue(recordCount.get() > 0);
        assertTrue(Files.exists(dumpDir.resolve("dump.hprof")),
                "decompressed sibling must be materialized next to the .gz");
    }

    @Test
    void cacheNotReadyWhenLeftoverWalSiblingExists(@TempDir Path dumpDir) throws IOException {
        Path hprof = SyntheticHeapDumps.writeMinimalDump(dumpDir, "dump.hprof");
        when(additionalFilesManager.getHeapDumpPath()).thenReturn(Optional.of(hprof));
        HeapDumpSessionTemplate template = new HeapDumpSessionTemplate(
                profileInfo(), additionalFilesManager, cache);

        template.execute(session -> session.view().metadata().recordCount());
        assertTrue(template.isCacheReady(), "freshly built index must be ready");

        // Simulate an interrupted build: a leftover WAL means the index content
        // is incomplete and must not be reported as ready.
        Files.writeString(HeapDumpIndexPaths.indexWalFor(hprof), "leftover-wal");
        assertFalse(template.isCacheReady(), "leftover WAL marks the index as incomplete");
    }

    @Test
    void returnsEmptyWhenNoHeapDumpPresent() {
        when(additionalFilesManager.getHeapDumpPath()).thenReturn(Optional.empty());
        HeapDumpSessionTemplate template = new HeapDumpSessionTemplate(
                profileInfo(), additionalFilesManager, cache);

        Optional<Long> result = template.execute(
                session -> session.view().metadata().recordCount());

        assertEquals(Optional.empty(), result);
    }

    private Path writeGzippedSyntheticDump(Path dumpDir) throws IOException {
        Path hprof = SyntheticHeapDumps.writeMinimalDump(tempDir, "dump.hprof");
        Path gzPath = dumpDir.resolve("dump.hprof.gz");
        try (OutputStream out = new GZIPOutputStream(Files.newOutputStream(gzPath))) {
            Files.copy(hprof, out);
        }
        return gzPath;
    }

    private static ProfileInfo profileInfo() {
        return new ProfileInfo(
                "test-profile", "test-project", "test-workspace", "Test Profile",
                RecordingEventSource.HEAP_DUMP,
                Instant.EPOCH, Instant.EPOCH, Instant.EPOCH,
                true, false, "test-recording");
    }

}
