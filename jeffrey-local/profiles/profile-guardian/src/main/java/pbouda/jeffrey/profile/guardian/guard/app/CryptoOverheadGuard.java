/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package pbouda.jeffrey.profile.guardian.guard.app;

import pbouda.jeffrey.profile.common.analysis.AnalysisResult;
import pbouda.jeffrey.profile.guardian.Formatter;
import pbouda.jeffrey.profile.guardian.guard.TraversableGuard;
import pbouda.jeffrey.profile.guardian.matcher.FrameMatchers;
import pbouda.jeffrey.profile.guardian.preconditions.Preconditions;
import pbouda.jeffrey.profile.guardian.traverse.MatchingType;
import pbouda.jeffrey.profile.guardian.traverse.ResultType;
import pbouda.jeffrey.profile.guardian.traverse.TargetFrameType;

public class CryptoOverheadGuard extends TraversableGuard {

    public CryptoOverheadGuard(ProfileInfo profileInfo, double threshold) {
        this("Crypto/TLS Overhead", ResultType.SAMPLES, profileInfo, threshold);
    }

    public CryptoOverheadGuard(String guardName, ResultType resultType, ProfileInfo profileInfo, double threshold) {
        super(guardName,
                profileInfo,
                threshold,
                FrameMatchers.composite(
                        FrameMatchers.prefix("javax.crypto."),
                        FrameMatchers.composite(
                                FrameMatchers.prefix("sun.security.ssl."),
                                FrameMatchers.prefix("sun.security.provider."))),
                Category.APPLICATION,
                TargetFrameType.JAVA,
                MatchingType.FULL_MATCH,
                resultType);
    }

    @Override
    protected String summary() {
        Result result = getResult();

        String direction = result.severity() == AnalysisResult.Severity.OK ? "lower" : "higher";
        return "The ratio between a total number of samples (" + result.totalValue() + ") and " +
                "samples with crypto/TLS activity (" + result.observedValue() + ") " +
                "is " + direction + " than the threshold (" +
                Formatter.formatRatio(result.ratioResult()) + " / " + result.threshold() + ").";
    }

    @Override
    protected String explanation() {
        return """
                Cryptographic operations and TLS processing can consume significant CPU, especially during
                TLS handshakes, bulk encryption/decryption, and certificate validation. This is often seen
                in applications with many short-lived HTTPS connections or heavy payload encryption.
                """;
    }

    @Override
    protected String solution() {
        Result result = getResult();
        if (result.severity() == AnalysisResult.Severity.OK) {
            return null;
        } else {
            return """
                    <ul>
                        <li>Enable TLS session resumption and session tickets to reduce handshake overhead
                        <li>Use HTTP/2 or connection pooling to reduce the number of TLS handshakes
                        <li>Consider hardware acceleration for crypto operations (AES-NI is usually enabled by default)
                        <li>Review cipher suite configuration - prefer modern, efficient algorithms
                    </ul>
                    """;
        }
    }

    @Override
    public Preconditions preconditions() {
        return Preconditions.builder().build();
    }
}
