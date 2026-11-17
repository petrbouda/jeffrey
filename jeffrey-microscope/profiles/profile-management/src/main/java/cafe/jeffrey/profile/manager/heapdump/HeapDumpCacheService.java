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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.microscope.model.ProfileInfo;
import cafe.jeffrey.profile.heapdump.persistence.HeapDumpIndexPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * What is built from a heap dump, as opposed to the dump itself: the open session over it, its index
 * and the cached reports. The dump is the profile's own copy of a recording and goes with the profile.
 */
public final class HeapDumpCacheService {

    private static final Logger LOG = LoggerFactory.getLogger(HeapDumpCacheService.class);

    private static final String CONFIG_FILE = "heap-dump-config.json";

    private static final String CONFIG_DISPLAY_NAME = "Heap dump config";

    private final ProfileInfo profileInfo;

    private final HeapDumpFiles files;

    private final HeapDumpReportStore reports;

    private final HeapDumpSessionCache sessionCache;

    public HeapDumpCacheService(
            ProfileInfo profileInfo,
            HeapDumpFiles files,
            HeapDumpReportStore reports,
            HeapDumpSessionCache sessionCache) {

        this.profileInfo = profileInfo;
        this.files = files;
        this.reports = reports;
        this.sessionCache = sessionCache;
    }

    public void unloadHeap() {
        // Release the cached session for this dump; the next analyzer call
        // transparently reopens it from the on-disk index.
        files.dumpPath().ifPresent(path -> sessionCache.invalidate(HeapDumpDecompressor.analyzablePath(path)));
    }

    public void deleteCache() {
        files.dumpPath().ifPresent(path -> {
            Path hprofPath = HeapDumpDecompressor.analyzablePath(path);
            sessionCache.invalidate(hprofPath);
            deleteIndex(hprofPath);
        });
        reports.deleteAllCachedAnalyses();
        reports.delete(CONFIG_FILE, CONFIG_DISPLAY_NAME);
    }

    /**
     * Deletes the index sidecar of the given dump, including a WAL sibling
     * possibly left over by an interrupted index build.
     */
    private void deleteIndex(Path hprofPath) {
        deleteIfPresent(HeapDumpIndexPaths.indexFor(hprofPath), "heap dump index");
        deleteIfPresent(HeapDumpIndexPaths.indexWalFor(hprofPath), "heap dump index WAL");
    }

    private void deleteIfPresent(Path path, String description) {
        if (!Files.exists(path)) {
            return;
        }
        try {
            Files.delete(path);
        } catch (IOException e) {
            LOG.error("Failed to delete {}: profileId={} path={}", description, profileInfo.id(), path, e);
        }
    }
}
