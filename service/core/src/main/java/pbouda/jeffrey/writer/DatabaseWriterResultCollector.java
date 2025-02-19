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

package pbouda.jeffrey.writer;

import com.fasterxml.jackson.databind.JsonNode;
import org.eclipse.collections.api.factory.primitive.ObjectLongMaps;
import org.eclipse.collections.api.map.primitive.MutableObjectLongMap;
import pbouda.jeffrey.common.Collector;
import pbouda.jeffrey.common.EventSource;
import pbouda.jeffrey.common.Type;
import pbouda.jeffrey.common.model.profile.EventThread;
import pbouda.jeffrey.common.model.profile.EventType;
import pbouda.jeffrey.profile.settings.ActiveSetting;
import pbouda.jeffrey.profile.settings.ActiveSettings;
import pbouda.jeffrey.profile.settings.SettingNameLabel;
import pbouda.jeffrey.profile.viewer.ProfileViewerUtils;
import pbouda.jeffrey.writer.enhancer.*;
import pbouda.jeffrey.writer.profile.BatchingDatabaseWriter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Stream;

public class DatabaseWriterResultCollector implements Collector<DatabaseWriterResult, Void> {

    private final BatchingDatabaseWriter<EventType> eventTypeWriter;
    private final BatchingDatabaseWriter<EventThread> threadWriter;

    public DatabaseWriterResultCollector(
            BatchingDatabaseWriter<EventType> eventTypeWriter,
            BatchingDatabaseWriter<EventThread> threadWriter) {
        this.eventTypeWriter = eventTypeWriter;
        this.threadWriter = threadWriter;
    }

    @Override
    public Supplier<DatabaseWriterResult> empty() {
        return () -> new DatabaseWriterResult(
                new ArrayList<>(),
                new ArrayList<>(),
                ObjectLongMaps.immutable.empty(),
                ObjectLongMaps.immutable.empty(),
                new HashMap<>());
    }

    @Override
    public DatabaseWriterResult combiner(DatabaseWriterResult partial1, DatabaseWriterResult partial2) {
        List<jdk.jfr.EventType> events = new ArrayList<>();
        Stream.concat(partial1.eventTypes().stream(), partial2.eventTypes().stream())
                .filter(type -> !containsEvent(events, type))
                .forEach(events::add);

        MutableObjectLongMap<Type> samplesCollector = ObjectLongMaps.mutable.empty();
        partial1.samples().keySet()
                .forEach(type -> samplesCollector.addToValue(type, partial1.samples().get(type)));
        partial2.samples().keySet()
                .forEach(type -> samplesCollector.addToValue(type, partial2.samples().get(type)));

        MutableObjectLongMap<Type> weightCollector = ObjectLongMaps.mutable.empty();
        partial1.weight().keySet()
                .forEach(type -> weightCollector.addToValue(type, partial1.weight().get(type)));
        partial2.weight().keySet()
                .forEach(type -> weightCollector.addToValue(type, partial2.weight().get(type)));

        Map<SettingNameLabel, ActiveSetting> activeSettings =
                combineActiveSettings(partial1.activeSettings(), partial2.activeSettings());

        List<EventThread> threads = new ArrayList<>();
        threads.addAll(partial1.eventThreads());
        threads.addAll(partial2.eventThreads());

        return new DatabaseWriterResult(threads, events, samplesCollector, weightCollector, activeSettings);
    }

    private static boolean containsEvent(List<jdk.jfr.EventType> events, jdk.jfr.EventType type) {
        for (jdk.jfr.EventType event : events) {
            if (event.getName().equals(type.getName())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Void finisher(DatabaseWriterResult combined) {
        ActiveSettings settings = new ActiveSettings(combined.activeSettings());

        List<EventTypeEnhancer> enhancers = List.of(
                new ExecutionSamplesExtraEnhancer(settings),
                new WallClockSamplesExtraEnhancer(),
                new NativeMallocAllocationSamplesExtraEnhancer(),
                new TlabAllocationSamplesExtraEnhancer(settings),
                new MonitorEnterExtraEnhancer(settings),
                new MonitorWaitExtraEnhancer(),
                new ThreadParkExtraEnhancer(settings),
                new WallClockSamplesWeightEnhancer(settings),
                new ExecutionSamplesWeightEnhancer(settings)
        );

        threadWriter.start();
        eventTypeWriter.start();

        for (jdk.jfr.EventType eventType : combined.eventTypes()) {
            Type type = Type.from(eventType);
            long weight = combined.weight().get(type);

            JsonNode columns = ProfileViewerUtils.toColumns(eventType);
            EventType newEventType = new EventType(
                    eventType.getName(),
                    eventType.getLabel(),
                    eventType.getId(),
                    eventType.getDescription(),
                    eventType.getCategoryNames(),
                    EventSource.JDK,
                    null,
                    combined.samples().get(type),
                    weight == 0 ? null : weight,
                    eventType.getField("stackTrace") != null,
                    false,
                    null,
                    columns);

            EventType enhancedEventType = applyEnhancers(enhancers, newEventType, type);
            eventTypeWriter.insert(enhancedEventType);
        }

        // Threads names can be cleaned/modified by several approaches to ensure the better consistency and completeness
        // e.g. missing names [tid=25432], shorter names from AsyncProfiler (based on Linux filesystem info), ...
        // In most cases, it's about JVM threads (GC, JIT, ...), JDK-based JFR events provides valid threads names
        List<EventThread> modifiedThreads = new EventThreadCleaner()
                .clean(combined.eventThreads());

        modifiedThreads.forEach(threadWriter::insert);

        eventTypeWriter.close();
        threadWriter.close();

        // Collector does not return any result - Void
        return null;
    }

    private static EventType applyEnhancers(List<EventTypeEnhancer> enhancers, EventType eventType, Type type) {
        EventType current = eventType;
        if (enhancers != null) {
            for (EventTypeEnhancer enhancer : enhancers) {
                if (enhancer.isApplicable(type)) {
                    current = enhancer.apply(current);
                }
            }
        }
        return current;
    }

    private static Map<SettingNameLabel, ActiveSetting> combineActiveSettings(
            Map<SettingNameLabel, ActiveSetting> partial1,
            Map<SettingNameLabel, ActiveSetting> partial2) {

        Map<SettingNameLabel, ActiveSetting> combined = new HashMap<>(partial1);
        for (Map.Entry<SettingNameLabel, ActiveSetting> entry : partial2.entrySet()) {
            combined.merge(entry.getKey(), entry.getValue(), (setting1, setting2) -> {
                if (setting2.enabled()) {
                    return setting2;
                } else {
                    return setting1;
                }
            });
        }
        return combined;
    }
}
