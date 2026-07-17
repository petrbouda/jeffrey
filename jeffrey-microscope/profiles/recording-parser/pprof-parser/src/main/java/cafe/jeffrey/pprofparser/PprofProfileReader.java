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

package cafe.jeffrey.pprofparser;

import com.google.perftools.profiles.ProfileProto.Label;
import com.google.perftools.profiles.ProfileProto.Profile;
import com.google.perftools.profiles.ProfileProto.Sample;
import com.google.perftools.profiles.ProfileProto.ValueType;
import cafe.jeffrey.pprofparser.mapping.PprofEventTypeNaming;
import cafe.jeffrey.pprofparser.mapping.PprofEventTypeNaming.PprofEventType;
import cafe.jeffrey.pprofparser.mapping.PprofFrameMapper;
import cafe.jeffrey.pprofparser.mapping.PprofFrameMapper.MappedStack;
import cafe.jeffrey.pprofparser.mapping.PprofLabels;
import cafe.jeffrey.pprofparser.mapping.PprofSampleUnit;
import cafe.jeffrey.pprofparser.mapping.PprofSampleUnit.BytesUnit;
import cafe.jeffrey.pprofparser.mapping.PprofSampleUnit.CountUnit;
import cafe.jeffrey.pprofparser.mapping.PprofSampleUnit.DurationUnit;
import cafe.jeffrey.provider.profile.api.Event;
import cafe.jeffrey.provider.profile.api.EventStacktrace;
import cafe.jeffrey.provider.profile.api.EventThread;
import cafe.jeffrey.provider.profile.api.EventType;
import cafe.jeffrey.provider.profile.api.SingleThreadedEventWriter;
import cafe.jeffrey.shared.common.Json;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Emits the samples of a single pprof {@link Profile} into a {@link SingleThreadedEventWriter},
 * following the writer's announce-once protocol (each distinct thread / stacktrace is announced
 * exactly once, before the first event that uses it).
 * <p>
 * A pprof profile carries several value dimensions in each sample (its {@code sample_type} list —
 * e.g. a Go CPU profile has {@code samples}/count and {@code cpu}/nanoseconds; a heap profile has
 * four). Each dimension becomes its own Jeffrey event type ({@code pprof.<type>}) so they stay
 * independently browsable, and every non-zero sample value contributes one event to its dimension.
 * pprof has no per-sample timestamps, so every event is stamped with the profile's collection time.
 */
public final class PprofProfileReader {

    private static final String COLUMN_TYPE_TEXT = "text";
    private static final String COLUMN_TYPE_NUMBER = "number";
    private static final String SYNTHETIC_THREAD_NAME = "pprof-samples";

    private static final String COLUMN_FIELD = "field";
    private static final String COLUMN_HEADER = "header";
    private static final String COLUMN_TYPE = "type";
    private static final String COLUMN_DESCRIPTION = "description";

    private final SingleThreadedEventWriter writer;

    public PprofProfileReader(SingleThreadedEventWriter writer) {
        this.writer = writer;
    }

    public void read(Profile profile) {
        writer.onThreadStart();

        PprofTables tables = new PprofTables(profile);
        List<Dimension> dimensions = resolveDimensions(profile, tables);
        EmissionContext context = new EmissionContext(
                tables,
                dimensions,
                Instant.ofEpochSecond(0, profile.getTimeNanos()),
                new EventThread(SYNTHETIC_THREAD_NAME, null, null, false),
                new HashMap<>(),
                new HashMap<>(),
                new LinkedHashMap<>());

        for (Sample sample : profile.getSampleList()) {
            emitSample(sample, context);
        }

        JsonNode columns = buildColumns(context.labelColumnTypes());
        for (Dimension dimension : dimensions) {
            writer.onEventType(new EventType(
                    dimension.eventType().name(),
                    dimension.eventType().label(),
                    null,
                    null,
                    dimension.eventType().categories(),
                    columns));
        }

        writer.onThreadComplete();
    }

    private void emitSample(Sample sample, EmissionContext context) {
        PprofTables tables = context.tables();
        List<Label> labels = sample.getLabelList();
        recordLabelColumns(labels, tables, context.labelColumnTypes());

        EventThread resolvedThread = PprofLabels.resolveThread(labels, tables);
        EventThread thread = resolvedThread != null ? resolvedThread : context.syntheticThread();
        long threadId = context.threadIds().computeIfAbsent(thread, writer::onEventThread);

        List<Long> locationIds = sample.getLocationIdList();
        long stacktraceId = context.stacktraceIds().computeIfAbsent(locationIds, ids -> {
            MappedStack mapped = PprofFrameMapper.mapStack(ids, tables);
            return writer.onEventStacktrace(new EventStacktrace(mapped.type(), mapped.frames()));
        });

        ObjectNode fields = PprofLabels.toFields(labels, tables);

        for (Dimension dimension : context.dimensions()) {
            if (dimension.index() >= sample.getValueCount()) {
                continue;
            }
            long rawValue = sample.getValue(dimension.index());
            if (rawValue == 0) {
                continue;
            }

            SampleMagnitude magnitude = magnitude(dimension.unit(), rawValue);
            Event event = new Event(
                    dimension.eventType().name(),
                    context.profileTime(),
                    null,
                    magnitude.samples(),
                    magnitude.weight(),
                    null,
                    stacktraceId,
                    threadId,
                    fields);
            writer.onEvent(event);
        }
    }

    private record Dimension(int index, PprofSampleUnit unit, PprofEventType eventType) {
    }

    private record SampleMagnitude(long samples, Long weight) {
    }

    /**
     * Per-read emission state threaded through every sample: the profile's lookup tables and
     * dimensions, the profile-wide event time (pprof has no per-sample timestamps), the fallback
     * thread for samples without thread labels, the announce-once dedup maps, and the label
     * key -> column content type accumulator that lets the Event Viewer render the labels of every
     * pprof event type.
     */
    private record EmissionContext(
            PprofTables tables,
            List<Dimension> dimensions,
            Instant profileTime,
            EventThread syntheticThread,
            Map<EventThread, Long> threadIds,
            Map<List<Long>, Long> stacktraceIds,
            Map<String, String> labelColumnTypes) {
    }

    private List<Dimension> resolveDimensions(Profile profile, PprofTables tables) {
        List<Dimension> dimensions = new ArrayList<>(profile.getSampleTypeCount());
        for (int i = 0; i < profile.getSampleTypeCount(); i++) {
            ValueType sampleType = profile.getSampleType(i);
            String type = tables.string(sampleType.getType());
            String unit = tables.string(sampleType.getUnit());
            dimensions.add(new Dimension(
                    i,
                    PprofSampleUnit.fromUnitString(unit),
                    PprofEventTypeNaming.resolve(type, unit)));
        }
        return dimensions;
    }

    private static SampleMagnitude magnitude(PprofSampleUnit unit, long rawValue) {
        return switch (unit) {
            case CountUnit ignored -> new SampleMagnitude(rawValue, null);
            case DurationUnit duration -> new SampleMagnitude(1, duration.toNanos(rawValue));
            case BytesUnit ignored -> new SampleMagnitude(1, rawValue);
        };
    }

    private static void recordLabelColumns(List<Label> labels, PprofTables tables, Map<String, String> columnTypes) {
        for (Label label : labels) {
            String key = tables.string(label.getKey());
            if (key.isBlank()) {
                continue;
            }
            boolean isText = !tables.string(label.getStr()).isBlank();
            columnTypes.putIfAbsent(key, isText ? COLUMN_TYPE_TEXT : COLUMN_TYPE_NUMBER);
        }
    }

    private static JsonNode buildColumns(Map<String, String> labelColumnTypes) {
        List<ObjectNode> columns = new ArrayList<>(labelColumnTypes.size());
        for (Map.Entry<String, String> entry : labelColumnTypes.entrySet()) {
            columns.add(Json.createObject()
                    .put(COLUMN_FIELD, entry.getKey())
                    .put(COLUMN_HEADER, entry.getKey())
                    .put(COLUMN_TYPE, entry.getValue())
                    .putNull(COLUMN_DESCRIPTION));
        }
        return Json.mapper().valueToTree(columns);
    }
}
