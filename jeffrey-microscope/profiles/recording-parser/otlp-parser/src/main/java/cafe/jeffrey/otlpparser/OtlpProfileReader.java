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

package cafe.jeffrey.otlpparser;

import io.opentelemetry.proto.common.v1.AnyValue;
import io.opentelemetry.proto.common.v1.KeyValue;
import io.opentelemetry.proto.profiles.v1development.Link;
import io.opentelemetry.proto.profiles.v1development.Profile;
import io.opentelemetry.proto.profiles.v1development.ProfilesData;
import io.opentelemetry.proto.profiles.v1development.ResourceProfiles;
import io.opentelemetry.proto.profiles.v1development.Sample;
import io.opentelemetry.proto.profiles.v1development.ScopeProfiles;
import io.opentelemetry.proto.profiles.v1development.Stack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.otlpparser.dictionary.OtlpDictionary;
import cafe.jeffrey.otlpparser.mapping.OtelEventTypeNaming;
import cafe.jeffrey.otlpparser.mapping.OtelEventTypeNaming.OtelEventType;
import cafe.jeffrey.otlpparser.mapping.OtelFrameMapper;
import cafe.jeffrey.otlpparser.mapping.OtelFrameMapper.MappedStack;
import cafe.jeffrey.otlpparser.mapping.OtelSampleUnit;
import cafe.jeffrey.otlpparser.mapping.OtelSemconv;
import cafe.jeffrey.otlpparser.mapping.OtelThreadResolver;
import cafe.jeffrey.otlpparser.mapping.OtlpAttributes;
import cafe.jeffrey.provider.profile.api.Event;
import cafe.jeffrey.provider.profile.api.EventSetting;
import cafe.jeffrey.provider.profile.api.EventStacktrace;
import cafe.jeffrey.provider.profile.api.EventThread;
import cafe.jeffrey.provider.profile.api.EventType;
import cafe.jeffrey.shared.common.model.RecordingEventSource;
import cafe.jeffrey.provider.profile.api.SingleThreadedEventWriter;
import cafe.jeffrey.shared.common.Json;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Streams OTLP profiles out of a {@code .otlp} recording and emits them into a
 * {@link SingleThreadedEventWriter}, following the writer's announce-once protocol for threads and
 * stacktraces. This is the OTLP analogue of the JFR {@code JfrEventReader}.
 * <p>
 * Every OTLP {@code Profile} (one {@code sample_type}) maps onto one Jeffrey {@code otel.*} event
 * type; all profiles of all frames in the file are folded into the same target profile database.
 */
public class OtlpProfileReader {

    private static final Logger LOG = LoggerFactory.getLogger(OtlpProfileReader.class);

    private static final String FIELD_TRACE_ID = "trace_id";
    private static final String FIELD_SPAN_ID = "span_id";
    // The raw OTLP sample type as `type/unit`, stored so the flamegraph panel can format the weight from
    // the unit without inspecting the event code (mirrors the pprof parser's extras key).
    private static final String EXTRA_SAMPLE_TYPE = "sampleType";
    private static final String SAMPLE_TYPE_SEPARATOR = "/";

    private static final String SETTING_PERIOD = "otel.period";

    private static final String FALLBACK_THREAD_NAME = "otel-samples";

    /**
     * Per-event-type accumulation used to synthesize the {@code EventType} rows at the end of parsing.
     */
    private static class EventTypeState {
        private final OtelEventType otelEventType;
        private final String sampleType;
        private final String sampleUnit;
        private final long typeId;
        private final Set<String> fieldKeys = new TreeSet<>();

        private EventTypeState(OtelEventType otelEventType, String sampleType, String sampleUnit, long typeId) {
            this.otelEventType = otelEventType;
            this.sampleType = sampleType;
            this.sampleUnit = sampleUnit;
            this.typeId = typeId;
        }
    }

    private final SingleThreadedEventWriter writer;
    private final OtlpStreamReader streamReader;

    private final Map<String, EventTypeState> eventTypesByName = new LinkedHashMap<>();
    private final Map<EventThread, Long> threadIdsByThread = new HashMap<>();
    private final Set<String> emittedSettingKeys = new HashSet<>();

    public OtlpProfileReader(SingleThreadedEventWriter writer) {
        this(writer, new OtlpStreamReader());
    }

    public OtlpProfileReader(SingleThreadedEventWriter writer, OtlpStreamReader streamReader) {
        this.writer = writer;
        this.streamReader = streamReader;
    }

    public void read(Path recording) {
        writer.onThreadStart();
        streamReader.read(recording, this::onFrame);
        emitEventTypes();
        writer.onThreadComplete();
    }

    private void onFrame(ProfilesData frame) {
        OtlpDictionary dictionary = new OtlpDictionary(frame.getDictionary());
        // Stack dedup is index-based and dictionary indices are only valid within a single frame.
        // Cross-frame duplicates are collapsed by the writer's content-hash deduplication.
        Map<Integer, StacktraceRef> stacktracesByStackIndex = new HashMap<>();

        for (ResourceProfiles resourceProfiles : frame.getResourceProfilesList()) {
            String fallbackThreadName = resolveFallbackThreadName(resourceProfiles);
            for (ScopeProfiles scopeProfiles : resourceProfiles.getScopeProfilesList()) {
                for (Profile profile : scopeProfiles.getProfilesList()) {
                    readProfile(profile, resourceProfiles, scopeProfiles, dictionary, fallbackThreadName,
                            stacktracesByStackIndex);
                }
            }
        }
    }

    private void readProfile(
            Profile profile,
            ResourceProfiles resourceProfiles,
            ScopeProfiles scopeProfiles,
            OtlpDictionary dictionary,
            String fallbackThreadName,
            Map<Integer, StacktraceRef> stacktracesByStackIndex) {

        String sampleType = dictionary.string(profile.getSampleType().getTypeStrindex());
        String sampleUnitName = dictionary.string(profile.getSampleType().getUnitStrindex());

        OtelEventType otelEventType = OtelEventTypeNaming.resolve(sampleType);
        OtelSampleUnit sampleUnit = OtelSampleUnit.fromUnitString(sampleUnitName);

        EventTypeState state = eventTypesByName.computeIfAbsent(
                otelEventType.name(),
                name -> new EventTypeState(otelEventType, sampleType, sampleUnitName, eventTypesByName.size() + 1));

        emitProvenanceSettings(otelEventType.name(), profile, resourceProfiles, scopeProfiles, dictionary);

        boolean cardinalityMismatchLogged = false;
        for (Sample sample : profile.getSamplesList()) {
            cardinalityMismatchLogged = readSample(
                    sample, profile, dictionary, state, sampleUnit, fallbackThreadName,
                    stacktracesByStackIndex, cardinalityMismatchLogged);
        }
    }

    /**
     * @return the updated "cardinality mismatch already logged" flag for the current profile
     */
    private boolean readSample(
            Sample sample,
            Profile profile,
            OtlpDictionary dictionary,
            EventTypeState state,
            OtelSampleUnit sampleUnit,
            String fallbackThreadName,
            Map<Integer, StacktraceRef> stacktracesByStackIndex,
            boolean cardinalityMismatchLogged) {

        Map<String, AnyValue> sampleAttributes = OtlpAttributes.resolve(sample.getAttributeIndicesList(), dictionary);

        Long threadId = resolveThreadId(sampleAttributes, fallbackThreadName);
        Long stacktraceId = resolveStacktraceId(sample, dictionary, stacktracesByStackIndex);
        String weightEntity = resolveWeightEntity(sampleUnit, sampleAttributes);
        ObjectNode fields = buildFields(sample, sampleAttributes, dictionary, state);

        List<Long> timestamps = sample.getTimestampsUnixNanoList();
        List<Long> values = sample.getValuesList();

        if (timestamps.isEmpty()) {
            long totalValue = values.isEmpty() ? 1 : sum(values);
            Instant timestamp = Instant.ofEpochSecond(0, profile.getTimeUnixNano());
            emitEvent(state, sampleUnit, timestamp, totalValue, weightEntity, stacktraceId, threadId, fields);
            return cardinalityMismatchLogged;
        }

        if (values.isEmpty()) {
            // Timestamps-only shape: the value of each observation is 1 (per the OTLP profiles spec)
            for (Long timestampNanos : timestamps) {
                Instant timestamp = Instant.ofEpochSecond(0, timestampNanos);
                emitEvent(state, sampleUnit, timestamp, 1, weightEntity, stacktraceId, threadId, fields);
            }
            return cardinalityMismatchLogged;
        }

        if (values.size() == timestamps.size()) {
            for (int i = 0; i < timestamps.size(); i++) {
                Instant timestamp = Instant.ofEpochSecond(0, timestamps.get(i));
                emitEvent(state, sampleUnit, timestamp, values.get(i), weightEntity, stacktraceId, threadId, fields);
            }
            return cardinalityMismatchLogged;
        }

        // Off-spec shape: values/timestamps cardinality mismatch. Spread the total value evenly over
        // the timestamps (remainder on the last one) so per-event granularity survives and the total
        // stays exact.
        if (!cardinalityMismatchLogged) {
            LOG.warn("OTLP sample values/timestamps cardinality mismatch, spreading total evenly: "
                            + "event_type={} values_count={} timestamps_count={}",
                    state.otelEventType.name(), values.size(), timestamps.size());
        }
        long total = sum(values);
        long base = total / timestamps.size();
        long remainder = total - base * timestamps.size();
        for (int i = 0; i < timestamps.size(); i++) {
            long value = base + (i == timestamps.size() - 1 ? remainder : 0);
            Instant timestamp = Instant.ofEpochSecond(0, timestamps.get(i));
            emitEvent(state, sampleUnit, timestamp, value, weightEntity, stacktraceId, threadId, fields);
        }
        return true;
    }

    private void emitEvent(
            EventTypeState state,
            OtelSampleUnit sampleUnit,
            Instant timestamp,
            long value,
            String weightEntity,
            Long stacktraceId,
            Long threadId,
            ObjectNode fields) {

        long samples;
        Long weight;
        switch (sampleUnit) {
            case OtelSampleUnit.CountUnit _ -> {
                samples = value;
                weight = null;
            }
            case OtelSampleUnit.DurationUnit durationUnit -> {
                samples = 1;
                weight = durationUnit.toNanos(value);
            }
            case OtelSampleUnit.BytesUnit _ -> {
                samples = 1;
                weight = value;
            }
        }

        Event event = new Event(
                state.otelEventType.name(),
                timestamp,
                null,
                samples,
                weight,
                weight != null ? weightEntity : null,
                stacktraceId,
                threadId,
                fields);
        writer.onEvent(event);
    }

    private Long resolveThreadId(Map<String, AnyValue> sampleAttributes, String fallbackThreadName) {
        EventThread thread = OtelThreadResolver.resolve(sampleAttributes, fallbackThreadName);
        return threadIdsByThread.computeIfAbsent(thread, writer::onEventThread);
    }

    /**
     * Announced stacktrace reference; {@code id} is {@code null} for empty/unresolvable stacks.
     */
    private record StacktraceRef(Long id) {
    }

    private Long resolveStacktraceId(
            Sample sample,
            OtlpDictionary dictionary,
            Map<Integer, StacktraceRef> stacktracesByStackIndex) {

        int stackIndex = sample.getStackIndex();
        Stack stack = dictionary.stack(stackIndex);
        if (stack == null) {
            return null;
        }

        StacktraceRef ref = stacktracesByStackIndex.computeIfAbsent(stackIndex, _ -> {
            MappedStack mappedStack = OtelFrameMapper.mapStack(stack, dictionary);
            if (mappedStack.frames().isEmpty()) {
                return new StacktraceRef(null);
            }
            EventStacktrace stacktrace = new EventStacktrace(mappedStack.type(), mappedStack.frames());
            return new StacktraceRef(writer.onEventStacktrace(stacktrace));
        });
        return ref.id();
    }

    private String resolveWeightEntity(OtelSampleUnit sampleUnit, Map<String, AnyValue> sampleAttributes) {
        if (!(sampleUnit instanceof OtelSampleUnit.BytesUnit)) {
            return null;
        }
        for (String key : OtelSemconv.WEIGHT_ENTITY_KEYS) {
            String entity = OtlpAttributes.stringValue(sampleAttributes.get(key));
            if (entity != null && !entity.isBlank()) {
                return entity;
            }
        }
        return null;
    }

    private ObjectNode buildFields(
            Sample sample,
            Map<String, AnyValue> sampleAttributes,
            OtlpDictionary dictionary,
            EventTypeState state) {

        ObjectNode fields = Json.createObject();

        Link link = dictionary.link(sample.getLinkIndex());
        if (link != null) {
            String traceId = toHex(link.getTraceId().toByteArray());
            String spanId = toHex(link.getSpanId().toByteArray());
            if (traceId != null) {
                fields.put(FIELD_TRACE_ID, traceId);
                state.fieldKeys.add(FIELD_TRACE_ID);
            }
            if (spanId != null) {
                fields.put(FIELD_SPAN_ID, spanId);
                state.fieldKeys.add(FIELD_SPAN_ID);
            }
        }

        for (Map.Entry<String, AnyValue> attribute : sampleAttributes.entrySet()) {
            if (OtelSemconv.STRUCTURAL_SAMPLE_KEYS.contains(attribute.getKey())) {
                continue;
            }
            OtlpAttributes.putJsonField(fields, attribute.getKey(), attribute.getValue());
            state.fieldKeys.add(attribute.getKey());
        }
        return fields;
    }

    private void emitProvenanceSettings(
            String eventTypeName,
            Profile profile,
            ResourceProfiles resourceProfiles,
            ScopeProfiles scopeProfiles,
            OtlpDictionary dictionary) {

        for (KeyValue attribute : resourceProfiles.getResource().getAttributesList()) {
            emitSetting(eventTypeName, attribute.getKey(), OtlpAttributes.stringValue(attribute.getValue()));
        }

        if (scopeProfiles.hasScope()) {
            emitSetting(eventTypeName, OtelSemconv.SCOPE_NAME_SETTING, scopeProfiles.getScope().getName());
            emitSetting(eventTypeName, OtelSemconv.SCOPE_VERSION_SETTING, scopeProfiles.getScope().getVersion());
        }

        if (profile.getPeriod() > 0) {
            String periodType = dictionary.string(profile.getPeriodType().getTypeStrindex());
            String periodUnit = dictionary.string(profile.getPeriodType().getUnitStrindex());
            emitSetting(eventTypeName, SETTING_PERIOD,
                    profile.getPeriod() + " " + periodUnit + " (" + periodType + ")");
        }
    }

    private void emitSetting(String eventTypeName, String name, String value) {
        if (name == null || name.isBlank() || value == null || value.isBlank()) {
            return;
        }
        if (emittedSettingKeys.add(eventTypeName + "|" + name)) {
            writer.onEventSetting(new EventSetting(eventTypeName, name, value));
        }
    }

    private void emitEventTypes() {
        for (EventTypeState state : eventTypesByName.values()) {
            writer.onEventType(new EventType(
                    state.otelEventType.name(),
                    state.otelEventType.label(),
                    state.typeId,
                    describe(state),
                    List.of(),
                    buildColumns(state),
                    Map.of(EXTRA_SAMPLE_TYPE, state.sampleType + SAMPLE_TYPE_SEPARATOR + state.sampleUnit),
                    RecordingEventSource.OPEN_TELEMETRY));
        }
    }

    private static String describe(EventTypeState state) {
        return "Synthesized from the OpenTelemetry profile sample type '"
                + state.sampleType + "/" + state.sampleUnit + "'";
    }

    private static JsonNode buildColumns(EventTypeState state) {
        List<ObjectNode> columns = state.fieldKeys.stream()
                .map(key -> {
                    ObjectNode column = Json.createObject()
                            .put("field", key)
                            .put("header", key);
                    column.putNull("type");
                    column.putNull("description");
                    return column;
                })
                .toList();
        return Json.mapper().valueToTree(columns);
    }

    private String resolveFallbackThreadName(ResourceProfiles resourceProfiles) {
        String executable = null;
        String pid = null;
        String serviceName = null;
        for (KeyValue attribute : resourceProfiles.getResource().getAttributesList()) {
            switch (attribute.getKey()) {
                case OtelSemconv.PROCESS_EXECUTABLE_NAME -> executable = OtlpAttributes.stringValue(attribute.getValue());
                case OtelSemconv.PROCESS_PID -> pid = OtlpAttributes.stringValue(attribute.getValue());
                case OtelSemconv.SERVICE_NAME -> serviceName = OtlpAttributes.stringValue(attribute.getValue());
                default -> {
                }
            }
        }

        if (executable != null && !executable.isBlank()) {
            return pid != null && !pid.isBlank() ? executable + " (pid=" + pid + ")" : executable;
        }
        if (serviceName != null && !serviceName.isBlank()) {
            return serviceName;
        }
        return FALLBACK_THREAD_NAME;
    }

    private static long sum(List<Long> values) {
        long total = 0;
        for (Long value : values) {
            total += value;
        }
        return total;
    }

    private static String toHex(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        boolean allZero = true;
        StringBuilder hex = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            if (b != 0) {
                allZero = false;
            }
            hex.append(Character.forDigit((b >> 4) & 0xF, 16));
            hex.append(Character.forDigit(b & 0xF, 16));
        }
        return allZero ? null : hex.toString();
    }
}
