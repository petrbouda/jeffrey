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

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Collects {@code jdk.X509Certificate} events, deduplicated by {@code certificateId} (a certificate
 * is reported once per validation path). The manager turns these into
 * {@link SecurityData.CertificateStat} rows with security flags and validation counts.
 */
public class CertificateBuilder implements RecordBuilder<GenericRecord, Map<Long, CertificateBuilder.CertInfo>> {

    public record CertInfo(
            long certificateId,
            String subject,
            String issuer,
            String keyType,
            int keyLength,
            String algorithm,
            long validFrom,
            long validUntil) {
    }

    private static final String CERTIFICATE_ID_FIELD = "certificateId";
    private static final String SUBJECT_FIELD = "subject";
    private static final String ISSUER_FIELD = "issuer";
    private static final String KEY_TYPE_FIELD = "keyType";
    private static final String KEY_LENGTH_FIELD = "keyLength";
    private static final String ALGORITHM_FIELD = "algorithm";
    private static final String VALID_FROM_FIELD = "validFrom";
    private static final String VALID_UNTIL_FIELD = "validUntil";

    private final Map<Long, CertInfo> byId = new LinkedHashMap<>();

    @Override
    public void onRecord(GenericRecord record) {
        ObjectNode fields = record.jsonFields();
        long certificateId = Json.readLong(fields, CERTIFICATE_ID_FIELD);
        if (certificateId < 0) {
            return;
        }
        byId.putIfAbsent(certificateId, new CertInfo(
                certificateId,
                Json.readString(fields, SUBJECT_FIELD),
                Json.readString(fields, ISSUER_FIELD),
                Json.readString(fields, KEY_TYPE_FIELD),
                Json.readInt(fields, KEY_LENGTH_FIELD),
                Json.readString(fields, ALGORITHM_FIELD),
                Json.readLong(fields, VALID_FROM_FIELD),
                Json.readLong(fields, VALID_UNTIL_FIELD)));
    }

    @Override
    public Map<Long, CertInfo> build() {
        return byId;
    }
}
