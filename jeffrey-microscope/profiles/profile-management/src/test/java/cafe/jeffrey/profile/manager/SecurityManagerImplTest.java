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
