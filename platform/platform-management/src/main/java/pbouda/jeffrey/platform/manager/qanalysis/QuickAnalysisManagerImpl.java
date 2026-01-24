/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package pbouda.jeffrey.platform.manager.qanalysis;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.profile.ProfileInitializer;
import pbouda.jeffrey.profile.manager.ProfileManager;
import pbouda.jeffrey.provider.profile.RecordingInformationParser;
import pbouda.jeffrey.provider.profile.model.recording.RecordingInformation;
import pbouda.jeffrey.shared.common.IDGenerator;
import pbouda.jeffrey.shared.common.Json;
import pbouda.jeffrey.shared.common.Schedulers;
import pbouda.jeffrey.shared.common.filesystem.FileSystemUtils;
import pbouda.jeffrey.shared.common.filesystem.JeffreyDirs;
import pbouda.jeffrey.shared.common.model.ProfileInfo;
import pbouda.jeffrey.shared.common.model.RecordingEventSource;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

public class QuickAnalysisManagerImpl implements QuickAnalysisManager {

    private static final Logger LOG = LoggerFactory.getLogger(QuickAnalysisManagerImpl.class);

    private final Clock clock;
    private final JeffreyDirs jeffreyDirs;
    private final RecordingInformationParser recordingInformationParser;
    private final ProfileInitializer profileInitializer;
    private final ProfileManager.Factory profileManagerFactory;

    public QuickAnalysisManagerImpl(
            Clock clock,
            JeffreyDirs jeffreyDirs,
            RecordingInformationParser recordingInformationParser,
            ProfileInitializer profileInitializer,
            ProfileManager.Factory profileManagerFactory) {

        this.clock = clock;
        this.jeffreyDirs = jeffreyDirs;
        this.recordingInformationParser = recordingInformationParser;
        this.profileInitializer = profileInitializer;
        this.profileManagerFactory = profileManagerFactory;
    }

    @Override
    public CompletableFuture<String> uploadAndAnalyze(String filename, InputStream inputStream) {
        return CompletableFuture.supplyAsync(
                        () -> uploadAndAnalyzeInternal(filename, inputStream),
                        Schedulers.sharedVirtual())
                .exceptionally(ex -> {
                    LOG.error("Could not upload and analyze JFR file: filename={} message={}",
                            filename, ex.getMessage(), ex);
                    throw new RuntimeException("Could not upload and analyze JFR file: " + filename, ex);
                });
    }

    @Override
    public CompletableFuture<String> uploadHeapDump(String filename, InputStream inputStream) {
        return CompletableFuture.supplyAsync(
                        () -> uploadHeapDumpInternal(filename, inputStream),
                        Schedulers.sharedVirtual())
                .exceptionally(ex -> {
                    LOG.error("Could not upload heap dump: filename={} message={}",
                            filename, ex.getMessage(), ex);
                    throw new RuntimeException("Could not upload heap dump: " + filename, ex);
                });
    }

    private String uploadHeapDumpInternal(String filename, InputStream inputStream) {
        String profileId = IDGenerator.generate();
        Instant createdAt = clock.instant();

        // Create heap dump analysis directory for this quick profile
        Path heapDumpAnalysisPath = jeffreyDirs.quickHeapDumpAnalysisDir(profileId);
        FileSystemUtils.createDirectories(heapDumpAnalysisPath);

        // Save heap dump file
        Path targetPath = heapDumpAnalysisPath.resolve(filename);
        try {
            Files.copy(inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to save heap dump file", e);
        }

        LOG.info("Uploaded heap dump saved: path={}", targetPath);

        // Create ProfileInfo without JFR-specific data (use UNKNOWN event source)
        ProfileInfo profileInfo = new ProfileInfo(
                profileId,
                null,  // workspaceId
                null,  // projectId
                filename,  // name
                RecordingEventSource.UNKNOWN,
                createdAt,  // profilingStartedAt (use creation time)
                createdAt,  // profilingFinishedAt
                createdAt,
                true   // enabled
        );

        // Write metadata file for filesystem-based resolution
        QuickProfileMetadata metadata = new QuickProfileMetadata(
                profileId,
                filename,
                RecordingEventSource.UNKNOWN.name(),
                createdAt,
                createdAt,
                createdAt);

        writeMetadata(profileId, metadata);

        // Create profile manager (initializes database)
        profileManagerFactory.apply(profileInfo);

        LOG.info("Quick heap dump profile created: profile_id={} profile_name={}", profileId, filename);

        return profileId;
    }

    private String uploadAndAnalyzeInternal(String filename, InputStream inputStream) {
        // Ensure quick-recordings directory exists
        Path quickRecordingsDir = jeffreyDirs.quickRecordings();
        FileSystemUtils.createDirectories(quickRecordingsDir);

        // Save file to quick-recordings with unique name
        String recordingId = IDGenerator.generate();
        Path targetPath = quickRecordingsDir.resolve(recordingId + "-" + filename);

        try {
            Files.copy(inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to save uploaded file", e);
        }

        LOG.info("Uploaded JFR file saved: path={}", targetPath);

        // Delegate to analyze with original filename as profile name
        return analyzeInternal(targetPath, filename);
    }

    private String analyzeInternal(Path filePath, String profileName) {
        if (!Files.exists(filePath)) {
            throw new IllegalArgumentException("JFR file does not exist: " + filePath);
        }

        String profileId = IDGenerator.generate();
        Instant createdAt = clock.instant();

        LOG.info("Quick analysis started: path={} profileName={}", filePath, profileName);

        // Parse recording information
        RecordingInformation recordingInfo = recordingInformationParser.provide(filePath);

        // Create ProfileInfo without workspace/project (Quick Analysis is independent)
        ProfileInfo profileInfo = new ProfileInfo(
                profileId,
                null,
                null,
                profileName,
                recordingInfo.eventSource(),
                recordingInfo.recordingStartedAt(),
                recordingInfo.recordingFinishedAt(),
                createdAt,
                false  // not enabled until initialization completes
        );

        // Initialize the profile (no recordingId needed for quick analysis)
        profileInitializer.initialize(profileInfo, null, filePath);

        // Write metadata file for filesystem-based resolution
        QuickProfileMetadata metadata = new QuickProfileMetadata(
                profileId,
                profileName,
                recordingInfo.eventSource().name(),
                recordingInfo.recordingStartedAt(),
                recordingInfo.recordingFinishedAt(),
                createdAt);

        writeMetadata(profileId, metadata);

        LOG.info("Quick analysis profile created: profile_id={} profile_name={}", profileId, profileName);

        return profileId;
    }

    private void writeMetadata(String profileId, QuickProfileMetadata metadata) {
        Path metadataPath = QuickProfileMetadata.metadataPath(jeffreyDirs.quickProfileDir(profileId));
        Json.write(metadataPath, metadata);
    }

    private Optional<QuickProfileMetadata> loadMetadata(Path profileDir) {
        Path metadataPath = QuickProfileMetadata.metadataPath(profileDir);
        if (!Files.exists(metadataPath)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Json.read(metadataPath, QuickProfileMetadata.class));
        } catch (RuntimeException e) {
            LOG.warn("Failed to read profile metadata: path={}", metadataPath, e);
            return Optional.empty();
        }
    }

    @Override
    public List<ProfileInfo> listProfiles() {
        Path quickProfilesDir = jeffreyDirs.quickProfiles();
        if (!Files.exists(quickProfilesDir)) {
            return List.of();
        }

        try (Stream<Path> dirs = Files.list(quickProfilesDir)) {
            return dirs.filter(Files::isDirectory)
                    .map(this::loadMetadata)
                    .flatMap(Optional::stream)
                    .map(QuickProfileMetadata::toProfileInfo)
                    .toList();
        } catch (IOException e) {
            LOG.error("Failed to list quick profiles: dir={}", quickProfilesDir, e);
            return List.of();
        }
    }

    @Override
    public Optional<ProfileManager> profile(String profileId) {
        Path profileDir = jeffreyDirs.quickProfileDir(profileId);
        return loadMetadata(profileDir)
                .map(QuickProfileMetadata::toProfileInfo)
                .map(profileManagerFactory);
    }

    @Override
    public void deleteProfile(String profileId) {
        Path profileDir = jeffreyDirs.quickProfileDir(profileId);

        if (!Files.exists(profileDir)) {
            LOG.warn("Quick analysis profile not found for deletion: profile_id={}", profileId);
            return;
        }

        FileSystemUtils.removeDirectory(profileDir);
        LOG.info("Quick analysis profile deleted: profile_id={}", profileId);
    }
}
