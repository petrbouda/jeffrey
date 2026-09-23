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

package cafe.jeffrey.profile.manager.model.security;

import tools.jackson.databind.node.ObjectNode;
import cafe.jeffrey.profile.manager.model.security.SecurityData.MisdeclarationStat;
import cafe.jeffrey.provider.profile.api.GenericRecord;
import cafe.jeffrey.provider.profile.api.RecordBuilder;
import cafe.jeffrey.shared.common.Json;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Aggregates {@code jdk.SerializationMisdeclaration} events (JDK 26+). The JVM emits one event the
 * first time a serializable class with a misdeclared {@code serialVersionUID}, {@code writeObject},
 * {@code serialPersistentFields}, etc. is used by serialization. Grouped by class + message and
 * counted, these point to brittle/incorrect serialization contracts.
 */
public class SerializationMisdeclarationBuilder
        implements RecordBuilder<GenericRecord, List<MisdeclarationStat>> {

    private static final String MISDECLARED_CLASS_FIELD = "misdeclaredClass";
    private static final String MESSAGE_FIELD = "message";
    private static final String UNKNOWN_CLASS = "unknown";
    private static final String NO_MESSAGE = "";
    private static final int MAX_MISDECLARATIONS = 200;

    private record Key(String misdeclaredClass, String message) {
    }

    private final Map<Key, long[]> byClassAndMessage = new LinkedHashMap<>();

    @Override
    public void onRecord(GenericRecord record) {
        ObjectNode fields = record.jsonFields();
        String misdeclaredClass = Json.readString(fields, MISDECLARED_CLASS_FIELD);
        String message = Json.readString(fields, MESSAGE_FIELD);
        Key key = new Key(
                misdeclaredClass == null ? UNKNOWN_CLASS : misdeclaredClass,
                message == null ? NO_MESSAGE : message);
        byClassAndMessage.computeIfAbsent(key, ignored -> new long[1])[0]++;
    }

    @Override
    public List<MisdeclarationStat> build() {
        return byClassAndMessage.entrySet().stream()
                .map(entry -> new MisdeclarationStat(
                        entry.getKey().misdeclaredClass(), entry.getKey().message(), entry.getValue()[0]))
                .sorted(Comparator.comparingLong(MisdeclarationStat::count).reversed())
                .limit(MAX_MISDECLARATIONS)
                .toList();
    }
}
