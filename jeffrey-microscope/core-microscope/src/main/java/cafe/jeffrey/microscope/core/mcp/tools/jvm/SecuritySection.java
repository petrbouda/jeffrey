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

package cafe.jeffrey.microscope.core.mcp.tools.jvm;

import cafe.jeffrey.microscope.core.mcp.MicroscopeView;
import cafe.jeffrey.microscope.core.mcp.tools.NextSteps;
import cafe.jeffrey.microscope.mcp.protocol.McpDescription;
import cafe.jeffrey.microscope.mcp.protocol.McpNullable;
import cafe.jeffrey.microscope.model.Type;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.profile.manager.model.io.IoKind;
import cafe.jeffrey.profile.manager.model.security.SecurityData;
import cafe.jeffrey.profile.mcp.McpFollowUp;

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
public record SecuritySection(ProfileManager profileManager) implements JvmSection<SecuritySection.SecurityDashboard> {

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

    private static final String RECONNECTS_WHY =
            "names what is being connected to; many handshakes for few peers means connections are not "
                    + "being reused";
    private static final String OFF_CPU_WHY =
            "shows the socket waiting a handshake belongs to, which is off-CPU and in no execution "
                    + "flamegraph frame";
    private static final String CERTIFICATE_GUIDANCE =
            "A flagged certificate is a finding about the deployment rather than the code, and this "
                    + "recording is evidence of what the JVM actually presented rather than what a manifest "
                    + "says it should.";

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
    public MicroscopeView view() {
        return MicroscopeView.SECURITY;
    }

    @Override
    public void followUp(NextSteps.Builder next, SecurityDashboard dashboard) {
        String profileId = profileManager.info().id();
        next.nextWhen(dashboard.tlsHandshakes() > 0, SectionCalls.on(SectionCalls.IO_ENDPOINTS, profileId)
                        .with(SectionCalls.KIND, IoKind.SOCKET)
                        .why(RECONNECTS_WHY))
                .nextWhen(dashboard.tlsHandshakes() > 0, SectionCalls.on(SectionCalls.IO_OVERVIEW, profileId)
                        .with(SectionCalls.KIND, IoKind.SOCKET)
                        .why(OFF_CPU_WHY))
                .guidanceWhen(dashboard.flaggedCertificates() > 0, CERTIFICATE_GUIDANCE);
    }

    @Override
    public SecurityDashboard render() {
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
                        certificate.validUntil() > 0 ? certificate.validUntil() : null,
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
    public record SecurityDashboard(
            long tlsHandshakes,
            long distinctPeers,
            long certificates,
            long flaggedCertificates,
            long deserializationEvents,
            long deserializationRejected,
            @McpDescription("The " + ROWS_LIMIT + " protocols negotiated most often")
            List<NamedCount> protocols,
            @McpDescription("The " + ROWS_LIMIT + " cipher suites negotiated most often")
            List<NamedCount> ciphers,
            @McpDescription("The " + ROWS_LIMIT + " peers handshaken with most often, out of distinctPeers")
            List<NamedCount> peers,
            @McpDescription("The first " + ROWS_LIMIT + " flagged certificates, out of flaggedCertificates")
            List<Certificate> flagged,
            @McpDescription("The " + ROWS_LIMIT + " types deserialized most often")
            List<DeserializedType> deserializationTypes) {
    }

    public record NamedCount(
            @McpNullable
            String name,
            long count) {
    }

    /**
     * @param validUntilEpochMs when the certificate expires, as UTC epoch milliseconds; null when the
     *                          recording did not say
     */
    public record Certificate(
            @McpNullable
            String subject,
            @McpNullable
            String issuer,
            @McpNullable
            String keyType,
            int keyLength,
            @McpNullable
            String signatureAlgorithm,
            @McpNullable
            Long validUntilEpochMs,
            boolean weakKey,
            boolean weakSignature,
            boolean expired,
            boolean expiringSoon) {
    }

    public record DeserializedType(
            @McpNullable
            String type,
            long count,
            long totalBytes,
            long maxBytes) {
    }

    /**
     * What {@code jvm_security} answers: the envelope every section shares, around this section's dashboard.
     */
    public record Answer(
            SectionStatus status,
            @McpNullable
            @McpDescription(SectionHeader.REASON)
            String reason,
            String profileId,
            @McpDescription(SectionHeader.SECTION)
            String section,
            String title,
            @McpNullable
            @McpDescription(SectionHeader.DASHBOARD)
            SecurityDashboard dashboard,
            McpFollowUp followUp,
            @McpDescription(SectionHeader.UI_LINK)
            String uiLink) {

        public static Answer of(SectionHeader header, SecurityDashboard dashboard) {
            return new Answer(header.status(), header.reason(), header.profileId(), header.section(),
                    header.title(), dashboard, header.followUp(), header.uiLink());
        }
    }
}
