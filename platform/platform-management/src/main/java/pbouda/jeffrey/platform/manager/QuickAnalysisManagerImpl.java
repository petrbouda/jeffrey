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

package pbouda.jeffrey.platform.manager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.profile.ProfileInitializer;
import pbouda.jeffrey.profile.manager.ProfileManager;
import pbouda.jeffrey.provider.profile.RecordingInformationParser;
import pbouda.jeffrey.provider.profile.model.recording.RecordingInformation;
import pbouda.jeffrey.shared.common.IDGenerator;
import pbouda.jeffrey.shared.common.Schedulers;
import pbouda.jeffrey.shared.common.filesystem.FileSystemUtils;
import pbouda.jeffrey.shared.common.filesystem.JeffreyDirs;
import pbouda.jeffrey.shared.common.model.ProfileInfo;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class QuickAnalysisManagerImpl implements QuickAnalysisManager {

    private static final Logger LOG = LoggerFactory.getLogger(QuickAnalysisManagerImpl.class);

    /**
     * Synthetic workspace ID for quick analysis profiles.
     */
    public static final String QUICK_ANALYSIS_WORKSPACE_ID = "quick-analysis";

    /**
     * Synthetic project ID for quick analysis profiles.
     */
    public static final String QUICK_ANALYSIS_PROJECT_ID = "quick-analysis";

    private final Clock clock;
    private final JeffreyDirs jeffreyDirs;
    private final RecordingInformationParser recordingInformationParser;
    private final ProfileInitializer profileInitializer;

    /**
     * In-memory storage for quick analysis profiles.
     * Maps profile ID to ProfileInfo.
     */
    private final Map<String, ProfileInfo> quickProfiles = new ConcurrentHashMap<>();

    /**
     * In-memory storage for quick analysis profile managers.
     * Maps profile ID to ProfileManager.
     */
    private final Map<String, ProfileManager> profileManagers = new ConcurrentHashMap<>();

    public QuickAnalysisManagerImpl(
            Clock clock,
            JeffreyDirs jeffreyDirs,
            RecordingInformationParser recordingInformationParser,
            ProfileInitializer profileInitializer) {

        this.clock = clock;
        this.jeffreyDirs = jeffreyDirs;
        this.recordingInformationParser = recordingInformationParser;
        this.profileInitializer = profileInitializer;
    }

    @Override
    public CompletableFuture<String> analyze(Path filePath) {
        String profileName = filePath.getFileName().toString();
        return CompletableFuture.supplyAsync(
                        () -> analyzeInternal(filePath, profileName),
                        Schedulers.sharedVirtual())
                .exceptionally(ex -> {
                    LOG.error("Could not create quick analysis profile: path={} message={}",
                            filePath, ex.getMessage(), ex);
                    throw new RuntimeException("Could not create quick analysis profile: " + filePath, ex);
                });
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

        // Create ProfileInfo with synthetic workspace/project
        ProfileInfo profileInfo = new ProfileInfo(
                profileId,
                QUICK_ANALYSIS_PROJECT_ID,
                QUICK_ANALYSIS_WORKSPACE_ID,
                profileName,
                recordingInfo.eventSource(),
                recordingInfo.recordingStartedAt(),
                recordingInfo.recordingFinishedAt(),
                createdAt,
                false  // not enabled until initialization completes
        );

        // Initialize the profile (no recordingId needed for quick analysis)
        ProfileManager profileManager = profileInitializer.initialize(profileInfo, null, filePath);

        // Create enabled profile info
        ProfileInfo enabledProfileInfo = new ProfileInfo(
                profileId,
                QUICK_ANALYSIS_PROJECT_ID,
                QUICK_ANALYSIS_WORKSPACE_ID,
                profileName,
                recordingInfo.eventSource(),
                recordingInfo.recordingStartedAt(),
                recordingInfo.recordingFinishedAt(),
                createdAt,
                true  // now enabled
        );

        // Store in memory
        quickProfiles.put(profileId, enabledProfileInfo);
        profileManagers.put(profileId, profileManager);

        LOG.info("Quick analysis profile created: profile_id={} profile_name={}", profileId, profileName);

        return profileId;
    }

    @Override
    public List<ProfileInfo> listProfiles() {
        return List.copyOf(quickProfiles.values());
    }

    @Override
    public Optional<ProfileManager> profile(String profileId) {
        ProfileManager manager = profileManagers.get(profileId);
        if (manager != null) {
            return Optional.of(manager);
        }
        return Optional.empty();
    }

    @Override
    public void deleteProfile(String profileId) {
        ProfileInfo profileInfo = quickProfiles.remove(profileId);

        if (profileInfo == null) {
            LOG.warn("Quick analysis profile not found for deletion: profile_id={}", profileId);
            return;
        }

        // Delete profile directory
        Path profileDir = jeffreyDirs.quickProfileDirectory(profileId);
        if (Files.exists(profileDir)) {
            FileSystemUtils.removeDirectory(profileDir);
        }

        LOG.info("Quick analysis profile deleted: profile_id={}", profileId);
    }
}
