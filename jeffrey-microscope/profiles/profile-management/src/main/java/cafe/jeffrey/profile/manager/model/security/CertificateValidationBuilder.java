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
import cafe.jeffrey.provider.profile.api.GenericRecord;
import cafe.jeffrey.provider.profile.api.RecordBuilder;
import cafe.jeffrey.shared.common.Json;

import java.util.HashMap;
import java.util.Map;

/**
 * Sums {@code jdk.X509Validation.validationCounter} per {@code certificateId} so each certificate row
 * can show how often it was validated.
 */
public class CertificateValidationBuilder implements RecordBuilder<GenericRecord, Map<Long, Long>> {

    private static final String CERTIFICATE_ID_FIELD = "certificateId";
    private static final String VALIDATION_COUNTER_FIELD = "validationCounter";

    private final Map<Long, Long> validationsById = new HashMap<>();

    @Override
    public void onRecord(GenericRecord record) {
        ObjectNode fields = record.jsonFields();
        long certificateId = Json.readLong(fields, CERTIFICATE_ID_FIELD);
        if (certificateId < 0) {
            return;
        }
        long counter = Math.max(1, Json.readLong(fields, VALIDATION_COUNTER_FIELD));
        validationsById.merge(certificateId, counter, Long::sum);
    }

    @Override
    public Map<Long, Long> build() {
        return validationsById;
    }
}
