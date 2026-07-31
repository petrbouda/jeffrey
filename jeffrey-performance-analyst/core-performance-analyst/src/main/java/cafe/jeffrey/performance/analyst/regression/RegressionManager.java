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

package cafe.jeffrey.performance.analyst.regression;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.performance.analyst.flamegraph.FlamegraphAiPrompt;
import cafe.jeffrey.performance.analyst.flamegraph.RecordingAiPromptManager;
import cafe.jeffrey.performance.analyst.settings.ProjectAiSettings;
import cafe.jeffrey.performance.analyst.settings.ProjectAiSettingsResolver;
import cafe.jeffrey.shared.common.exception.Exceptions;

import java.util.List;

/**
 * Compares one recording's profile against an earlier baseline recording of the same event type.
 *
 * <p>Both sides come from the cached prompt index, so a comparison costs two SQLite reads and no JFR
 * parsing. That matters because the comparison is the cheap, deterministic step that makes the
 * expensive one worthwhile: once the moved frames are known, an analysis can be scoped to just those
 * instead of re-examining a whole profile that is mostly unchanged.</p>
 */
public class RegressionManager {

    private static final Logger LOG = LoggerFactory.getLogger(RegressionManager.class);

    private final RecordingAiPromptManager promptManager;
    private final ProjectAiSettingsResolver settingsResolver;

    public RegressionManager(
            RecordingAiPromptManager promptManager, ProjectAiSettingsResolver settingsResolver) {
        this.promptManager = promptManager;
        this.settingsResolver = settingsResolver;
    }

    /**
     * Compares {@code recordingId} against {@code baselineRecordingId} for one event type, using the
     * project's regression threshold.
     */
    public ProfileComparison compare(
            String projectId, String baselineRecordingId, String recordingId, String eventType) {

        ProfileFrameIndexPair pair = new ProfileFrameIndexPair(
                indexFor(baselineRecordingId, eventType, projectId, "baseline"),
                indexFor(recordingId, eventType, projectId, "current"));

        ProjectAiSettings settings = settingsResolver.resolve(projectId);
        ProfileComparison comparison = ProfileComparator.compare(
                eventType, pair.baseline().frameIndex(), pair.current().frameIndex(),
                settings.regressionThresholdPp());

        LOG.info("Compared profiles: project_id={} baseline_recording_id={} recording_id={} event_type={} "
                        + "regressed={} improved={} threshold_pp={}",
                projectId, baselineRecordingId, recordingId, eventType,
                comparison.regressed().size(), comparison.improved().size(), settings.regressionThresholdPp());
        return comparison;
    }

    private FlamegraphAiPrompt indexFor(String recordingId, String eventType, String projectId, String side) {
        List<FlamegraphAiPrompt> prompts = promptManager.getPrompts(recordingId, projectId);
        return prompts.stream()
                .filter(prompt -> prompt.eventType().equals(eventType))
                .findFirst()
                .orElseThrow(() -> Exceptions.invalidRequest(
                        "The " + side + " recording has no " + eventType + " profile to compare."));
    }

    /**
     * The two sides of a comparison, named so the call site cannot silently swap them — a reversed
     * baseline turns every regression into an improvement and reads as good news.
     */
    private record ProfileFrameIndexPair(FlamegraphAiPrompt baseline, FlamegraphAiPrompt current) {
    }
}
