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

import cafe.jeffrey.profile.manager.model.security.SecurityData;
import cafe.jeffrey.shared.common.model.ProfileInfo;

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
