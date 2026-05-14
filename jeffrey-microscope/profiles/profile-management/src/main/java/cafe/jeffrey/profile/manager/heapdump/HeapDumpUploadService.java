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
import cafe.jeffrey.profile.manager.AdditionalFilesManager;
import cafe.jeffrey.shared.common.filesystem.FileSystemUtils;
import cafe.jeffrey.shared.common.model.ProfileInfo;
import cafe.jeffrey.shared.common.model.repository.FileExtensions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.Set;

/**
 * File-system side of heap-dump lifecycle: upload, sanitize, and delete the
 * heap-dump file along with its sidecars (gzip companion, DuckDB index, and
 * cached analysis JSON files).
 */
public final class HeapDumpUploadService {

    private static final Logger LOG = LoggerFactory.getLogger(HeapDumpUploadService.class);

    private static final String GZ_SUFFIX = "." + FileExtensions.HPROF_GZ;

    private static final String HPROF_SUFFIX = "." + FileExtensions.HPROF;

    private static final int GZ_SUFFIX_LENGTH = 3; // ".gz"

    private static final Set<String> ACCEPTED_SUFFIXES = Set.of(HPROF_SUFFIX, GZ_SUFFIX);

    private static final String CONFIG_FILE = "heap-dump-config.json";

    private static final String CONFIG_DISPLAY_NAME = "Heap dump config";

    private static final String INIT_PIPELINE_RESULT_FILE = "init-pipeline-result.json";

    private static final String INIT_PIPELINE_DISPLAY_NAME = "Init pipeline result";

    private final ProfileInfo profileInfo;

    private final AdditionalFilesManager additionalFilesManager;

    private final HeapDumpReportStore reports;

    private final Path heapDumpAnalysisPath;

    public HeapDumpUploadService(
            ProfileInfo profileInfo,
            AdditionalFilesManager additionalFilesManager,
            HeapDumpReportStore reports,
            Path heapDumpAnalysisPath) {

        this.profileInfo = profileInfo;
        this.additionalFilesManager = additionalFilesManager;
        this.reports = reports;
        this.heapDumpAnalysisPath = heapDumpAnalysisPath;
    }

    public void upload(InputStream inputStream, String filename) {
        validateFilename(filename);

        try {
            FileSystemUtils.removeDirectory(heapDumpAnalysisPath);
        } catch (RuntimeException e) {
            LOG.warn("Failed to clean up existing heap dump directory: profileId={} path={}",
                    profileInfo.id(), heapDumpAnalysisPath, e);
        }

        Path targetPath = heapDumpAnalysisPath.resolve(filename);
        try {
            Files.createDirectories(heapDumpAnalysisPath);
            Files.copy(inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
            LOG.info("Heap dump uploaded: profileId={} path={}", profileInfo.id(), targetPath);
        } catch (IOException e) {
            LOG.error("Failed to upload heap dump: profileId={} filename={}", profileInfo.id(), filename, e);
            throw new RuntimeException("Failed to upload heap dump: " + e.getMessage(), e);
        }
    }

    public void sanitize() {
        // The native parser is fault-tolerant by design — framing recovery is
        // applied inline during indexing rather than as a separate pre-pass.
        // Kept as a no-op so any frontend that still calls /sanitize gets a
        // clean response.
        LOG.debug("sanitizeHeapDump is a no-op on the native parser path: profileId={}", profileInfo.id());
    }

    public void unloadHeap() {
        // The native parser path doesn't hold long-lived heap state; every
        // analyzer call opens and closes its own short-lived session.
    }

    public void deleteCache() {
        additionalFilesManager.getHeapDumpPath().ifPresent(path -> deleteIndex(HeapDumpIndexPaths.indexFor(path)));
        reports.deleteAllCachedAnalyses();
        reports.delete(CONFIG_FILE, CONFIG_DISPLAY_NAME);
        reports.delete(INIT_PIPELINE_RESULT_FILE, INIT_PIPELINE_DISPLAY_NAME);
    }

    public void deleteHeapDump() {
        Optional<Path> heapPath = additionalFilesManager.getHeapDumpPath();
        if (heapPath.isEmpty()) {
            return;
        }

        Path path = heapPath.get();
        String fileName = path.getFileName().toString().toLowerCase();

        Path hprofPath;
        Path gzPath = null;
        if (fileName.endsWith(GZ_SUFFIX)) {
            gzPath = path;
            String decompressedName = path.getFileName().toString();
            decompressedName = decompressedName.substring(0, decompressedName.length() - GZ_SUFFIX_LENGTH);
            hprofPath = path.resolveSibling(decompressedName);
        } else {
            hprofPath = path;
            Path potentialGz = path.resolveSibling(path.getFileName() + ".gz");
            if (Files.exists(potentialGz)) {
                gzPath = potentialGz;
            }
        }

        deleteIndex(HeapDumpIndexPaths.indexFor(hprofPath));
        deleteIfPresent(hprofPath, "heap dump");
        if (gzPath != null) {
            deleteIfPresent(gzPath, "compressed heap dump");
        }
    }

    private void validateFilename(String filename) {
        String lower = filename.toLowerCase();
        for (String suffix : ACCEPTED_SUFFIXES) {
            if (lower.endsWith(suffix)) {
                return;
            }
        }
        throw new IllegalArgumentException("Invalid file type. Only .hprof and .hprof.gz files are supported.");
    }

    private void deleteIndex(Path indexPath) {
        try {
            Files.deleteIfExists(indexPath);
        } catch (IOException e) {
            LOG.error("Failed to delete heap dump index: profileId={} path={}", profileInfo.id(), indexPath, e);
        }
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
