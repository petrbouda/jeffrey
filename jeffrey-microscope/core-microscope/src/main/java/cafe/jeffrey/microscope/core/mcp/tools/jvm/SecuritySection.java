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
package cafe.jeffrey.microscope.core.mcp.tools.jvm;

import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.profile.manager.model.security.SecurityData;
import cafe.jeffrey.shared.common.model.Type;

import java.util.List;
import java.util.Set;

/**
 * TLS, certificates and deserialization.
 * <p>
 * This is the one section whose findings are usually not about speed. A certificate a fortnight from
 * expiry, a peer still negotiating an obsolete protocol, a deserialization filter rejecting payloads —
 * none of them show up as time anywhere, and all of them are the kind of thing a reader is glad to
 * learn from a recording they took for another reason.
 * <p>
 * The performance reading is real too: a handshake is expensive, and an application making thousands
 * of them is one that is not reusing connections.
 */
public record SecuritySection(ProfileManager profileManager) implements JvmSection {

    public static final String ID = "security";

    private static final String TITLE = "Security & TLS";

    private static final int ROWS_LIMIT = 15;

    private static final Set<Type> EVENT_TYPES = Set.of(
            Type.TLS_HANDSHAKE,
            Type.X509_CERTIFICATE,
            Type.X509_VALIDATION,
            Type.DESERIALIZATION,
            Type.SECURITY_PROVIDER_SERVICE,
            Type.SERIALIZATION_MISDECLARATION);

    private static final List<String> NEXT_STEPS = List.of(
            "Many handshakes for few peers means connections are not being reused. The socket side of "
                    + "that is io_endpoints with kind SOCKET, which names what is being reconnected to.",
            "A flagged certificate is a finding about the deployment rather than the code, and this "
                    + "recording is evidence of what the JVM actually presented rather than what a "
                    + "manifest says it should.",
            "Handshake time is off-CPU: it belongs to the waiting in blocking_ and io_, not to any "
                    + "frame an execution flamegraph will show.");

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String title() {
        return TITLE;
    }

    @Override
    public Set<Type> eventTypes() {
        return EVENT_TYPES;
    }

    @Override
    public List<String> nextSteps() {
        return NEXT_STEPS;
    }

    @Override
    public Object render() {
        SecurityData data = profileManager.securityManager().securityData();
        SecurityData.SecurityHeader header = data.header();

        return new SecurityDashboard(
                header.tlsHandshakes(),
                header.distinctPeers(),
                header.certificates(),
                header.flaggedCertificates(),
                header.deserializationEvents(),
                header.deserializationRejected(),
                counts(data.protocols()),
                counts(data.ciphers()),
                counts(data.peers()),
                certificates(data),
                deserializationTypes(data));
    }

    private static List<NamedCount> counts(List<SecurityData.NamedCount> source) {
        return source.stream()
                .limit(ROWS_LIMIT)
                .map(entry -> new NamedCount(entry.name(), entry.count()))
                .toList();
    }

    /**
     * Only the certificates worth reading about: a run behind a busy service validates the same handful
     * over and over, and the flagged ones are the reason to look at all.
     */
    private static List<Certificate> certificates(SecurityData data) {
        return data.certificates().stream()
                .filter(certificate -> certificate.weakKey() || certificate.weakSignature()
                        || certificate.expired() || certificate.expiringSoon())
                .limit(ROWS_LIMIT)
                .map(certificate -> new Certificate(
                        certificate.subject(),
                        certificate.issuer(),
                        certificate.keyType(),
                        certificate.keyLength(),
                        certificate.signatureAlgorithm(),
                        certificate.validUntil(),
                        certificate.weakKey(),
                        certificate.weakSignature(),
                        certificate.expired(),
                        certificate.expiringSoon()))
                .toList();
    }

    private static List<DeserializedType> deserializationTypes(SecurityData data) {
        return data.deserializationTypes().stream()
                .limit(ROWS_LIMIT)
                .map(entry -> new DeserializedType(
                        entry.type(), entry.count(), entry.totalBytes(), entry.maxBytes()))
                .toList();
    }

    /**
     * @param flaggedCertificates certificates that are expired, expiring soon, or signed weakly; the
     *                            certificate list below carries only those, since a healthy one says
     *                            nothing a reader needs
     */
    private record SecurityDashboard(
            long tlsHandshakes,
            long distinctPeers,
            long certificates,
            long flaggedCertificates,
            long deserializationEvents,
            long deserializationRejected,
            List<NamedCount> protocols,
            List<NamedCount> ciphers,
            List<NamedCount> peers,
            List<Certificate> flagged,
            List<DeserializedType> deserializationTypes) {
    }

    private record NamedCount(String name, long count) {
    }

    private record Certificate(
            String subject,
            String issuer,
            String keyType,
            int keyLength,
            String signatureAlgorithm,
            long validUntil,
            boolean weakKey,
            boolean weakSignature,
            boolean expired,
            boolean expiringSoon) {
    }

    private record DeserializedType(String type, long count, long totalBytes, long maxBytes) {
    }
}
