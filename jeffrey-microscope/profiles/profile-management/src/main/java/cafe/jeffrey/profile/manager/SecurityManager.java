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

import cafe.jeffrey.profile.manager.model.security.SecurityData;
import cafe.jeffrey.microscope.model.ProfileInfo;

import java.util.function.Function;

/**
 * Security &amp; TLS insight for a single profile: TLS handshakes ({@code jdk.TLSHandshake}), X.509
 * certificates and validation ({@code jdk.X509Certificate}/{@code jdk.X509Validation}),
 * deserialization ({@code jdk.Deserialization}) and crypto-provider usage
 * ({@code jdk.SecurityProviderService}). All are optional, so consumers must handle empty results.
 */
public interface SecurityManager {

    @FunctionalInterface
    interface Factory extends Function<ProfileInfo, SecurityManager> {
    }

    /**
     * Composite security dashboard data: TLS handshake breakdowns, certificates with security flags,
     * deserialization summary/top-types, and crypto-provider usage.
     */
    SecurityData securityData();
}
