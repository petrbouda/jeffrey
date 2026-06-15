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
import cafe.jeffrey.profile.guardian.definition.GuardDefinition;
import cafe.jeffrey.profile.guardian.definition.GuardDefinitions;
import cafe.jeffrey.provider.profile.api.CachingSupplier;
import cafe.jeffrey.provider.profile.api.ProfileCacheRepository;
import cafe.jeffrey.shared.common.CacheKey;

import java.util.List;

/**
 * A caching decorator for {@link GuardianProvider} that caches the result in a {@link ProfileCacheRepository}.
 * <p>
 * The cache key is versioned with a short stable hash of the effective {@link GuardDefinitions} so that
 * editing, adding, disabling, or removing a guard silently invalidates the existing cached analysis —
 * without this, users tuning guards from the UI would keep seeing stale results from before the change.
 */
public class CachingGuardianProvider implements GuardianProvider {

    private static final TypeReference<List<GuardianResult>> GUARDIAN_RESULT_TYPE =
            new TypeReference<List<GuardianResult>>() {
            };

    private final CachingSupplier<List<GuardianResult>> cachingSupplier;

    public CachingGuardianProvider(
            ProfileCacheRepository cacheRepository,
            GuardianProvider delegate,
            GuardDefinitions definitions) {
        String versionedKey = CacheKey.PROFILE_GUARDIAN + ":" + configHash(definitions);
        this.cachingSupplier = new CachingSupplier<>(
                delegate, cacheRepository, versionedKey, GUARDIAN_RESULT_TYPE);
    }

    /**
     * Stable 8-hex-digit fingerprint of the effective guard configuration. Built from the record
     * {@code toString()} of every definition plus the per-group minimum-sample gates and hashed with
     * {@link String#hashCode()} — deterministic across JVM runs (unlike {@code enum.hashCode()}), so a
     * persisted cache survives a restart yet flips the moment any guard definition changes.
     */
    private static String configHash(GuardDefinitions definitions) {
        StringBuilder fingerprint = new StringBuilder();
        for (GuardDefinition definition : definitions.all()) {
            fingerprint.append(definition).append('\n');
        }
        return String.format("%08x", fingerprint.toString().hashCode());
    }

    @Override
    public List<GuardianResult> get() {
        return cachingSupplier.get();
    }
}
