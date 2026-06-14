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
