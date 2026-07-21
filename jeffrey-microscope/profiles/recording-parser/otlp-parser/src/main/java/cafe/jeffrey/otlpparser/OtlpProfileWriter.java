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
import io.opentelemetry.proto.common.v1.InstrumentationScope;
import io.opentelemetry.proto.common.v1.KeyValue;
import io.opentelemetry.proto.profiles.v1development.Function;
import io.opentelemetry.proto.profiles.v1development.KeyValueAndUnit;
import io.opentelemetry.proto.profiles.v1development.Line;
import io.opentelemetry.proto.profiles.v1development.Link;
import io.opentelemetry.proto.profiles.v1development.Location;
import io.opentelemetry.proto.profiles.v1development.Mapping;
import io.opentelemetry.proto.profiles.v1development.Profile;
import io.opentelemetry.proto.profiles.v1development.ProfilesData;
import io.opentelemetry.proto.profiles.v1development.ProfilesDictionary;
import io.opentelemetry.proto.profiles.v1development.ResourceProfiles;
import io.opentelemetry.proto.profiles.v1development.Sample;
import io.opentelemetry.proto.profiles.v1development.ScopeProfiles;
import io.opentelemetry.proto.profiles.v1development.Stack;
import io.opentelemetry.proto.profiles.v1development.ValueType;
import io.opentelemetry.proto.resource.v1.Resource;
import cafe.jeffrey.otlpparser.mapping.OtelFrameTypeMapper;
import cafe.jeffrey.otlpparser.mapping.OtelSemconv;
import cafe.jeffrey.profile.common.model.FrameType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Writes Jeffrey stacks into a standard OpenTelemetry profiles ({@code ProfilesData}) message — the
 * inverse of {@link OtlpProfileReader}. Callers hand over root-first frames plus, per stack, a list of
 * {@code (timestamp, value)} observations; this builder deduplicates the dictionary tables (strings /
 * functions / locations / stacks / attributes, each with the mandatory zero value at index 0), emits
 * leaf-first {@code location_indices} (OTLP puts the leaf first, opposite Jeffrey's root-first order),
 * and stamps the {@code profile.frame.type} semantic-convention attribute onto every location whose
 * frame type is representable — the interoperability detail async-profiler's own converter omits.
 * <p>
 * Each stack becomes one OTLP {@link Sample} carrying its {@code timestamps_unix_nano} and aligned
 * {@code values}, so the per-observation timing survives the round-trip (unlike pprof, which has no
 * per-sample timestamps). OTLP allows a single {@code sample_type} per {@link Profile}, so each exported
 * dimension is one {@link Profile}; several event types export as several {@link Profile} messages inside
 * one {@link ScopeProfiles}, all sharing the single {@link ProfilesDictionary} (the dedup tables here are
 * global, so cross-profile frames/strings are shared automatically). The result is a single serialized
 * (uncompressed) raw {@code ProfilesData}, which {@link OtlpStreamReader} reads back directly.
 */
public final class OtlpProfileWriter {

    private static final String EMPTY_STRING = "";
    private static final String QUALIFIED_NAME_SEPARATOR = ".";
    private static final String SCOPE_NAME = "jeffrey";
    private static final String SCOPE_VERSION = "1.0";

    /**
     * The measurement dimension of the exported profile ({@code type}/{@code unit}, e.g.
     * {@code samples}/{@code count} or {@code cpu}/{@code nanoseconds}) — the OTLP {@code sample_type}.
     */
    public record SampleValueType(String type, String unit) {
    }

    /**
     * A single frame. {@code className} may be blank for native frames that carry only a method;
     * {@code frameTypeCode} is Jeffrey's persisted {@link FrameType#code()} (e.g. {@code "JIT compiled"},
     * {@code "Native"}) and drives the emitted {@code profile.frame.type} attribute — blank/unresolvable
     * codes emit no attribute.
     */
    public record ExportFrame(String className, String methodName, int line, String frameTypeCode) {
    }

    /**
     * One stack with its per-observation timing. {@code rootFirstFrames} is Jeffrey's order (root at
     * index 0); {@code timestampsNanos} and {@code values} are aligned element-wise — observation
     * {@code i} contributes {@code values[i]} at {@code timestampsNanos[i]} (nanoseconds past the epoch).
     */
    public record ExportSample(List<ExportFrame> rootFirstFrames, long[] timestampsNanos, long[] values) {
    }

    /**
     * One exported dimension: its {@link SampleValueType} plus the stacks contributing to it. Several
     * entries become several {@link Profile} messages in one file.
     */
    public record ProfileEntry(SampleValueType valueType, List<ExportSample> samples) {
    }

    private final List<String> stringTable = new ArrayList<>();
    private final Map<String, Integer> stringIndex = new HashMap<>();
    private final List<Function> functionTable = new ArrayList<>();
    private final Map<String, Integer> functionIndex = new HashMap<>();
    private final List<Location> locationTable = new ArrayList<>();
    private final Map<String, Integer> locationIndex = new HashMap<>();
    private final List<Stack> stackTable = new ArrayList<>();
    private final Map<String, Integer> stackIndex = new HashMap<>();
    private final List<KeyValueAndUnit> attributeTable = new ArrayList<>();
    private final Map<String, Integer> attributeIndex = new HashMap<>();

    public OtlpProfileWriter() {
        // Index 0 of every table must be the zero value so a 0 reference means "null / not set".
        stringTable.add(EMPTY_STRING);
        stringIndex.put(EMPTY_STRING, 0);
        functionTable.add(Function.getDefaultInstance());
        locationTable.add(Location.getDefaultInstance());
        stackTable.add(Stack.getDefaultInstance());
        attributeTable.add(KeyValueAndUnit.getDefaultInstance());
    }

    /**
     * Builds the OTLP profiles message for a single dimension and its samples. Convenience overload of
     * {@link #write(List, long, long, String)} for the common one-event-type export.
     *
     * @param valueType     the sample dimension (the OTLP {@code sample_type})
     * @param samples       stacks with root-first frames and aligned per-observation timestamps/values
     * @param timeNanos     collection time as nanoseconds past the epoch (informational)
     * @param durationNanos duration of the profiled window in nanoseconds (informational)
     * @param serviceName   value of the resource {@code service.name} attribute; skipped when blank
     * @return the serialized (uncompressed) {@code ProfilesData} bytes
     */
    public byte[] write(
            SampleValueType valueType,
            List<ExportSample> samples,
            long timeNanos,
            long durationNanos,
            String serviceName) {

        return write(List.of(new ProfileEntry(valueType, samples)), timeNanos, durationNanos, serviceName);
    }

    /**
     * Builds the OTLP profiles message for one or more dimensions. Each {@link ProfileEntry} becomes its
     * own {@link Profile} (OTLP allows one {@code sample_type} per profile); all profiles live in a single
     * {@link ScopeProfiles} and share the one {@link ProfilesDictionary} this writer accumulates, so frames
     * and strings common to several event types are stored once.
     *
     * @param entries       the dimensions to export, each with its own sample_type and samples
     * @param timeNanos     collection time as nanoseconds past the epoch (informational)
     * @param durationNanos duration of the profiled window in nanoseconds (informational)
     * @param serviceName   value of the resource {@code service.name} attribute; skipped when blank
     * @return the serialized (uncompressed) {@code ProfilesData} bytes
     */
    public byte[] write(
            List<ProfileEntry> entries,
            long timeNanos,
            long durationNanos,
            String serviceName) {

        ScopeProfiles.Builder scopeProfiles = ScopeProfiles.newBuilder()
                .setScope(InstrumentationScope.newBuilder()
                        .setName(SCOPE_NAME)
                        .setVersion(SCOPE_VERSION));

        // Build every profile first so all stack()/location()/string() calls populate the shared tables,
        // then emit the dictionary once from those tables.
        for (ProfileEntry entry : entries) {
            scopeProfiles.addProfiles(buildProfile(entry, timeNanos, durationNanos));
        }

        Resource.Builder resource = Resource.newBuilder();
        if (serviceName != null && !serviceName.isBlank()) {
            resource.addAttributes(KeyValue.newBuilder()
                    .setKey(OtelSemconv.SERVICE_NAME)
                    .setValue(AnyValue.newBuilder().setStringValue(serviceName)));
        }

        return ProfilesData.newBuilder()
                .addResourceProfiles(ResourceProfiles.newBuilder()
                        .setResource(resource)
                        .addScopeProfiles(scopeProfiles))
                .setDictionary(buildDictionary())
                .build()
                .toByteArray();
    }

    private Profile.Builder buildProfile(ProfileEntry entry, long timeNanos, long durationNanos) {
        Profile.Builder profile = Profile.newBuilder()
                .setSampleType(ValueType.newBuilder()
                        .setTypeStrindex(string(entry.valueType().type()))
                        .setUnitStrindex(string(entry.valueType().unit())))
                .setTimeUnixNano(timeNanos)
                .setDurationNano(durationNanos);

        for (ExportSample sample : entry.samples()) {
            Sample.Builder builder = Sample.newBuilder()
                    .setStackIndex(stack(sample.rootFirstFrames()));
            for (long timestampNanos : sample.timestampsNanos()) {
                builder.addTimestampsUnixNano(timestampNanos);
            }
            for (long value : sample.values()) {
                builder.addValues(value);
            }
            profile.addSamples(builder);
        }
        return profile;
    }

    private ProfilesDictionary buildDictionary() {
        return ProfilesDictionary.newBuilder()
                // One zero-value mapping is enough — this writer does not track binary mappings.
                .addMappingTable(Mapping.getDefaultInstance())
                .addAllLocationTable(locationTable)
                .addAllFunctionTable(functionTable)
                .addLinkTable(Link.getDefaultInstance())
                .addAllStringTable(stringTable)
                .addAllAttributeTable(attributeTable)
                .addAllStackTable(stackTable)
                .build();
    }

    private int stack(List<ExportFrame> rootFirstFrames) {
        // OTLP stacks are leaf-first (leaf at location_indices[0]); Jeffrey frames are root-first.
        List<Integer> locationIndices = new ArrayList<>(rootFirstFrames.size());
        for (int i = rootFirstFrames.size() - 1; i >= 0; i--) {
            locationIndices.add(location(rootFirstFrames.get(i)));
        }
        String key = locationIndices.toString();
        Integer existing = stackIndex.get(key);
        if (existing != null) {
            return existing;
        }
        int index = stackTable.size();
        stackTable.add(Stack.newBuilder().addAllLocationIndices(locationIndices).build());
        stackIndex.put(key, index);
        return index;
    }

    private int location(ExportFrame frame) {
        int functionRef = function(frame);
        int frameTypeAttr = frameTypeAttribute(frame.frameTypeCode());
        String key = functionRef + "|" + frame.line() + "|" + frameTypeAttr;
        Integer existing = locationIndex.get(key);
        if (existing != null) {
            return existing;
        }

        Line.Builder line = Line.newBuilder().setFunctionIndex(functionRef);
        if (frame.line() > 0) {
            line.setLine(frame.line());
        }
        Location.Builder location = Location.newBuilder().addLines(line);
        if (frameTypeAttr > 0) {
            location.addAttributeIndices(frameTypeAttr);
        }
        int index = locationTable.size();
        locationTable.add(location.build());
        locationIndex.put(key, index);
        return index;
    }

    private int function(ExportFrame frame) {
        String qualifiedName = qualifiedName(frame.className(), frame.methodName());
        String key = qualifiedName;
        Integer existing = functionIndex.get(key);
        if (existing != null) {
            return existing;
        }
        int index = functionTable.size();
        functionTable.add(Function.newBuilder()
                .setNameStrindex(string(qualifiedName))
                .build());
        functionIndex.put(key, index);
        return index;
    }

    /**
     * @return the attribute-table index of the {@code profile.frame.type} attribute for the given
     * Jeffrey frame-type code, or {@code 0} (the zero value = no attribute) when the code is blank,
     * unrecognized, or maps to no OTLP semantic-convention value.
     */
    private int frameTypeAttribute(String frameTypeCode) {
        String semconv = semconvFrameType(frameTypeCode);
        if (semconv == null) {
            return 0;
        }
        String cacheKey = OtelSemconv.PROFILE_FRAME_TYPE + '\0' + semconv;
        Integer existing = attributeIndex.get(cacheKey);
        if (existing != null) {
            return existing;
        }
        int index = attributeTable.size();
        attributeTable.add(KeyValueAndUnit.newBuilder()
                .setKeyStrindex(string(OtelSemconv.PROFILE_FRAME_TYPE))
                .setValue(AnyValue.newBuilder().setStringValue(semconv))
                .build());
        attributeIndex.put(cacheKey, index);
        return index;
    }

    private static String semconvFrameType(String frameTypeCode) {
        if (frameTypeCode == null || frameTypeCode.isBlank()) {
            return null;
        }
        FrameType frameType;
        try {
            frameType = FrameType.fromCode(frameTypeCode);
        } catch (RuntimeException e) {
            return null;
        }
        return OtelFrameTypeMapper.toSemconv(frameType);
    }

    private static String qualifiedName(String className, String methodName) {
        if (className == null || className.isBlank()) {
            return methodName;
        }
        return className + QUALIFIED_NAME_SEPARATOR + methodName;
    }

    private int string(String value) {
        String normalized = value == null ? EMPTY_STRING : value;
        Integer existing = stringIndex.get(normalized);
        if (existing != null) {
            return existing;
        }
        int index = stringTable.size();
        stringTable.add(normalized);
        stringIndex.put(normalized, index);
        return index;
    }
}
