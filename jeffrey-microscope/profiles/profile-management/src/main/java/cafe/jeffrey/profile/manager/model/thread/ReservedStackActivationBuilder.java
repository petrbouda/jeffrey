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
