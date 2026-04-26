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

package cafe.jeffrey.profile.manager;

import tools.jackson.databind.JsonNode;
import cafe.jeffrey.profile.common.event.GarbageCollectorType;
import cafe.jeffrey.shared.common.model.ProfileInfo;
import cafe.jeffrey.shared.common.model.Type;
import cafe.jeffrey.shared.common.model.time.RelativeTimeRange;
import cafe.jeffrey.profile.manager.builder.ConcurrentGCOverviewEventBuilder;
import cafe.jeffrey.profile.manager.builder.G1GCOverviewEventBuilder;
import cafe.jeffrey.profile.manager.builder.GCConfigurationEventBuilder;
import cafe.jeffrey.profile.manager.builder.NonConcurrentGCOverviewEventBuilder;
import cafe.jeffrey.profile.manager.model.gc.GCGenerationTimeseriesBuilder;
import cafe.jeffrey.profile.manager.model.gc.GCOverviewData;
import cafe.jeffrey.profile.manager.model.gc.GCTimeseriesType;
import cafe.jeffrey.profile.manager.model.gc.configuration.GCConfigurationData;
import cafe.jeffrey.provider.profile.builder.RecordBuilder;
import cafe.jeffrey.provider.profile.repository.EventQueryConfigurer;
import cafe.jeffrey.provider.profile.repository.ProfileEventRepository;
import cafe.jeffrey.provider.profile.repository.ProfileEventStreamRepository;
import cafe.jeffrey.provider.profile.model.GenericRecord;
import cafe.jeffrey.timeseries.TimeseriesData;

import java.util.List;

public class GarbageCollectionManagerImpl implements GarbageCollectionManager {

    private static final int MAX_LONGEST_PAUSES = 20;

    private final ProfileInfo profileInfo;
    private final ProfileEventRepository eventRepository;
    private final ProfileEventStreamRepository eventStreamRepository;

    public GarbageCollectionManagerImpl(
            ProfileInfo profileInfo,
            ProfileEventRepository eventRepository,
            ProfileEventStreamRepository eventStreamRepository) {
        this.profileInfo = profileInfo;
        this.eventRepository = eventRepository;
        this.eventStreamRepository = eventStreamRepository;
    }

    @Override
    public GarbageCollectorType garbageCollectorType() {
        List<JsonNode> gcConfigurationFields = eventRepository.eventsByTypeWithFields(Type.GC_CONFIGURATION);
        if (!gcConfigurationFields.isEmpty()) {
            JsonNode gcConfiguration = gcConfigurationFields.getFirst();

            String oldCollector = gcConfiguration.get("oldCollector").asString();
            return GarbageCollectorType.fromOldGenCollector(oldCollector);
        } else {
            throw new IllegalStateException("No GC configuration event found in the profile.");
        }
    }

    @Override
    public GCOverviewData overviewData() {
        RelativeTimeRange timeRange = new RelativeTimeRange(profileInfo.profilingStartEnd());

        GarbageCollectorType gcType = garbageCollectorType();
        EventQueryConfigurer configurer = new EventQueryConfigurer()
                .withEventTypes(List.of(
                        Type.GARBAGE_COLLECTION,
                        Type.GC_HEAP_SUMMARY,
                        Type.YOUNG_GARBAGE_COLLECTION,
                        Type.OLD_GARBAGE_COLLECTION,
                        Type.G1_GARBAGE_COLLECTION,
                        Type.GC_PHASE_CONCURRENT
                ))
                .withJsonFields();

        RecordBuilder<GenericRecord, GCOverviewData> builder = switch (gcType) {
            case SERIAL -> nonConcurrentGCBuilder(GarbageCollectorType.SERIAL, timeRange);
            case PARALLEL -> nonConcurrentGCBuilder(GarbageCollectorType.PARALLEL, timeRange);
            case G1 -> new G1GCOverviewEventBuilder(timeRange, MAX_LONGEST_PAUSES);
            case Z -> concurrentGCBuilder(GarbageCollectorType.Z, timeRange);
            case SHENANDOAH -> concurrentGCBuilder(GarbageCollectorType.SHENANDOAH, timeRange);
            case ZGENERATIONAL -> concurrentGCBuilder(GarbageCollectorType.ZGENERATIONAL, timeRange);
        };

        return eventStreamRepository.genericStreaming(configurer, builder);
    }

    private NonConcurrentGCOverviewEventBuilder nonConcurrentGCBuilder(GarbageCollectorType gcType, RelativeTimeRange timeRange) {
        return new NonConcurrentGCOverviewEventBuilder(
                gcType,
                timeRange,
                MAX_LONGEST_PAUSES,
                Type.YOUNG_GARBAGE_COLLECTION,
                Type.OLD_GARBAGE_COLLECTION);
    }

    private ConcurrentGCOverviewEventBuilder concurrentGCBuilder(GarbageCollectorType gcType, RelativeTimeRange timeRange) {
        return new ConcurrentGCOverviewEventBuilder(
                gcType,
                timeRange,
                MAX_LONGEST_PAUSES,
                Type.YOUNG_GARBAGE_COLLECTION,
                Type.OLD_GARBAGE_COLLECTION);
    }

    @Override
    public TimeseriesData timeseries(GCTimeseriesType timeseriesType) {
        RelativeTimeRange timeRange = new RelativeTimeRange(profileInfo.profilingStartEnd());
        GarbageCollectorType gcType = garbageCollectorType();

        EventQueryConfigurer configurer = new EventQueryConfigurer()
                .withEventType(Type.GARBAGE_COLLECTION)
                .withJsonFields();

        return eventStreamRepository.genericStreaming(
                configurer,
                new GCGenerationTimeseriesBuilder(timeRange, timeseriesType, gcType));
    }

    @Override
    public GCConfigurationData configuration() {
        EventQueryConfigurer configurer = new EventQueryConfigurer()
                .withEventTypes(List.of(
                        Type.GC_CONFIGURATION,
                        Type.GC_HEAP_CONFIGURATION,
                        Type.GC_TLAB_CONFIGURATION,
                        Type.GC_SURVIVOR_CONFIGURATION,
                        Type.YOUNG_GENERATION_CONFIGURATION
                ))
                .withJsonFields();

        return eventStreamRepository.genericStreaming(configurer, new GCConfigurationEventBuilder());
    }
}
