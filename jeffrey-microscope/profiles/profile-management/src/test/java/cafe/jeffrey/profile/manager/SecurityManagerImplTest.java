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

package cafe.jeffrey.profile.manager;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import cafe.jeffrey.profile.manager.model.security.CertificateBuilder.CertInfo;
import cafe.jeffrey.profile.manager.model.security.SecurityData.CertificateStat;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("SecurityManagerImpl.buildCertificates")
class SecurityManagerImplTest {

    private static final long RECORDING_END = 1_000_000_000L;
    private static final long DAY = 24L * 60 * 60 * 1000;

    private static CertInfo cert(long id, String keyType, int keyLength, String algorithm, long validUntil) {
        return new CertInfo(id, "CN=test-" + id, "CN=ca", keyType, keyLength, algorithm, 0L, validUntil);
    }

    @Test
    @DisplayName("Flags weak key, weak signature, expired and expiring-soon; attaches validation counts")
    void flagsCertificates() {
        Map<Long, CertInfo> certs = Map.of(
                1L, cert(1, "RSA", 1024, "SHA256withRSA", RECORDING_END + 365 * DAY),   // weak key
                2L, cert(2, "RSA", 4096, "SHA1withRSA", RECORDING_END + 365 * DAY),      // weak signature
                3L, cert(3, "RSA", 4096, "SHA256withRSA", RECORDING_END - DAY),          // expired
                4L, cert(4, "EC", 256, "SHA256withECDSA", RECORDING_END + 10 * DAY),     // expiring soon
                5L, cert(5, "RSA", 4096, "SHA256withRSA", RECORDING_END + 365 * DAY));   // healthy

        List<CertificateStat> result = SecurityManagerImpl.buildCertificates(
                certs, Map.of(1L, 7L), RECORDING_END);

        assertEquals(5, result.size());
        assertTrue(byId(result, "CN=test-1").weakKey());
        assertTrue(byId(result, "CN=test-2").weakSignature());
        assertTrue(byId(result, "CN=test-3").expired());
        assertTrue(byId(result, "CN=test-4").expiringSoon());

        CertificateStat healthy = byId(result, "CN=test-5");
        assertFalse(healthy.weakKey() || healthy.weakSignature() || healthy.expired() || healthy.expiringSoon());

        assertEquals(7, byId(result, "CN=test-1").validationCount());
    }

    private static CertificateStat byId(List<CertificateStat> result, String subject) {
        return result.stream()
                .filter(c -> subject.equals(c.subject()))
                .findFirst()
                .orElseThrow();
    }
}
