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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import cafe.jeffrey.profile.manager.model.security.DeserializationBuilder.Result;
import cafe.jeffrey.profile.manager.model.security.SecurityData.MisdeclarationStat;
import cafe.jeffrey.profile.manager.model.security.SecurityData.NamedCount;
import cafe.jeffrey.profile.manager.model.security.SecurityData.ProviderServiceStat;
import cafe.jeffrey.provider.profile.api.GenericRecord;
import cafe.jeffrey.shared.common.Json;
import cafe.jeffrey.shared.common.model.Type;
import cafe.jeffrey.shared.common.model.time.RelativeTimeRange;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("Security builders")
class SecurityBuildersTest {

    private static final Instant START = Instant.parse("2024-01-01T00:00:00Z");

    private static GenericRecord rec(Type type, long secondsFromStart, ObjectNode fields) {
        return new GenericRecord(
                type, "label", START,
                Duration.ofSeconds(secondsFromStart), Duration.ZERO,
                null, null, 0L, 0L, fields);
    }

    private static long countOf(List<NamedCount> list, String name) {
        return list.stream().filter(n -> n.name().equals(name)).mapToLong(NamedCount::count).findFirst().orElse(0);
    }

    @Nested
    @DisplayName("TlsHandshakeBuilder")
    class Tls {

        @Test
        @DisplayName("Counts handshakes by protocol, cipher and peer")
        void aggregates() {
            TlsHandshakeBuilder builder = new TlsHandshakeBuilder(new RelativeTimeRange(0, 10_000));
            builder.onRecord(rec(Type.TLS_HANDSHAKE, 1, tls("h1", 443, "TLSv1.3", "TLS_AES_128_GCM_SHA256")));
            builder.onRecord(rec(Type.TLS_HANDSHAKE, 1, tls("h1", 443, "TLSv1.3", "TLS_AES_128_GCM_SHA256")));
            builder.onRecord(rec(Type.TLS_HANDSHAKE, 2, tls("h2", 8443, "TLSv1.2", "TLS_RSA_WITH_AES_128_CBC_SHA")));

            TlsHandshakeBuilder.Result result = builder.build();

            assertEquals(3, result.total());
            assertEquals(2, result.distinctPeers());
            assertEquals(2, countOf(result.protocols(), "TLSv1.3"));
            assertEquals(1, countOf(result.protocols(), "TLSv1.2"));
            assertEquals(2, countOf(result.peers(), "h1:443"));
            assertEquals("TLS Handshakes", result.timeline().series().getFirst().name());
        }

        private ObjectNode tls(String host, long port, String protocol, String cipher) {
            ObjectNode node = Json.createObject();
            node.put("peerHost", host);
            node.put("peerPort", port);
            node.put("protocolVersion", protocol);
            node.put("cipherSuite", cipher);
            return node;
        }
    }

    @Nested
    @DisplayName("DeserializationBuilder")
    class Deser {

        @Test
        @DisplayName("Summarises filter status and ranks types by bytes")
        void summarises() {
            DeserializationBuilder builder = new DeserializationBuilder();
            builder.onRecord(rec(Type.DESERIALIZATION, 1, deser("com.A", 1000, 5, true, "ALLOWED", null)));
            builder.onRecord(rec(Type.DESERIALIZATION, 1, deser("com.A", 4000, 9, true, "ALLOWED", null)));
            builder.onRecord(rec(Type.DESERIALIZATION, 2, deser("com.B", 200, 2, true, "REJECTED", "java.io.IOException")));

            Result result = builder.build();

            assertEquals(3, result.summary().totalEvents());
            assertEquals(3, result.summary().filterConfiguredEvents());
            assertEquals(1, result.summary().rejectedEvents());
            assertEquals(1, result.summary().exceptionEvents());
            assertEquals("com.A", result.types().getFirst().type());
            assertEquals(5000, result.types().getFirst().totalBytes());
            assertEquals(9, result.types().getFirst().maxDepth());
        }

        private ObjectNode deser(String type, long bytes, long depth, boolean configured, String status, String ex) {
            ObjectNode node = Json.createObject();
            node.put("type", type);
            node.put("bytesRead", bytes);
            node.put("depth", depth);
            node.put("filterConfigured", configured);
            node.put("filterStatus", status);
            if (ex != null) {
                node.put("exceptionType", ex);
            }
            return node;
        }
    }

    @Nested
    @DisplayName("ProviderServiceBuilder")
    class Providers {

        @Test
        @DisplayName("Counts provider/type/algorithm combinations")
        void counts() {
            ProviderServiceBuilder builder = new ProviderServiceBuilder();
            builder.onRecord(rec(Type.SECURITY_PROVIDER_SERVICE, 1, provider("SunJCE", "Cipher", "AES")));
            builder.onRecord(rec(Type.SECURITY_PROVIDER_SERVICE, 1, provider("SunJCE", "Cipher", "AES")));
            builder.onRecord(rec(Type.SECURITY_PROVIDER_SERVICE, 2, provider("SUN", "MessageDigest", "SHA-256")));

            List<ProviderServiceStat> result = builder.build();

            assertEquals(2, result.size());
            assertEquals("SunJCE", result.getFirst().provider());
            assertEquals(2, result.getFirst().count());
        }

        private ObjectNode provider(String provider, String type, String algorithm) {
            ObjectNode node = Json.createObject();
            node.put("provider", provider);
            node.put("type", type);
            node.put("algorithm", algorithm);
            return node;
        }
    }

    @Nested
    @DisplayName("CertificateBuilder + CertificateValidationBuilder")
    class Certificates {

        @Test
        @DisplayName("Dedups certificates by id and sums validations")
        void dedupAndSum() {
            CertificateBuilder certBuilder = new CertificateBuilder();
            certBuilder.onRecord(rec(Type.X509_CERTIFICATE, 1, cert(1, "CN=a", 2048)));
            certBuilder.onRecord(rec(Type.X509_CERTIFICATE, 2, cert(1, "CN=a", 2048)));
            certBuilder.onRecord(rec(Type.X509_CERTIFICATE, 3, cert(2, "CN=b", 1024)));
            Map<Long, CertificateBuilder.CertInfo> certs = certBuilder.build();

            CertificateValidationBuilder valBuilder = new CertificateValidationBuilder();
            valBuilder.onRecord(rec(Type.X509_VALIDATION, 1, validation(1, 3)));
            valBuilder.onRecord(rec(Type.X509_VALIDATION, 2, validation(1, 2)));
            Map<Long, Long> validations = valBuilder.build();

            assertEquals(2, certs.size());
            assertEquals("CN=a", certs.get(1L).subject());
            assertEquals(5, validations.get(1L));
        }

        private ObjectNode cert(long id, String subject, int keyLength) {
            ObjectNode node = Json.createObject();
            node.put("certificateId", id);
            node.put("subject", subject);
            node.put("issuer", "CN=ca");
            node.put("keyType", "RSA");
            node.put("keyLength", keyLength);
            node.put("algorithm", "SHA256withRSA");
            node.put("validFrom", 1000L);
            node.put("validUntil", 2000L);
            return node;
        }

        private ObjectNode validation(long id, long counter) {
            ObjectNode node = Json.createObject();
            node.put("certificateId", id);
            node.put("validationCounter", counter);
            return node;
        }
    }

    @Nested
    @DisplayName("SerializationMisdeclarationBuilder")
    class SerializationMisdeclarations {

        @Test
        @DisplayName("Groups misdeclarations by class and message, ranked by count")
        void groupsByClassAndMessage() {
            SerializationMisdeclarationBuilder builder = new SerializationMisdeclarationBuilder();
            builder.onRecord(rec(Type.SERIALIZATION_MISDECLARATION, 1, misdeclaration("com.A", "bad serialVersionUID")));
            builder.onRecord(rec(Type.SERIALIZATION_MISDECLARATION, 2, misdeclaration("com.A", "bad serialVersionUID")));
            builder.onRecord(rec(Type.SERIALIZATION_MISDECLARATION, 3, misdeclaration("com.B", "non-private writeObject")));

            List<MisdeclarationStat> result = builder.build();

            assertEquals(2, result.size());
            assertEquals("com.A", result.getFirst().misdeclaredClass());
            assertEquals("bad serialVersionUID", result.getFirst().message());
            assertEquals(2, result.getFirst().count());
            assertEquals("com.B", result.get(1).misdeclaredClass());
            assertEquals(1, result.get(1).count());
        }

        private ObjectNode misdeclaration(String misdeclaredClass, String message) {
            ObjectNode node = Json.createObject();
            node.put("misdeclaredClass", misdeclaredClass);
            node.put("message", message);
            return node;
        }
    }
}
