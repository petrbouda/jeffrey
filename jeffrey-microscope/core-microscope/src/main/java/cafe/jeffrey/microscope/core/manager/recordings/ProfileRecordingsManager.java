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

package cafe.jeffrey.microscope.core.manager.recordings;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.microscope.core.MicroscopeJeffreyDirs;
import cafe.jeffrey.microscope.persistence.api.MicroscopeCoreRepositories;
import cafe.jeffrey.microscope.persistence.api.ProfileRepository;
import cafe.jeffrey.microscope.persistence.api.RecordingGroup;
import cafe.jeffrey.microscope.persistence.api.RecordingRepository;
import cafe.jeffrey.microscope.persistence.api.RecordingTag;
import cafe.jeffrey.profile.ProfileInitializer;
import cafe.jeffrey.profile.common.pipeline.PipelineRunRegistry;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.provider.profile.api.RecordingInformation;
import cafe.jeffrey.provider.profile.api.RecordingInformationParser;
import cafe.jeffrey.recordings.core.manager.RecordingsCoreManager;
import cafe.jeffrey.shared.common.IDGenerator;
import cafe.jeffrey.shared.common.exception.Exceptions;
import cafe.jeffrey.shared.common.filesystem.FileSystemUtils;
import cafe.jeffrey.shared.common.model.ProfileInfo;
import cafe.jeffrey.shared.common.model.Recording;
import cafe.jeffrey.shared.common.model.RecordingEventSource;
import cafe.jeffrey.shared.common.model.RecordingFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import cafe.jeffrey.shared.notification.NotificationCategory;
import cafe.jeffrey.shared.notification.NotificationType;
import cafe.jeffrey.shared.notification.Notifications;
import cafe.jeffrey.jfr.events.notification.Severity;

/**
 * Microscope's profile-aware {@link RecordingsManager}. Delegates all deployment-agnostic recording
 * store operations to the shared {@link RecordingsCoreManager} and adds the profile-creation /
 * profile-lifecycle operations that are specific to the full microscope deployment.
 */
public class ProfileRecordingsManager implements RecordingsManager {

    private static final Logger LOG = LoggerFactory.getLogger(ProfileRecordingsManager.class);

    private final RecordingsCoreManager core;
    private final Clock clock;
    private final MicroscopeJeffreyDirs jeffreyDirs;
    private final Path recordingsDir;
    private final RecordingInformationParser recordingInformationParser;
    private final ProfileInitializer profileInitializer;
    private final ProfileManager.Factory profileManagerFactory;
    private final MicroscopeCoreRepositories localCoreRepositories;
    private final MicroscopeProfileCleanup profileCleanup;
    private final RecordingRepository recordingRepository;
    private final PipelineRunRegistry<String> runRegistry;
    private final ConcurrentMap<String, CompletableFuture<String>> analyses = new ConcurrentHashMap<>();

    public ProfileRecordingsManager(
            RecordingsCoreManager core,
            Clock clock,
            MicroscopeJeffreyDirs jeffreyDirs,
            Path recordingsDir,
            RecordingInformationParser recordingInformationParser,
            ProfileInitializer profileInitializer,
            ProfileManager.Factory profileManagerFactory,
            MicroscopeCoreRepositories localCoreRepositories,
            MicroscopeProfileCleanup profileCleanup,
            PipelineRunRegistry<String> runRegistry) {

        this.core = core;
        this.clock = clock;
        this.jeffreyDirs = jeffreyDirs;
        this.recordingsDir = recordingsDir;
        this.recordingInformationParser = recordingInformationParser;
        this.profileInitializer = profileInitializer;
        this.profileManagerFactory = profileManagerFactory;
        this.localCoreRepositories = localCoreRepositories;
        this.profileCleanup = profileCleanup;
        this.recordingRepository = localCoreRepositories.newRecordingRepository(null);
        this.runRegistry = runRegistry;
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
    public String createDownloadedRecording(
            String recordingName,
            Path mergedRecordingFile,
            List<Path> artifactFiles,
            Map<String, String> originTags) {
        return core.createDownloadedRecording(recordingName, mergedRecordingFile, artifactFiles, originTags);
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

        if (recording.hasProfile()) {
            String profileId = recording.profileId();
            // A second request while initialization is active joins the attempt already represented
            // by this row. Deleting it would pull the database out from under the parser.
            if (runRegistry.isRunning(profileId)) {
                return profileId;
            }

            ProfileRepository existing = localCoreRepositories.newProfileRepository(profileId);
            if (existing.find().map(ProfileInfo::enabled).orElse(false)) {
                return profileId;
            }

            // Disabled with no live run means either a terminal failed attempt retained for diagnosis,
            // or work interrupted by a process restart. An explicit Analyze is the retry boundary:
            // remove that attempt here, then build a fresh profile id below.
            profileCleanup.deleteProfile(profileId);
        }

        RecordingFile file = recording.files().getFirst();
        String profileId;
        if (recording.eventSource() == RecordingEventSource.HEAP_DUMP) {
            profileId = analyzeHeapDump(recording, file);
        } else {
            profileId = analyzeJfr(recording, file);
        }

        LOG.info("Quick analysis recording analyzed: recordingId={} profileId={}", recordingId, profileId);
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

    private String analyzeJfr(Recording recording, RecordingFile file) {
        Path filePath = resolveRecordingFilePath(file);
        if (!Files.exists(filePath)) {
            throw Exceptions.internal("Recording file does not exist: %s".formatted(filePath));
        }

        String profileId = IDGenerator.generate();
        Instant createdAt = clock.instant();

        RecordingInformation recordingInfo = resolveRecordingInformation(recording, filePath);

        ProfileInfo profileInfo = new ProfileInfo(
                profileId, null, null, file.filename(),
                recordingInfo.eventSource(),
                recordingInfo.recordingStartedAt(),
                recordingInfo.recordingFinishedAt(),
                createdAt, false, false, recording.id());

        // Insert the profile row before initializing, the way the project path does. The recordings
        // list reaches a run's progress through the recording's profile, so a row that appears only
        // once the pipeline has finished leaves the whole run invisible -- the card can say nothing
        // but "Initializing..." for as long as it takes.
        ProfileRepository profileRepository = localCoreRepositories.newProfileRepository(profileId);
        profileRepository.insert(ProfileRepository.InsertProfile.quickProfile(
                file.filename(),
                recordingInfo.eventSource(), createdAt,
                recording.id(),
                recordingInfo.recordingStartedAt(),
                recordingInfo.recordingFinishedAt()));

        try {
            profileInitializer.initialize(profileInfo, null, filePath);
        } catch (RuntimeException e) {
            // Keep the disabled row. It is the durable recording-to-profile link through which the
            // in-memory pipeline can report the failed attempt, and after a restart it is the evidence
            // that an initialization was interrupted rather than never requested. A later explicit
            // Analyze removes it before retrying.
            Notifications.of(NotificationType.PROFILE_ANALYSIS_FAILED)
                    .attribute("recordingId", recording.id())
                    .attribute("profileId", profileId)
                    .errorType(e)
                    .emit();

            throw e;
        }
        profileRepository.enableProfile(createdAt);

        return profileId;
    }

    /**
     * Prefers the recording metadata persisted at upload time (event source + profiling start/end)
     * and re-parses the JFR file only when any of them is missing — e.g. when the metadata parse
     * failed during the upload.
     */
    private RecordingInformation resolveRecordingInformation(Recording recording, Path filePath) {
        boolean persistedInfoComplete = recording.eventSource() != null
                && recording.eventSource() != RecordingEventSource.UNKNOWN
                && recording.recordingStartedAt() != null
                && recording.recordingFinishedAt() != null;

        if (persistedInfoComplete) {
            return new RecordingInformation(
                    FileSystemUtils.size(filePath),
                    recording.eventSource(),
                    recording.recordingStartedAt(),
                    recording.recordingFinishedAt());
        }
        return recordingInformationParser.provide(filePath);
    }

    private String analyzeHeapDump(Recording recording, RecordingFile file) {
        String profileId = IDGenerator.generate();
        Instant createdAt = clock.instant();

        Path heapDumpAnalysisPath = jeffreyDirs.profileDir(profileId).resolve(MicroscopeJeffreyDirs.HEAP_DUMP_ANALYSIS_DIR);
        FileSystemUtils.createDirectories(heapDumpAnalysisPath);

        Path sourcePath = resolveRecordingFilePath(file);
        Path targetPath = heapDumpAnalysisPath.resolve(file.filename());

        try {
            Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to copy heap dump to analysis directory", e);
        }

        ProfileInfo profileInfo = new ProfileInfo(
                profileId, null, null, file.filename(),
                RecordingEventSource.HEAP_DUMP,
                createdAt, createdAt, createdAt, true, false, recording.id());

        ProfileRepository profileRepository = localCoreRepositories.newProfileRepository(profileId);
        profileRepository.insert(ProfileRepository.InsertProfile.quickProfile(
                file.filename(),
                RecordingEventSource.HEAP_DUMP, createdAt,
                recording.id(),
                createdAt, createdAt));
        profileRepository.enableProfile(createdAt);

        profileManagerFactory.apply(profileInfo);

        return profileId;
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
    public Optional<ProfileManager> profile(String profileId) {
        return localCoreRepositories.newProfileRepository(profileId).find()
                .map(profileManagerFactory);
    }

    private Path resolveRecordingFilePath(RecordingFile file) {
        return recordingsDir.resolve(file.recordingId() + "-" + file.filename());
    }
}
