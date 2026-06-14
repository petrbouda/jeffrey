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
