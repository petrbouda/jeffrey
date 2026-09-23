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

import cafe.jeffrey.timeseries.TimeseriesData;

import java.util.List;

/**
 * Security &amp; TLS insight from the JDK security JFR events: TLS handshakes
 * ({@code jdk.TLSHandshake}), X.509 certificates and validation
 * ({@code jdk.X509Certificate}/{@code jdk.X509Validation}), deserialization
 * ({@code jdk.Deserialization}) and crypto-provider usage ({@code jdk.SecurityProviderService}).
 *
 * @param header               headline counters
 * @param tlsTimeline          TLS handshakes per second (single series)
 * @param protocols            handshake counts by TLS protocol version
 * @param ciphers              handshake counts by cipher suite
 * @param peers                handshake counts by peer ({@code host:port})
 * @param certificates         observed certificates with security flags and validation counts
 * @param deserialization            deserialization summary (events / filter / exceptions)
 * @param deserializationTypes       top deserialized types by total bytes read
 * @param serializationMisdeclarations classes with serialization misdeclarations
 *                                   ({@code jdk.SerializationMisdeclaration}, JDK 26+)
 * @param cryptoProviders            crypto provider/algorithm usage counts
 */
public record SecurityData(
        SecurityHeader header,
        TimeseriesData tlsTimeline,
        List<NamedCount> protocols,
        List<NamedCount> ciphers,
        List<NamedCount> peers,
        List<CertificateStat> certificates,
        DeserializationSummary deserialization,
        List<DeserializationTypeStat> deserializationTypes,
        List<MisdeclarationStat> serializationMisdeclarations,
        List<ProviderServiceStat> cryptoProviders) {

    public record SecurityHeader(
            long tlsHandshakes,
            long distinctPeers,
            long certificates,
            long flaggedCertificates,
            long deserializationEvents,
            long deserializationRejected) {
    }

    public record NamedCount(String name, long count) {
    }

    public record CertificateStat(
            String subject,
            String issuer,
            String keyType,
            int keyLength,
            String signatureAlgorithm,
            long validFrom,
            long validUntil,
            long validationCount,
            boolean weakKey,
            boolean weakSignature,
            boolean expired,
            boolean expiringSoon) {
    }

    public record DeserializationSummary(
            long totalEvents,
            long filterConfiguredEvents,
            long rejectedEvents,
            long exceptionEvents) {
    }

    public record DeserializationTypeStat(String type, long count, long totalBytes, long maxBytes, long maxDepth) {
    }

    public record MisdeclarationStat(String misdeclaredClass, String message, long count) {
    }

    public record ProviderServiceStat(String provider, String type, String algorithm, long count) {
    }
}
