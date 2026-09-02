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

package cafe.jeffrey.microscope.core.mcp.tools;

import cafe.jeffrey.profile.feature.FeatureType;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.profile.manager.heapdump.HeapDumpManager;
import cafe.jeffrey.profile.mcp.McpToolOutput;
import cafe.jeffrey.shared.common.model.ProfileInfo;
import cafe.jeffrey.shared.common.model.RecordingEventSource;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * What can be said about one profile before analysing anything in it: its identity, what it is capable
 * of answering, and where to look at it.
 * <p>
 * Shares the {@code profiles} prefix with {@link ProfilesMcpTools} so the two read as one family, but
 * is registered profile-scoped — which is what puts {@code profileId} in each schema as a required
 * argument rather than an optional one the model may quietly omit.
 */
public class ProfileMcpTools {

    private static final String PROFILE_UI_PATH = "/profiles/%s";

    private final ProfileManager profileManager;

    public ProfileMcpTools(ProfileManager profileManager) {
        this.profileManager = profileManager;
    }

    @Tool(description = "Details of one profile: its identity, the recording window it covers, and how "
            + "much data it holds.")
    public String get() {
        ProfileInfo info = profileManager.info();
        return McpToolOutput.json(new ProfileDetail(
                info.id(),
                info.name(),
                info.projectId(),
                info.workspaceId(),
                info.eventSource().name(),
                info.profilingStartedAt().toString(),
                info.profilingFinishedAt().toString(),
                info.duration().toString(),
                info.createdAt().toString(),
                info.enabled(),
                info.modified(),
                profileManager.sizeInBytes()));
    }

    @Tool(description = "What this profile can answer: which analysis features it has the data for, and "
            + "every event type it recorded with its sample and weight totals. Call this after "
            + "profiles_list to learn whether a profile carries traces, a heap dump or the "
            + "instrumentation dashboards before asking for them.")
    public String features() {
        return McpToolOutput.json(new ProfileCapabilities(
                disabledFeatures().stream().map(Enum::name).sorted().toList(),
                profileManager.flamegraphManager().allEventSummaries().stream()
                        .map(summary -> new RecordedEventType(
                                summary.code(),
                                summary.label(),
                                summary.primary().samples(),
                                summary.primary().weight()))
                        .toList()));
    }

    @Tool(description = "A link that opens this profile in the Jeffrey web UI, for a reader who wants "
            + "to look at the interactive version of what was just analysed.")
    public String link() {
        return ServletUriComponentsBuilder.fromCurrentContextPath()
                .replacePath(PROFILE_UI_PATH.formatted(profileManager.info().id()))
                .toUriString();
    }

    /**
     * The same reasoning {@code ProfileFeaturesController} applies, minus its AI-analysis check: that
     * one describes whether Jeffrey's own assistant is configured, which says nothing about what this
     * profile holds — and the client asking is an assistant already.
     */
    private List<FeatureType> disabledFeatures() {
        List<FeatureType> disabled = new ArrayList<>(profileManager.featuresManager().getDisabledFeatures());
        HeapDumpManager heapDumpManager = profileManager.heapDumpManager();
        if (!heapDumpManager.heapDumpExists() || !heapDumpManager.isCacheReady()) {
            disabled.add(FeatureType.HEAP_DUMP);
        }
        // pprof profiles are aggregated and carry no per-sample timestamps, so the time-resolved views
        // collapse into a single spike and convey no information.
        if (profileManager.info().eventSource() == RecordingEventSource.PPROF) {
            disabled.add(FeatureType.SUBSECOND);
            disabled.add(FeatureType.TIMESERIES);
        }
        return disabled;
    }

    private record ProfileCapabilities(
            List<String> disabledFeatures,
            List<RecordedEventType> eventTypes) {
    }

    private record RecordedEventType(String name, String label, long samples, long weight) {
    }

    private record ProfileDetail(
            String profileId,
            String name,
            String projectId,
            String workspaceId,
            String eventSource,
            String recordingStartedAt,
            String recordingFinishedAt,
            String duration,
            String createdAt,
            boolean enabled,
            boolean modified,
            long sizeInBytes) {
    }
}
