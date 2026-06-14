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
