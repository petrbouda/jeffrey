/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cafe.jeffrey.pprofparser;

import com.google.perftools.profiles.ProfileProto.Function;
import com.google.perftools.profiles.ProfileProto.Label;
import com.google.perftools.profiles.ProfileProto.Line;
import com.google.perftools.profiles.ProfileProto.Location;
import com.google.perftools.profiles.ProfileProto.Profile;
import com.google.perftools.profiles.ProfileProto.Sample;
import com.google.perftools.profiles.ProfileProto.ValueType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Programmatic pprof {@link Profile} builder for tests. Interns strings into the string table
 * (index 0 is the required empty string) and assigns sequential function / location ids.
 */
public final class PprofTestFixtures {

    private final List<String> strings = new ArrayList<>();
    private final Map<String, Long> stringIndex = new HashMap<>();
    private final List<Function> functions = new ArrayList<>();
    private final List<Location> locations = new ArrayList<>();
    private final List<Sample> samples = new ArrayList<>();
    private final List<ValueType> sampleTypes = new ArrayList<>();
    private long nextFunctionId = 1;
    private long nextLocationId = 1;
    private long timeNanos;
    private long durationNanos;

    public PprofTestFixtures() {
        intern("");
    }

    public long intern(String value) {
        return stringIndex.computeIfAbsent(value, v -> {
            long index = strings.size();
            strings.add(v);
            return index;
        });
    }

    public PprofTestFixtures sampleType(String type, String unit) {
        sampleTypes.add(ValueType.newBuilder()
                .setType(intern(type))
                .setUnit(intern(unit))
                .build());
        return this;
    }

    public PprofTestFixtures time(long timeNanos, long durationNanos) {
        this.timeNanos = timeNanos;
        this.durationNanos = durationNanos;
        return this;
    }

    /**
     * Creates a single-line location for {@code functionName} and returns its id.
     */
    public long location(String functionName, long line) {
        long functionId = nextFunctionId++;
        functions.add(Function.newBuilder()
                .setId(functionId)
                .setName(intern(functionName))
                .build());

        long locationId = nextLocationId++;
        locations.add(Location.newBuilder()
                .setId(locationId)
                .addLine(Line.newBuilder().setFunctionId(functionId).setLine(line).build())
                .build());
        return locationId;
    }

    /**
     * @param locationIds leaf-first location ids (pprof convention)
     * @param values      one value per declared sample_type dimension
     */
    public PprofTestFixtures sample(List<Long> locationIds, List<Long> values) {
        return sample(locationIds, values, List.of());
    }

    public PprofTestFixtures sample(List<Long> locationIds, List<Long> values, List<Label> labels) {
        Sample.Builder sample = Sample.newBuilder()
                .addAllLocationId(locationIds)
                .addAllValue(values)
                .addAllLabel(labels);
        samples.add(sample.build());
        return this;
    }

    public Label stringLabel(String key, String value) {
        return Label.newBuilder().setKey(intern(key)).setStr(intern(value)).build();
    }

    public Label numberLabel(String key, long value) {
        return Label.newBuilder().setKey(intern(key)).setNum(value).build();
    }

    public Profile build() {
        return Profile.newBuilder()
                .addAllSampleType(sampleTypes)
                .addAllSample(samples)
                .addAllFunction(functions)
                .addAllLocation(locations)
                .addAllStringTable(strings)
                .setTimeNanos(timeNanos)
                .setDurationNanos(durationNanos)
                .build();
    }
}
