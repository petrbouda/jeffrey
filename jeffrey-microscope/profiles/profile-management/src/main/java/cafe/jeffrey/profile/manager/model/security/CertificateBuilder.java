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
