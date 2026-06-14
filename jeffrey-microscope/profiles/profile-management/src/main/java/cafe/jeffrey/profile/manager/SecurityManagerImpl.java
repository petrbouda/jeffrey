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

import cafe.jeffrey.profile.manager.model.security.CertificateBuilder;
import cafe.jeffrey.profile.manager.model.security.CertificateBuilder.CertInfo;
import cafe.jeffrey.profile.manager.model.security.CertificateValidationBuilder;
import cafe.jeffrey.profile.manager.model.security.DeserializationBuilder;
import cafe.jeffrey.profile.manager.model.security.ProviderServiceBuilder;
import cafe.jeffrey.profile.manager.model.security.SecurityData;
import cafe.jeffrey.profile.manager.model.security.SecurityData.CertificateStat;
import cafe.jeffrey.profile.manager.model.security.SecurityData.MisdeclarationStat;
import cafe.jeffrey.profile.manager.model.security.SecurityData.ProviderServiceStat;
import cafe.jeffrey.profile.manager.model.security.SecurityData.SecurityHeader;
import cafe.jeffrey.profile.manager.model.security.SerializationMisdeclarationBuilder;
import cafe.jeffrey.profile.manager.model.security.TlsHandshakeBuilder;
import cafe.jeffrey.provider.profile.api.EventQueryConfigurer;
import cafe.jeffrey.provider.profile.api.ProfileEventStreamRepository;
import cafe.jeffrey.shared.common.model.ProfileInfo;
import cafe.jeffrey.shared.common.model.Type;
import cafe.jeffrey.shared.common.model.time.RelativeTimeRange;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SecurityManagerImpl implements SecurityManager {

    private static final long EXPIRING_SOON_WINDOW_MILLIS = 30L * 24 * 60 * 60 * 1000;
    private static final int RSA_MIN_BITS = 2048;
    private static final int EC_MIN_BITS = 256;
    private static final int MAX_CERTIFICATES = 200;
    private static final Set<String> RSA_LIKE_KEY_TYPES = Set.of("RSA", "DSA", "DH", "DIFFIEHELLMAN");
    private static final Set<String> WEAK_SIGNATURE_MARKERS = Set.of("MD2", "MD5", "SHA1");

    private final ProfileInfo profileInfo;
    private final ProfileEventStreamRepository eventStreamRepository;

    public SecurityManagerImpl(ProfileInfo profileInfo, ProfileEventStreamRepository eventStreamRepository) {
        this.profileInfo = profileInfo;
        this.eventStreamRepository = eventStreamRepository;
    }

    @Override
    public SecurityData securityData() {
        RelativeTimeRange timeRange = new RelativeTimeRange(profileInfo.profilingStartEnd());
        long recordingEndMillis = profileInfo.profilingStartEnd().end().toEpochMilli();

        TlsHandshakeBuilder.Result tls = eventStreamRepository.genericStreaming(
                configurer(Type.TLS_HANDSHAKE), new TlsHandshakeBuilder(timeRange));

        Map<Long, CertInfo> certInfos = eventStreamRepository.genericStreaming(
                configurer(Type.X509_CERTIFICATE), new CertificateBuilder());
        Map<Long, Long> validations = eventStreamRepository.genericStreaming(
                configurer(Type.X509_VALIDATION), new CertificateValidationBuilder());

        DeserializationBuilder.Result deserialization = eventStreamRepository.genericStreaming(
                configurer(Type.DESERIALIZATION), new DeserializationBuilder());

        List<MisdeclarationStat> misdeclarations = eventStreamRepository.genericStreaming(
                configurer(Type.SERIALIZATION_MISDECLARATION), new SerializationMisdeclarationBuilder());

        List<ProviderServiceStat> providers = eventStreamRepository.genericStreaming(
                configurer(Type.SECURITY_PROVIDER_SERVICE), new ProviderServiceBuilder());

        List<CertificateStat> certificates = buildCertificates(certInfos, validations, recordingEndMillis);
        long flagged = certificates.stream()
                .filter(c -> c.weakKey() || c.weakSignature() || c.expired() || c.expiringSoon())
                .count();

        SecurityHeader header = new SecurityHeader(
                tls.total(),
                tls.distinctPeers(),
                certificates.size(),
                flagged,
                deserialization.summary().totalEvents(),
                deserialization.summary().rejectedEvents());

        return new SecurityData(
                header,
                tls.timeline(),
                tls.protocols(),
                tls.ciphers(),
                tls.peers(),
                certificates,
                deserialization.summary(),
                deserialization.types(),
                misdeclarations,
                providers);
    }

    private static EventQueryConfigurer configurer(Type type) {
        return new EventQueryConfigurer().withEventType(type).withJsonFields();
    }

    static List<CertificateStat> buildCertificates(
            Map<Long, CertInfo> certInfos, Map<Long, Long> validations, long recordingEndMillis) {
        return certInfos.values().stream()
                .map(cert -> new CertificateStat(
                        cert.subject(),
                        cert.issuer(),
                        cert.keyType(),
                        cert.keyLength(),
                        cert.algorithm(),
                        cert.validFrom(),
                        cert.validUntil(),
                        validations.getOrDefault(cert.certificateId(), 0L),
                        isWeakKey(cert.keyType(), cert.keyLength()),
                        isWeakSignature(cert.algorithm()),
                        isExpired(cert.validUntil(), recordingEndMillis),
                        isExpiringSoon(cert.validUntil(), recordingEndMillis)))
                .sorted(Comparator.comparingLong(SecurityManagerImpl::flagRank).reversed()
                        .thenComparing(Comparator.comparingLong(CertificateStat::validationCount).reversed()))
                .limit(MAX_CERTIFICATES)
                .toList();
    }

    private static long flagRank(CertificateStat cert) {
        return (cert.weakKey() || cert.weakSignature() || cert.expired() || cert.expiringSoon()) ? 1 : 0;
    }

    private static boolean isWeakKey(String keyType, int keyLength) {
        if (keyType == null || keyLength <= 0) {
            return false;
        }
        String normalized = keyType.toUpperCase().replace("-", "");
        if (RSA_LIKE_KEY_TYPES.contains(normalized)) {
            return keyLength < RSA_MIN_BITS;
        }
        if (normalized.startsWith("EC")) {
            return keyLength < EC_MIN_BITS;
        }
        return false;
    }

    private static boolean isWeakSignature(String algorithm) {
        if (algorithm == null) {
            return false;
        }
        String upper = algorithm.toUpperCase().replace("-", "");
        return WEAK_SIGNATURE_MARKERS.stream().anyMatch(upper::contains);
    }

    private static boolean isExpired(long validUntil, long recordingEndMillis) {
        return validUntil > 0 && validUntil < recordingEndMillis;
    }

    private static boolean isExpiringSoon(long validUntil, long recordingEndMillis) {
        return validUntil > 0
                && validUntil >= recordingEndMillis
                && (validUntil - recordingEndMillis) < EXPIRING_SOON_WINDOW_MILLIS;
    }
}
