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

import com.google.perftools.profiles.ProfileProto.Function;
import com.google.perftools.profiles.ProfileProto.Line;
import com.google.perftools.profiles.ProfileProto.Location;
import com.google.perftools.profiles.ProfileProto.Profile;
import com.google.perftools.profiles.ProfileProto.Sample;
import com.google.perftools.profiles.ProfileProto.ValueType;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

/**
 * Writes Jeffrey stacks into a standard, gzip-compressed pprof ({@code perftools.profiles.Profile})
 * — the exact inverse of {@link PprofProfileReader}. It is decoupled from the persistence / JFR
 * types via a small input model ({@link SampleValueType}, {@link ExportSample}, {@link ExportFrame}):
 * callers hand over root-first frames plus one value per declared {@link SampleValueType}, and this
 * builder assigns ids, deduplicates the string / function / location tables, and emits leaf-first
 * {@code location_id} lists (pprof puts the leaf at index 0, opposite Jeffrey's root-first order).
 */
public final class PprofProfileWriter {

    /** pprof requires {@code string_table[0]} to be the empty string. */
    private static final String EMPTY_STRING = "";
    // Standard dotted form ('libjvm.so.Foo::bar', 'com.example.Foo.bar') — the idiomatic pprof
    // representation, matching how async-profiler records these. The reader recovers the class via
    // the last dot (Java/C++ method names carry no dots, so the split lands on the right boundary).
    private static final char DOTTED_SEPARATOR = '.';
    // Only when the method itself contains a dot (rare native libc symbols such as
    // '__new_sem_wait_slow64.constprop.0') would the last-dot split be ambiguous, so mark that one
    // boundary with '#' to keep the round-trip lossless. FunctionNameSplitter reads both forms.
    private static final char EXPLICIT_SEPARATOR = '#';

    /**
     * One measurement dimension of the exported profile, mirroring a pprof {@code sample_type}
     * ({@code type}/{@code unit}, e.g. {@code samples}/{@code count} or {@code alloc_space}/{@code
     * bytes}). By pprof convention the count dimension is at index 0 with {@code unit == "count"}.
     */
    public record SampleValueType(String type, String unit) {
    }

    /** A single frame; {@code className} may be blank for native/C++ frames that carry only a method. */
    public record ExportFrame(String className, String methodName, int line) {
    }

    /**
     * One aggregated stack. {@code rootFirstFrames} is Jeffrey's order (root at index 0); {@code
     * values} are aligned element-wise with the profile's {@link SampleValueType} list.
     */
    public record ExportSample(List<ExportFrame> rootFirstFrames, long[] values) {
    }

    private final List<String> stringTable = new ArrayList<>();
    private final Map<String, Long> stringIndex = new HashMap<>();
    private final Map<String, Long> functionIds = new HashMap<>();
    private final Map<FunctionLine, Long> locationIds = new HashMap<>();
    private final Profile.Builder profile = Profile.newBuilder();

    private long nextFunctionId = 1;
    private long nextLocationId = 1;

    private record FunctionLine(long functionId, long line) {
    }

    public PprofProfileWriter() {
        // string_table[0] must be "".
        stringTable.add(EMPTY_STRING);
        stringIndex.put(EMPTY_STRING, 0L);
    }

    /**
     * Builds the pprof for the given dimensions and samples and returns the gzip-compressed bytes
     * (pprof mandates gzip on disk).
     *
     * @param valueTypes    the sample_type dimensions; every sample's {@code values} must match this length
     * @param samples       aggregated stacks with root-first frames and per-dimension values
     * @param timeNanos     collection time as nanoseconds past the epoch (informational)
     * @param durationNanos duration of the profiled window in nanoseconds (informational)
     */
    public byte[] write(List<SampleValueType> valueTypes, List<ExportSample> samples, long timeNanos, long durationNanos) {
        for (SampleValueType valueType : valueTypes) {
            profile.addSampleType(ValueType.newBuilder()
                    .setType(internString(valueType.type()))
                    .setUnit(internString(valueType.unit()))
                    .build());
        }

        for (ExportSample sample : samples) {
            profile.addSample(buildSample(sample));
        }

        profile.setTimeNanos(timeNanos);
        profile.setDurationNanos(durationNanos);
        profile.addAllStringTable(stringTable);

        return gzip(profile.build().toByteArray());
    }

    private Sample buildSample(ExportSample sample) {
        Sample.Builder builder = Sample.newBuilder();
        List<ExportFrame> frames = sample.rootFirstFrames();
        // pprof is leaf-first (leaf at location_id[0]); Jeffrey frames are root-first — walk in reverse.
        for (int i = frames.size() - 1; i >= 0; i--) {
            builder.addLocationId(locationId(frames.get(i)));
        }
        for (long value : sample.values()) {
            builder.addValue(value);
        }
        return builder.build();
    }

    private long locationId(ExportFrame frame) {
        long functionId = functionId(frame);
        FunctionLine key = new FunctionLine(functionId, frame.line());
        Long existing = locationIds.get(key);
        if (existing != null) {
            return existing;
        }

        long locationId = nextLocationId++;
        profile.addLocation(Location.newBuilder()
                .setId(locationId)
                .addLine(Line.newBuilder()
                        .setFunctionId(functionId)
                        .setLine(frame.line())
                        .build())
                .build());
        locationIds.put(key, locationId);
        return locationId;
    }

    private long functionId(ExportFrame frame) {
        String name = functionName(frame);
        Long existing = functionIds.get(name);
        if (existing != null) {
            return existing;
        }

        long functionId = nextFunctionId++;
        long nameIndex = internString(name);
        profile.addFunction(Function.newBuilder()
                .setId(functionId)
                .setName(nameIndex)
                .setSystemName(nameIndex)
                .build());
        functionIds.put(name, functionId);
        return functionId;
    }

    /**
     * Builds the pprof function name from the split class / method — the inverse of the reader's
     * {@code FunctionNameSplitter}. Emits the idiomatic dotted form ({@code class.method}); only when
     * the method itself already contains a dot is the boundary marked with {@code #} so the reader's
     * last-dot split stays lossless. A blank class means a native/C++ frame whose whole name lives in
     * the method.
     */
    private static String functionName(ExportFrame frame) {
        String className = frame.className();
        if (className == null || className.isBlank()) {
            return frame.methodName();
        }
        char separator = frame.methodName().indexOf(DOTTED_SEPARATOR) >= 0 ? EXPLICIT_SEPARATOR : DOTTED_SEPARATOR;
        return className + separator + frame.methodName();
    }

    private long internString(String value) {
        String normalized = value == null ? EMPTY_STRING : value;
        Long existing = stringIndex.get(normalized);
        if (existing != null) {
            return existing;
        }
        long index = stringTable.size();
        stringTable.add(normalized);
        stringIndex.put(normalized, index);
        return index;
    }

    private static byte[] gzip(byte[] data) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(data.length / 2 + 1);
        try (GZIPOutputStream gzip = new GZIPOutputStream(out)) {
            gzip.write(data);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to gzip pprof profile", e);
        }
        return out.toByteArray();
    }
}
