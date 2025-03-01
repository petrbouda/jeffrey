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

package pbouda.jeffrey.provider.writer.sqlite;

import pbouda.jeffrey.common.Type;
import pbouda.jeffrey.common.model.ActiveSetting;
import pbouda.jeffrey.common.model.ActiveSettings;
import pbouda.jeffrey.provider.api.EventWriterResult;
import pbouda.jeffrey.provider.api.model.EnhancedEventType;
import pbouda.jeffrey.provider.api.model.EventThread;
import pbouda.jeffrey.provider.api.model.EventTypeBuilder;
import pbouda.jeffrey.provider.writer.sqlite.model.EventThreadWithId;
import pbouda.jeffrey.provider.writer.sqlite.writer.DatabaseWriter;
import pbouda.jeffrey.provider.writer.sqlite.enhancer.*;

import java.util.*;

public class WriterResultCollector {

    private final ProfileSequences sequences;
    private final DatabaseWriter<EnhancedEventType> eventTypeWriter;
    private final DatabaseWriter<EventThreadWithId> threadWriter;

    private EventWriterResult combined = new EventWriterResult(new ArrayList<>(), new ArrayList<>(), new HashMap<>());

    public WriterResultCollector(
            ProfileSequences sequences,
            DatabaseWriter<EnhancedEventType> eventTypeWriter,
            DatabaseWriter<EventThreadWithId> threadWriter) {

        this.sequences = sequences;
        this.eventTypeWriter = eventTypeWriter;
        this.threadWriter = threadWriter;
    }

    public void add(EventWriterResult partial2) {
        List<EventTypeBuilder> events =
                combineEventTypes(combined.eventTypes(), partial2.eventTypes());

        Map<String, ActiveSetting> activeSettings =
                combineActiveSettings(combined.activeSettings(), partial2.activeSettings());

        List<EventThread> threads = new ArrayList<>();
        threads.addAll(combined.eventThreads());
        threads.addAll(partial2.eventThreads());

        this.combined = new EventWriterResult(threads, events, activeSettings);
    }

    public void execute() {
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

        for (EventTypeBuilder eventTypeBuilder : combined.eventTypes()) {
            applyEnhancers(enhancers, eventTypeBuilder);
            eventTypeWriter.insert(eventTypeBuilder.build());
        }

        // Threads names can be cleaned/modified by several approaches to ensure the better consistency and completeness
        // e.g. missing names [tid=25432], shorter names from AsyncProfiler (based on Linux filesystem info), ...
        // In most cases, it's about JVM threads (GC, JIT, ...), JDK-based JFR events provides valid threads names
        List<EventThread> modifiedThreads = new EventThreadCleaner()
                .clean(combined.eventThreads());

        modifiedThreads.forEach(thread -> {
            threadWriter.insert(new EventThreadWithId(sequences.nextThreadId(), thread));
        });
    }

    private static void applyEnhancers(List<EventTypeEnhancer> enhancers, EventTypeBuilder builder) {
        Type type = Type.fromCode(builder.getEventType().name());
        if (enhancers != null) {
            for (EventTypeEnhancer enhancer : enhancers) {
                if (enhancer.isApplicable(type)) {
                    enhancer.apply(builder);
                }
            }
        }
    }

    private static List<EventTypeBuilder> combineEventTypes(
            List<EventTypeBuilder> partial1,
            List<EventTypeBuilder> partial2) {

        List<EventTypeBuilder> merge = new ArrayList<>();
        for (EventTypeBuilder builder : partial1) {
            // Find if the builder with the same name has been already found and processed
            // If so, merge the samples and weight values together
            Optional<EventTypeBuilder> builderOpt = partial2.stream()
                    .filter(type -> type.getEventType().name().equals(builder.getEventType().name()))
                    .findFirst();

            if (builderOpt.isPresent()) {
                builderOpt.get().mergeWith(builder);
            } else {
                merge.add(builder);
            }
        }
        return merge;
    }

    private static Map<String, ActiveSetting> combineActiveSettings(
            Map<String, ActiveSetting> partial1,
            Map<String, ActiveSetting> partial2) {

        Map<String, ActiveSetting> combined = new HashMap<>(partial1);
        for (Map.Entry<String, ActiveSetting> entry : partial2.entrySet()) {
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
