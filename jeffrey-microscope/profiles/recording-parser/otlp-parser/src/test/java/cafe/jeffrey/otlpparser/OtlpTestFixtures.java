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

import com.google.protobuf.ByteString;
import io.opentelemetry.proto.common.v1.AnyValue;
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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Programmatic builder of OTLP {@code ProfilesData} messages for tests. Maintains the dictionary
 * tables with the mandatory zero-value element at index 0 and deduplicates strings by value.
 */
public class OtlpTestFixtures {

    private final Map<String, Integer> stringIndices = new LinkedHashMap<>();
    private final List<String> stringTable = new ArrayList<>();
    private final List<Mapping> mappingTable = new ArrayList<>();
    private final List<Location> locationTable = new ArrayList<>();
    private final List<Function> functionTable = new ArrayList<>();
    private final List<Link> linkTable = new ArrayList<>();
    private final List<KeyValueAndUnit> attributeTable = new ArrayList<>();
    private final List<Stack> stackTable = new ArrayList<>();

    private final List<Profile> profiles = new ArrayList<>();
    private final List<KeyValue> resourceAttributes = new ArrayList<>();

    public OtlpTestFixtures() {
        // index 0 of every dictionary table must be the zero value
        stringTable.add("");
        stringIndices.put("", 0);
        mappingTable.add(Mapping.getDefaultInstance());
        locationTable.add(Location.getDefaultInstance());
        functionTable.add(Function.getDefaultInstance());
        linkTable.add(Link.getDefaultInstance());
        attributeTable.add(KeyValueAndUnit.getDefaultInstance());
        stackTable.add(Stack.getDefaultInstance());
    }

    public int string(String value) {
        return stringIndices.computeIfAbsent(value, v -> {
            stringTable.add(v);
            return stringTable.size() - 1;
        });
    }

    public int mapping(String filename) {
        mappingTable.add(Mapping.newBuilder()
                .setFilenameStrindex(string(filename))
                .build());
        return mappingTable.size() - 1;
    }

    public int function(String name) {
        functionTable.add(Function.newBuilder()
                .setNameStrindex(string(name))
                .build());
        return functionTable.size() - 1;
    }

    public int stringAttribute(String key, String value) {
        attributeTable.add(KeyValueAndUnit.newBuilder()
                .setKeyStrindex(string(key))
                .setValue(AnyValue.newBuilder().setStringValue(value))
                .build());
        return attributeTable.size() - 1;
    }

    public int longAttribute(String key, long value) {
        attributeTable.add(KeyValueAndUnit.newBuilder()
                .setKeyStrindex(string(key))
                .setValue(AnyValue.newBuilder().setIntValue(value))
                .build());
        return attributeTable.size() - 1;
    }

    /**
     * Adds a location with a single line referencing the given function; {@code frameTypeAttrIndex}
     * may be {@code 0} to omit the {@code profile.frame.type} attribute.
     */
    public int location(int mappingIndex, int functionIndex, long lineNumber, int frameTypeAttrIndex) {
        Location.Builder location = Location.newBuilder()
                .setMappingIndex(mappingIndex)
                .addLines(Line.newBuilder().setFunctionIndex(functionIndex).setLine(lineNumber));
        if (frameTypeAttrIndex > 0) {
            location.addAttributeIndices(frameTypeAttrIndex);
        }
        locationTable.add(location.build());
        return locationTable.size() - 1;
    }

    public int addLocation(Location location) {
        locationTable.add(location);
        return locationTable.size() - 1;
    }

    /**
     * @param locationIndices leaf-first location indices, per the OTLP convention
     */
    public int stack(List<Integer> locationIndices) {
        stackTable.add(Stack.newBuilder().addAllLocationIndices(locationIndices).build());
        return stackTable.size() - 1;
    }

    public int link(byte[] traceId, byte[] spanId) {
        linkTable.add(Link.newBuilder()
                .setTraceId(ByteString.copyFrom(traceId))
                .setSpanId(ByteString.copyFrom(spanId))
                .build());
        return linkTable.size() - 1;
    }

    public void resourceAttribute(String key, String value) {
        resourceAttributes.add(KeyValue.newBuilder()
                .setKey(key)
                .setValue(AnyValue.newBuilder().setStringValue(value))
                .build());
    }

    public void profile(Profile profile) {
        profiles.add(profile);
    }

    public Profile.Builder profileBuilder(String sampleType, String sampleUnit, long timeUnixNano) {
        return Profile.newBuilder()
                .setSampleType(ValueType.newBuilder()
                        .setTypeStrindex(string(sampleType))
                        .setUnitStrindex(string(sampleUnit)))
                .setTimeUnixNano(timeUnixNano);
    }

    public Sample.Builder sampleBuilder(int stackIndex) {
        return Sample.newBuilder().setStackIndex(stackIndex);
    }

    public ProfilesDictionary buildDictionary() {
        return ProfilesDictionary.newBuilder()
                .addAllStringTable(stringTable)
                .addAllMappingTable(mappingTable)
                .addAllLocationTable(locationTable)
                .addAllFunctionTable(functionTable)
                .addAllLinkTable(linkTable)
                .addAllAttributeTable(attributeTable)
                .addAllStackTable(stackTable)
                .build();
    }

    public ProfilesData build() {
        return ProfilesData.newBuilder()
                .setDictionary(buildDictionary())
                .addResourceProfiles(ResourceProfiles.newBuilder()
                        .setResource(Resource.newBuilder().addAllAttributes(resourceAttributes))
                        .addScopeProfiles(ScopeProfiles.newBuilder().addAllProfiles(profiles)))
                .build();
    }
}
