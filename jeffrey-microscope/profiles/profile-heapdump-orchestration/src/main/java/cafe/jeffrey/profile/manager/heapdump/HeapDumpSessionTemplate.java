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
import cafe.jeffrey.profile.heapdump.persistence.HeapDumpSession;
import cafe.jeffrey.profile.manager.additional.AdditionalFilesManager;
import cafe.jeffrey.shared.common.exception.Exceptions;
import cafe.jeffrey.shared.common.model.ProfileInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.Optional;

/**
 * Lifecycle wrapper around {@link HeapDumpSession}. Resolves the profile's
 * analyzable heap dump (decompressing a gzipped dump on first access), runs a
 * unit of work against the {@link HeapDumpSessionCache}-managed session, and
 * maps failures to the profile-scoped error contract.
 */
public final class HeapDumpSessionTemplate {

    private static final Logger LOG = LoggerFactory.getLogger(HeapDumpSessionTemplate.class);

    private final ProfileInfo profileInfo;

    private final AdditionalFilesManager additionalFilesManager;

    private final HeapDumpSessionCache sessionCache;

    public HeapDumpSessionTemplate(
            ProfileInfo profileInfo,
            AdditionalFilesManager additionalFilesManager,
            HeapDumpSessionCache sessionCache) {

        this.profileInfo = profileInfo;
        this.additionalFilesManager = additionalFilesManager;
        this.sessionCache = sessionCache;
    }

    @FunctionalInterface
    public interface SessionWork<R> {
        R apply(HeapDumpSession session) throws SQLException, IOException;
    }

    public <R> Optional<R> execute(SessionWork<R> work) {
        Optional<Path> heapPath = additionalFilesManager.getHeapDumpPath();
        if (heapPath.isEmpty()) {
            LOG.debug("No heap dump available: profileId={}", profileInfo.id());
            return Optional.empty();
        }
        try {
            Path hprofPath = HeapDumpDecompressor.ensureDecompressed(heapPath.get());
            return Optional.ofNullable(sessionCache.withSession(hprofPath, work::apply));
        } catch (IOException | SQLException e) {
            LOG.warn("Heap dump operation failed: profileId={} path={} error={}",
                    profileInfo.id(), heapPath.get(), e.getMessage());
            throw Exceptions.internal("Heap dump operation failed: " + e.getMessage(), e);
        }
    }

    public boolean heapDumpExists() {
        return additionalFilesManager.heapDumpExists();
    }

    public boolean isCacheReady() {
        Optional<Path> heapPath = additionalFilesManager.getHeapDumpPath();
        if (heapPath.isEmpty()) {
            return false;
        }
        // The index sidecar is keyed on the decompressed .hprof, which for a
        // gzipped dump may not have been materialized yet.
        Path hprofPath = HeapDumpDecompressor.analyzablePath(heapPath.get());
        Path indexPath = HeapDumpIndexPaths.indexFor(hprofPath);
        if (!Files.exists(hprofPath) || !Files.exists(indexPath)) {
            return false;
        }
        try {
            long hprofMtime = Files.getLastModifiedTime(hprofPath).toMillis();
            long indexMtime = Files.getLastModifiedTime(indexPath).toMillis();
            return indexMtime >= hprofMtime;
        } catch (IOException e) {
            return false;
        }
    }
}
