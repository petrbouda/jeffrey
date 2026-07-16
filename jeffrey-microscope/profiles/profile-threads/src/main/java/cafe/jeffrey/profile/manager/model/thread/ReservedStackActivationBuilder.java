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

package cafe.jeffrey.profile.manager.model.thread;

import tools.jackson.databind.node.ObjectNode;
import cafe.jeffrey.provider.profile.api.GenericRecord;
import cafe.jeffrey.provider.profile.api.RecordBuilder;
import cafe.jeffrey.shared.common.Json;

import java.util.ArrayList;
import java.util.List;

/**
 * Collects {@code jdk.ReservedStackActivation} events in chronological order.
 */
public class ReservedStackActivationBuilder implements RecordBuilder<GenericRecord, List<ReservedStackActivation>> {

    private static final String EVENT_THREAD_FIELD = "eventThread";
    private static final String METHOD_FIELD = "method";

    private final List<ReservedStackActivation> activations = new ArrayList<>();

    @Override
    public void onRecord(GenericRecord record) {
        ObjectNode fields = record.jsonFields();
        activations.add(new ReservedStackActivation(
                record.timestampFromStart().toMillis(),
                Json.readString(fields, EVENT_THREAD_FIELD),
                Json.readString(fields, METHOD_FIELD)));
    }

    @Override
    public List<ReservedStackActivation> build() {
        return activations;
    }
}
