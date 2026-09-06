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

import cafe.jeffrey.microscope.core.web.ProfileManagerResolver;
import cafe.jeffrey.microscope.core.web.dto.response.IdeRecordingStateResponse;
import cafe.jeffrey.microscope.core.web.dto.response.IdeRecordingStateResponse.Finding;
import cafe.jeffrey.microscope.core.web.dto.response.IdeRecordingStateResponse.HeapFigures;
import cafe.jeffrey.microscope.core.web.dto.response.IdeRecordingStateResponse.Kind;
import cafe.jeffrey.microscope.core.web.dto.response.IdeRecordingStateResponse.ProfileSummary;
import cafe.jeffrey.microscope.core.web.dto.response.IdeRecordingStateResponse.RecordingFigures;
import cafe.jeffrey.microscope.core.web.dto.response.IdeRecordingStateResponse.State;
import cafe.jeffrey.profile.common.analysis.AutoAnalysisResult;
import cafe.jeffrey.profile.common.pipeline.PipelineRunRegistry;
import cafe.jeffrey.profile.feature.FeatureType;
import cafe.jeffrey.profile.heapdump.model.HeapSummary;
import cafe.jeffrey.profile.manager.heapdump.HeapDumpManager;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.provider.profile.api.CpuTimeSampleLoss;
import cafe.jeffrey.shared.common.model.Recording;
import cafe.jeffrey.shared.common.model.RecordingEventSource;
import cafe.jeffrey.shared.common.model.repository.SupportedRecordingFile;
import cafe.jeffrey.shared.common.model.RecordingFile;
import cafe.jeffrey.shared.common.InstantUtils;
import cafe.jeffrey.shared.common.model.ProfileInfo;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Matches a file on the developer's disk to what Microscope already holds, for the IDE panel.
 *
 * <p>The match is by <b>file name and byte size</b>, because that is all there is to match on: an
 * import copies the file into Microscope's own storage and records no origin path, so the absolute
 * path the IDE sends exists nowhere in the database. Name and size together are a strong signal —
 * two different recordings that agree on both are, in practice, the same recording imported twice —
 * and the failure mode is the friendly one: an unrecognised file offers to be analysed, which is
 * what the panel would offer anyway.
 *
 * <p>Kept out of the controller because the rule above is the part worth testing, and a rule that
 * lives in a request handler is a rule that gets tested through HTTP or not at all.
 */
public class IdeRecordingLookup {

    /**
     * How many findings the panel shows. Auto-analysis is ordered by severity, so the first few are
     * the ones worth acting on; the rest are what the auto-analysis page is for.
     */
    private static final int FINDINGS_LIMIT = 5;

    private final RecordingsManager recordingsManager;
    private final ProfileManagerResolver profileManagerResolver;
    private final PipelineRunRegistry<String> profileInitRunRegistry;

    public IdeRecordingLookup(
            RecordingsManager recordingsManager,
            ProfileManagerResolver profileManagerResolver,
            PipelineRunRegistry<String> profileInitRunRegistry) {

        this.recordingsManager = recordingsManager;
        this.profileManagerResolver = profileManagerResolver;
        this.profileInitRunRegistry = profileInitRunRegistry;
    }

    public IdeRecordingStateResponse byPath(Path path, long sizeInBytes) {
        String filename = path.getFileName().toString();

        List<Recording> candidates = recordingsManager.listRecordings().stream()
                .filter(recording -> matches(recording, filename, sizeInBytes))
                .toList();

        // A recording with a profile wins over a newer one without, and only then does newest win.
        // Importing the same file twice is ordinary — the panel's own "Analyze again" does it, and so
        // does dragging the file into the web UI — and ranking purely by import time would let a bare
        // second copy hide an analysis that already exists, offering to rebuild what is right there.
        Optional<Recording> match = candidates.stream()
                .max(Comparator.comparing(Recording::hasProfile)
                        .thenComparing(Recording::createdAt));

        if (match.isEmpty()) {
            return IdeRecordingStateResponse.notImported(filename, sizeInBytes);
        }

        Recording recording = match.get();
        if (!recording.hasProfile()) {
            return new IdeRecordingStateResponse(
                    State.IMPORTED.name(), recording.id(), null, filename, sizeInBytes, null);
        }

        String profileId = recording.profileId();
        if (profileInitRunRegistry.isRunning(profileId)) {
            return new IdeRecordingStateResponse(
                    State.ANALYZING.name(), recording.id(), profileId, filename, sizeInBytes, null);
        }

        return profileManagerResolver.find(profileId)
                .map(profileManager -> new IdeRecordingStateResponse(
                        State.READY.name(),
                        recording.id(),
                        profileId,
                        filename,
                        sizeInBytes,
                        summarize(profileManager, filename)))
                // A recording that claims a profile the resolver cannot open describes a profile that
                // was deleted underneath it. Offering to analyse again beats reporting a broken link.
                .orElseGet(() -> new IdeRecordingStateResponse(
                        State.IMPORTED.name(), recording.id(), null, filename, sizeInBytes, null));
    }

    private static boolean matches(Recording recording, String filename, long sizeInBytes) {
        return recording.files().stream()
                .anyMatch(file -> file.filename().equals(filename) && sizeMatches(file, sizeInBytes));
    }

    /**
     * A size of zero means the caller could not stat the file, so the name alone decides. Anything
     * else has to agree exactly.
     */
    private static boolean sizeMatches(RecordingFile file, long sizeInBytes) {
        return sizeInBytes <= 0 || file.sizeInBytes() == sizeInBytes;
    }

    private ProfileSummary summarize(ProfileManager profileManager, String filename) {
        ProfileInfo info = profileManager.info();
        List<AutoAnalysisResult> findings = profileManager.autoAnalysisManager().analysisResults();
        boolean heapDump = isHeapDump(filename);

        return new ProfileSummary(
                heapDump ? Kind.HEAP_DUMP : Kind.RECORDING,
                info.name(),
                InstantUtils.toEpochMilli(info.profilingStartedAt()),
                InstantUtils.toEpochMilli(info.profilingFinishedAt()),
                heapDump ? null : recordingFigures(profileManager, info),
                heapDump ? heapFigures(profileManager) : null,
                !findings.isEmpty(),
                findings.stream()
                        .limit(FINDINGS_LIMIT)
                        .map(result -> new Finding(
                                result.rule(),
                                result.severity() == null ? null : result.severity().name(),
                                result.summary()))
                        .toList(),
                disabledFeatures(profileManager));
    }

    /**
     * What the developer double-clicked decides the kind, not what the profile happens to carry. A
     * recording can have a heap dump attached to it, and opening the {@code .jfr} should still show
     * the recording's figures rather than the dump's.
     */
    private static boolean isHeapDump(String filename) {
        SupportedRecordingFile type = SupportedRecordingFile.of(filename);
        return type == SupportedRecordingFile.HEAP_DUMP || type == SupportedRecordingFile.HEAP_DUMP_GZ;
    }

    private static RecordingFigures recordingFigures(ProfileManager profileManager, ProfileInfo info) {
        CpuTimeSampleLoss loss = profileManager.samplerHealthManager().cpuTimeSampleLoss();

        long sampleCount = 0;
        int eventTypeCount = 0;
        for (var summary : profileManager.flamegraphManager().allEventSummaries()) {
            sampleCount += summary.primary().samples();
            eventTypeCount++;
        }

        return new RecordingFigures(
                info.duration() == null ? 0L : info.duration().toMillis(),
                sampleCount,
                eventTypeCount,
                loss == null ? 0L : loss.capturedSamples(),
                loss == null ? 0L : loss.lostSamples());
    }

    /**
     * An un-indexed dump reports zeroes and says so rather than being asked for a summary it cannot
     * produce: building the index is minutes of work on a large heap, and the panel is not the place
     * to start it by accident.
     */
    private static HeapFigures heapFigures(ProfileManager profileManager) {
        HeapDumpManager heapDumpManager = profileManager.heapDumpManager();
        if (!heapDumpManager.heapDumpExists() || !heapDumpManager.isCacheReady()) {
            return new HeapFigures(0L, 0L, 0, 0, false);
        }

        HeapSummary summary = heapDumpManager.getSummary();
        if (summary == null) {
            return new HeapFigures(0L, 0L, 0, 0, false);
        }
        return new HeapFigures(
                summary.totalBytes(),
                summary.totalInstances(),
                summary.classCount(),
                summary.gcRootCount(),
                true);
    }

    /**
     * Which features this profile has no data for, so the panel can mark a view as empty rather than
     * link the reader to a page with nothing on it.
     *
     * <p>Narrower than {@code ProfileFeaturesController.disabledFeatures}, on purpose: that one also
     * reports whether Jeffrey's own AI assistant is configured, which says nothing about what this
     * profile holds, and the panel offers no AI view to disable. The two heap-dump conditions are kept
     * because a heap dump that exists but has no cache built is, to a reader, a view with nothing in
     * it — the same thing.
     */
    private static List<String> disabledFeatures(ProfileManager profileManager) {
        List<FeatureType> disabled = new ArrayList<>(profileManager.featuresManager().getDisabledFeatures());

        HeapDumpManager heapDumpManager = profileManager.heapDumpManager();
        if (!heapDumpManager.heapDumpExists() || !heapDumpManager.isCacheReady()) {
            disabled.add(FeatureType.HEAP_DUMP);
        }
        // pprof profiles are aggregated and carry no per-sample timestamps, so the time-resolved views
        // collapse into a single spike and convey nothing.
        if (profileManager.info().eventSource() == RecordingEventSource.PPROF) {
            disabled.add(FeatureType.SUBSECOND);
            disabled.add(FeatureType.TIMESERIES);
        }

        return disabled.stream().map(Enum::name).distinct().sorted().toList();
    }
}
