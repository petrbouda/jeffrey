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

package cafe.jeffrey.microscope.core.manager.recordings;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.microscope.core.manager.recordings.builder.ProfileBuilder;
import cafe.jeffrey.microscope.core.manager.recordings.builder.ProfileBuilders;
import cafe.jeffrey.microscope.persistence.api.MicroscopeCoreRepositories;
import cafe.jeffrey.microscope.persistence.api.RecordingGroup;
import cafe.jeffrey.microscope.persistence.api.RecordingRepository;
import cafe.jeffrey.microscope.persistence.api.RecordingTag;
import cafe.jeffrey.profile.common.pipeline.PipelineProgress;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.recordings.core.manager.RecordingsCoreManager;
import cafe.jeffrey.shared.common.exception.Exceptions;
import cafe.jeffrey.storage.recording.api.file.Bundle;
import cafe.jeffrey.storage.recording.api.file.Recording;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Microscope's profile-aware {@link RecordingsManager}. Delegates all deployment-agnostic recording
 * store operations to the shared {@link RecordingsCoreManager} and adds the profile-creation /
 * profile-lifecycle operations that are specific to the full microscope deployment.
 * <p>
 * A recording becomes at most one profile, built by the {@link ProfileBuilder} of its kind.
 */
public class ProfileRecordingsManager implements RecordingsManager {

    private static final Logger LOG = LoggerFactory.getLogger(ProfileRecordingsManager.class);

    private final RecordingsCoreManager core;
    private final ProfileManager.Factory profileManagerFactory;
    private final MicroscopeCoreRepositories localCoreRepositories;
    private final MicroscopeProfileCleanup profileCleanup;
    private final RecordingRepository recordingRepository;
    private final ProfileBuilders builders;
    private final ConcurrentMap<String, CompletableFuture<String>> analyses = new ConcurrentHashMap<>();

    public ProfileRecordingsManager(
            RecordingsCoreManager core,
            ProfileManager.Factory profileManagerFactory,
            MicroscopeCoreRepositories localCoreRepositories,
            MicroscopeProfileCleanup profileCleanup,
            ProfileBuilders builders) {

        this.core = core;
        this.profileManagerFactory = profileManagerFactory;
        this.localCoreRepositories = localCoreRepositories;
        this.profileCleanup = profileCleanup;
        this.recordingRepository = localCoreRepositories.newRecordingRepository();
        this.builders = builders;
    }

    // --- Delegated store operations (deployment-agnostic) ---

    @Override
    public String createGroup(String groupName) {
        return core.createGroup(groupName);
    }

    @Override
    public List<RecordingGroup> listGroups() {
        return core.listGroups();
    }

    @Override
    public void deleteGroup(String groupId) {
        core.deleteGroup(groupId);
    }

    @Override
    public String uploadRecording(String filename, InputStream inputStream, String groupId) {
        return core.uploadRecording(filename, inputStream, groupId);
    }

    @Override
    public String importRecordingFromPath(Path path) {
        return core.importRecordingFromPath(path);
    }

    @Override
    public Optional<Recording> findByFileNameAndSize(String fileName, long sizeInBytes) {
        return core.findByFileNameAndSize(fileName, sizeInBytes);
    }

    @Override
    public String createDownloadedBundle(
            String bundleName,
            List<Path> recordingFiles,
            List<Path> artifactFiles,
            Map<String, String> originTags) {
        return core.createDownloadedBundle(bundleName, recordingFiles, artifactFiles, originTags);
    }

    @Override
    public void moveRecordingToGroup(String recordingId, String groupId) {
        core.moveRecordingToGroup(recordingId, groupId);
    }

    @Override
    public List<Recording> listRecordings() {
        return core.listRecordings();
    }

    @Override
    public Optional<Recording> findRecording(String recordingId) {
        return core.findRecording(recordingId);
    }

    @Override
    public Map<String, List<RecordingTag>> tagsForRecordings(Collection<String> recordingIds) {
        return core.tagsForRecordings(recordingIds);
    }

    @Override
    public void deleteRecording(String recordingId) {
        core.deleteRecording(recordingId);
    }

    @Override
    public Optional<Path> findRecordingFile(String recordingId, String fileId) {
        return core.findRecordingFile(recordingId, fileId);
    }

    @Override
    public List<Bundle> listBundles() {
        return core.listBundles();
    }

    @Override
    public Optional<Bundle> findBundle(String bundleId) {
        return core.findBundle(bundleId);
    }

    @Override
    public List<Recording> findBundleMembers(String bundleId) {
        return core.findBundleMembers(bundleId);
    }

    @Override
    public void moveBundleToGroup(String bundleId, String groupId) {
        core.moveBundleToGroup(bundleId, groupId);
    }

    @Override
    public void deleteBundle(String bundleId) {
        core.deleteBundle(bundleId);
    }

    @Override
    public Optional<Path> findBundleFile(String bundleId, String fileId) {
        return core.findBundleFile(bundleId, fileId);
    }

    // --- Profile creation (microscope-specific) ---

    @Override
    public String analyzeRecording(String recordingId) {
        AtomicBoolean started = new AtomicBoolean();
        CompletableFuture<String> analysis = analyses.computeIfAbsent(recordingId, _ -> {
            started.set(true);
            return new CompletableFuture<>();
        });

        if (!started.get()) {
            return join(analysis);
        }

        try {
            String profileId = analyzeRecordingOnce(recordingId);
            analysis.complete(profileId);
            return profileId;
        } catch (RuntimeException | Error e) {
            analysis.completeExceptionally(e);
            throw e;
        } finally {
            // Complete first and remove second. A concurrent caller that already found this attempt
            // joins its exact result; a call beginning after this method returns starts a retry.
            analyses.remove(recordingId, analysis);
        }
    }

    private String analyzeRecordingOnce(String recordingId) {
        Recording recording = recordingRepository.findRecording(recordingId)
                .orElseThrow(() -> Exceptions.recordingNotFound(recordingId));

        ProfileBuilder builder = builders.of(recording.eventSource());
        if (recording.hasProfile()) {
            Optional<String> reused = builder.reuse(recording);
            if (reused.isPresent()) {
                return reused.get();
            }
        }

        String profileId = builder.build(recording);
        LOG.info("Quick analysis recording analyzed: recordingId={} profileId={} eventSource={}",
                recordingId, profileId, recording.eventSource());
        return profileId;
    }

    private static String join(CompletableFuture<String> analysis) {
        try {
            return analysis.join();
        } catch (CompletionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw e;
        }
    }

    @Override
    public void updateProfileName(String profileId, String profileName) {
        localCoreRepositories.newProfileRepository(profileId).update(profileName);
        LOG.info("Quick analysis profile renamed: profileId={} newName={}", profileId, profileName);
    }

    @Override
    public void deleteProfile(String recordingId) {
        Recording recording = recordingRepository.findRecording(recordingId)
                .orElseThrow(() -> Exceptions.recordingNotFound(recordingId));

        if (!recording.hasProfile()) {
            throw Exceptions.invalidRequest("Recording has no profile: %s".formatted(recordingId));
        }

        profileCleanup.deleteProfile(recording.profileId());

        LOG.info("Quick analysis profile deleted: recordingId={} profileId={}", recordingId, recording.profileId());
    }

    @Override
    public PipelineProgress initProgress(Recording recording) {
        return builders.of(recording.eventSource()).progress(recording.profileId());
    }

    @Override
    public Optional<ProfileManager> profile(String profileId) {
        return localCoreRepositories.newProfileRepository(profileId).find()
                .map(profileManagerFactory);
    }
}
