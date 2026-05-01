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

package cafe.jeffrey.profile.guardian;

import tools.jackson.core.type.TypeReference;
import cafe.jeffrey.provider.profile.api.CachingSupplier;
import cafe.jeffrey.provider.profile.api.ProfileCacheRepository;
import cafe.jeffrey.shared.common.CacheKey;

import java.util.List;
import java.util.Objects;

/**
 * A caching decorator for {@link GuardianProvider} that caches the result in a {@link ProfileCacheRepository}.
 * <p>
 * The cache key is versioned with a short stable hash of the current {@link GuardianProperties} so that changing
 * any threshold silently invalidates the existing cached analysis — without this, operators tweaking
 * {@code jeffrey.microscope.guardian.*} properties would keep seeing stale results from before the change.
 */
public class CachingGuardianProvider implements GuardianProvider {

    private static final TypeReference<List<GuardianResult>> GUARDIAN_RESULT_TYPE =
            new TypeReference<List<GuardianResult>>() {
            };

    private final CachingSupplier<List<GuardianResult>> cachingSupplier;

    public CachingGuardianProvider(
            ProfileCacheRepository cacheRepository,
            GuardianProvider delegate,
            GuardianProperties props) {
        String versionedKey = CacheKey.PROFILE_GUARDIAN + ":" + configHash(props);
        this.cachingSupplier = new CachingSupplier<>(
                delegate, cacheRepository, versionedKey, GUARDIAN_RESULT_TYPE);
    }

    /**
     * Stable 8-hex-digit fingerprint of the effective threshold configuration. Uses Java's
     * default {@code Objects.hash} over the record fields (records inherit a deterministic
     * hashCode based on component values), so any change to any threshold produces a different
     * hash — thereby a different cache key — on the very next call.
     */
    private static String configHash(GuardianProperties props) {
        int h = Objects.hashCode(props);
        return String.format("%08x", h);
    }

    @Override
    public List<GuardianResult> get() {
        return cachingSupplier.get();
    }
}
