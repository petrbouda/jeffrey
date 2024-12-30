/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
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

package pbouda.jeffrey.manager;

import com.fasterxml.jackson.databind.node.ObjectNode;
import pbouda.jeffrey.common.EventSummary;
import pbouda.jeffrey.common.ProfilingStartEnd;
import pbouda.jeffrey.common.Schedulers;
import pbouda.jeffrey.common.Type;
import pbouda.jeffrey.common.config.Config;
import pbouda.jeffrey.common.filesystem.ProfileDirs;
import pbouda.jeffrey.common.model.ProfileInfo;
import pbouda.jeffrey.flamegraph.GraphGenerator;
import pbouda.jeffrey.jfrparser.api.ProcessableEvents;
import pbouda.jeffrey.model.EventSummaryResult;
import pbouda.jeffrey.profile.settings.ActiveSettingsProvider;
import pbouda.jeffrey.profile.summary.ParsingEventSummaryProvider;
import pbouda.jeffrey.repository.GraphRepository;
import pbouda.jeffrey.repository.model.GraphInfo;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class DiffgraphManagerImpl extends AbstractFlamegraphManager {

    private static final List<Type> SUPPORTED_EVENTS = List.of(
            Type.EXECUTION_SAMPLE,
            Type.WALL_CLOCK_SAMPLE,
            Type.OBJECT_ALLOCATION_SAMPLE,
            Type.OBJECT_ALLOCATION_IN_NEW_TLAB,
            Type.OBJECT_ALLOCATION_OUTSIDE_TLAB);

    private final ProfileInfo primaryProfileInfo;
    private final ProfileInfo secondaryProfileInfo;
    private final ActiveSettingsProvider primarySettingsProvider;
    private final ActiveSettingsProvider secondarySettingsProvider;
    private final GraphGenerator generator;
    private final Path primaryRecordingDir;
    private final Path secondaryRecordingDir;
    private final ProfileDirs primaryProfileDirs;
    private final ProfileDirs secondaryProfileDirs;

    public DiffgraphManagerImpl(
            ProfileInfo primaryProfileInfo,
            ProfileInfo secondaryProfileInfo,
            ProfileDirs primaryProfileDirs,
            ProfileDirs secondaryProfileDirs,
            ActiveSettingsProvider primarySettingsProvider,
            ActiveSettingsProvider secondarySettingsProvider,
            GraphRepository repository,
            GraphGenerator generator) {

        super(primaryProfileInfo, repository);
        this.primaryProfileDirs = primaryProfileDirs;
        this.secondaryProfileDirs = secondaryProfileDirs;
        this.primaryRecordingDir = primaryProfileDirs.recordingsDir();
        this.secondaryRecordingDir = secondaryProfileDirs.recordingsDir();
        this.primaryProfileInfo = primaryProfileInfo;
        this.secondaryProfileInfo = secondaryProfileInfo;
        this.primarySettingsProvider = primarySettingsProvider;
        this.secondarySettingsProvider = secondarySettingsProvider;
        this.generator = generator;
    }

    @Override
    public List<EventSummaryResult> eventSummaries() {
        CompletableFuture<List<EventSummary>> primaryFuture = CompletableFuture.supplyAsync(() -> {
            return new ParsingEventSummaryProvider(primarySettingsProvider,
                    primaryProfileDirs.allRecordingPaths(),
                    new ProcessableEvents(SUPPORTED_EVENTS)).get();
        }, Schedulers.parallel());

        CompletableFuture<List<EventSummary>> secondaryFuture = CompletableFuture.supplyAsync(() -> {
            return new ParsingEventSummaryProvider(
                    secondarySettingsProvider,
                    secondaryProfileDirs.allRecordingPaths(),
                    new ProcessableEvents(SUPPORTED_EVENTS)).get();
        }, Schedulers.parallel());

        CompletableFuture.allOf(primaryFuture, secondaryFuture).join();

        List<EventSummary> primaryEvents = primaryFuture.join();
        List<EventSummary> secondaryEvents = secondaryFuture.join();

        List<EventSummaryResult> results = new ArrayList<>();
        for (EventSummary primary : primaryEvents) {
            Optional<EventSummary> secondaryOpt = findEventType(secondaryEvents, primary.name());
            if (secondaryOpt.isPresent()) {
                EventSummaryResult result = new EventSummaryResult(primary, secondaryOpt.get());
                results.add(result);
            }
        }

        return results;
    }

    private static Optional<EventSummary> findEventType(List<EventSummary> secondary, String eventName) {
        return secondary.stream()
                .filter(e -> eventName.equals(e.name()))
                .findFirst();
    }

    @Override
    public ObjectNode generate(Generate generateRequest) {
        // Baseline is the secondary profile and comparison is the "new one" - primary
        Config config = Config.differentialBuilder()
                .withPrimaryRecordingDir(primaryRecordingDir)
                .withPrimaryStartEnd(new ProfilingStartEnd(primaryProfileInfo.startedAt(), primaryProfileInfo.endedAt()))
                .withSecondaryRecordingDir(secondaryRecordingDir)
                .withSecondaryStartEnd(new ProfilingStartEnd(secondaryProfileInfo.startedAt(), secondaryProfileInfo.endedAt()))
                .withGraphParameters(generateRequest.graphParameters())
                .withEventType(generateRequest.eventType())
                .withTimeRange(generateRequest.timeRange())
                .build();

        return generator.generate(config);
    }

    @Override
    public void save(Generate generateRequest, String flamegraphName) {
        GraphInfo graphInfo = GraphInfo.custom(
                primaryProfileInfo.id(),
                generateRequest.graphParameters().threadMode(),
                generateRequest.graphParameters().collectWeight(),
                flamegraphName);

        Config config = Config.differentialBuilder()
                .withPrimaryRecordingDir(primaryRecordingDir)
                .withPrimaryStartEnd(new ProfilingStartEnd(primaryProfileInfo.startedAt(), primaryProfileInfo.endedAt()))
                .withSecondaryRecordingDir(secondaryRecordingDir)
                .withSecondaryStartEnd(new ProfilingStartEnd(secondaryProfileInfo.startedAt(), secondaryProfileInfo.endedAt()))
                .withEventType(generateRequest.eventType())
                .withGraphParameters(generateRequest.graphParameters())
                .withTimeRange(generateRequest.timeRange())
                .build();

        generateAndSave(graphInfo, () -> generator.generate(config));
    }
}
