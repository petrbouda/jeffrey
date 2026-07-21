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

import cafe.jeffrey.shared.common.measure.Elapsed;
import cafe.jeffrey.shared.common.measure.Measuring;
import cafe.jeffrey.shared.common.model.repository.FileExtensions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import java.util.zip.GZIPInputStream;

/**
 * Resolves the analyzable {@code .hprof} path for a heap dump that may have
 * been captured gzip-compressed ({@code .hprof.gz}). The native parser mmaps
 * raw HPROF bytes, so a gzipped dump is decompressed once into a sibling
 * {@code .hprof} file; that sibling then owns the DuckDB index sidecar and is
 * the path every analysis opens.
 *
 * <p>Decompression is idempotent: the sibling is reused while it is at least
 * as new as the gzipped source. Concurrent callers may decompress twice, but
 * each writes to a unique temp file and atomically moves it into place, so
 * readers never observe a partially written dump.</p>
 */
public final class HeapDumpDecompressor {

    private static final Logger LOG = LoggerFactory.getLogger(HeapDumpDecompressor.class);

    private static final String GZIPPED_HPROF_SUFFIX = "." + FileExtensions.HPROF_GZ;

    private static final String GZ_EXTENSION = ".gz";

    private static final String TMP_FILE_PREFIX = ".decompress-";

    private static final String TMP_FILE_SUFFIX = ".tmp";

    private HeapDumpDecompressor() {
    }

    public static boolean isGzipped(Path heapDumpPath) {
        return heapDumpPath.getFileName().toString().toLowerCase().endsWith(GZIPPED_HPROF_SUFFIX);
    }

    /**
     * The path analyses (and the index sidecar) are keyed on: the sibling
     * {@code .hprof} for a gzipped dump, the path itself otherwise. Does not
     * touch the filesystem.
     */
    public static Path analyzablePath(Path heapDumpPath) {
        if (!isGzipped(heapDumpPath)) {
            return heapDumpPath;
        }
        String fileName = heapDumpPath.getFileName().toString();
        String decompressedName = fileName.substring(0, fileName.length() - GZ_EXTENSION.length());
        return heapDumpPath.resolveSibling(decompressedName);
    }

    /**
     * Ensures the analyzable {@code .hprof} exists and is up to date with the
     * gzipped source, decompressing it if needed, and returns its path.
     */
    public static Path ensureDecompressed(Path heapDumpPath) throws IOException {
        if (!isGzipped(heapDumpPath)) {
            return heapDumpPath;
        }

        Path target = analyzablePath(heapDumpPath);
        if (isUpToDate(target, heapDumpPath)) {
            return target;
        }

        Path tmpFile = target.resolveSibling(TMP_FILE_PREFIX + UUID.randomUUID() + TMP_FILE_SUFFIX);
        try {
            Elapsed<Long> decompressed = Measuring.s(() -> decompressToFile(heapDumpPath, tmpFile));
            Files.move(tmpFile, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            LOG.info("Decompressed gzipped heap dump: source={} target={} bytes={} duration_ms={}",
                    heapDumpPath, target, decompressed.entity(), decompressed.duration().toMillis());
        } catch (UncheckedIOException e) {
            throw e.getCause();
        } finally {
            Files.deleteIfExists(tmpFile);
        }
        return target;
    }

    private static boolean isUpToDate(Path target, Path gzSource) throws IOException {
        if (!Files.exists(target)) {
            return false;
        }
        long targetMtime = Files.getLastModifiedTime(target).toMillis();
        long sourceMtime = Files.getLastModifiedTime(gzSource).toMillis();
        return targetMtime >= sourceMtime;
    }

    private static long decompressToFile(Path gzSource, Path targetFile) {
        try (InputStream gzipStream = new GZIPInputStream(Files.newInputStream(gzSource))) {
            return Files.copy(gzipStream, targetFile);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
