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

import cafe.jeffrey.profile.heapdump.parser.HeapDumpIndexPaths;
import cafe.jeffrey.profile.heapdump.parser.HeapDumpSession;
import cafe.jeffrey.profile.manager.AdditionalFilesManager;
import cafe.jeffrey.shared.common.model.ProfileInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Clock;
import java.util.Optional;

/**
 * Lifecycle wrapper around {@link HeapDumpSession}. Opens (or builds) a
 * short-lived session over a profile's heap dump, runs a unit of work, and
 * always closes the session afterwards.
 */
public final class HeapDumpSessionTemplate {

    private static final Logger LOG = LoggerFactory.getLogger(HeapDumpSessionTemplate.class);

    private final ProfileInfo profileInfo;

    private final AdditionalFilesManager additionalFilesManager;

    private final Clock clock;

    public HeapDumpSessionTemplate(
            ProfileInfo profileInfo,
            AdditionalFilesManager additionalFilesManager,
            Clock clock) {

        this.profileInfo = profileInfo;
        this.additionalFilesManager = additionalFilesManager;
        this.clock = clock;
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
        try (HeapDumpSession session = HeapDumpSession.openOrBuild(heapPath.get(), clock)) {
            return Optional.ofNullable(work.apply(session));
        } catch (IOException | SQLException e) {
            LOG.warn("Heap dump operation failed: profileId={} path={} error={}",
                    profileInfo.id(), heapPath.get(), e.getMessage());
            throw new RuntimeException("Heap dump operation failed: " + e.getMessage(), e);
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
        Path indexPath = HeapDumpIndexPaths.indexFor(heapPath.get());
        if (!Files.exists(indexPath)) {
            return false;
        }
        try {
            long hprofMtime = Files.getLastModifiedTime(heapPath.get()).toMillis();
            long indexMtime = Files.getLastModifiedTime(indexPath).toMillis();
            return indexMtime >= hprofMtime;
        } catch (IOException e) {
            return false;
        }
    }
}
