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

package pbouda.jeffrey.profile.guardian;

import org.junit.jupiter.api.Test;
import tools.jackson.core.type.TypeReference;
import pbouda.jeffrey.provider.profile.repository.ProfileCacheRepository;
import pbouda.jeffrey.shared.common.CacheKey;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The E3 cache key must flip whenever any threshold in {@link GuardianProperties} changes,
 * so operators tuning via {@code application.properties} don't silently see stale analyses.
 */
class CachingGuardianProviderConfigHashTest {

    @Test
    void identicalProps_hitCacheOnSecondCall() {
        RecordingCacheRepository cache = new RecordingCacheRepository();
        AtomicInteger delegateInvocations = new AtomicInteger();
        GuardianProvider delegate = () -> {
            delegateInvocations.incrementAndGet();
            return List.of();
        };

        CachingGuardianProvider provider = new CachingGuardianProvider(cache, delegate, GuardianPropertiesTestDefaults.defaults());
        provider.get();
        provider.get();

        assertEquals(1, delegateInvocations.get(),
                "Second call with same props must hit the cache — delegate should not be invoked twice");
        assertEquals(1, cache.keysWritten.size());
        assertTrue(cache.keysWritten.getFirst().startsWith(CacheKey.PROFILE_GUARDIAN + ":"),
                "Cache key must be prefixed with the base PROFILE_GUARDIAN constant");
    }

    @Test
    void changingAnyThreshold_producesDifferentCacheKey() {
        RecordingCacheRepository cache = new RecordingCacheRepository();
        GuardianProvider delegate = List::of;

        GuardianProperties original = GuardianPropertiesTestDefaults.defaults();
        // Tweak a single threshold; hash should flip.
        GuardianProperties tweaked = copyWithLogbackWarning(original, original.logbackWarningThreshold() + 0.001);

        new CachingGuardianProvider(cache, delegate, original).get();
        new CachingGuardianProvider(cache, delegate, tweaked).get();

        assertEquals(2, cache.keysWritten.size());
        assertNotEquals(cache.keysWritten.get(0), cache.keysWritten.get(1),
                "A threshold change must flip the versioned cache key");
    }

    @Test
    void differentProviders_sameProps_agreeOnKey() {
        // Two separately-constructed providers with equal props must stamp the exact same key,
        // otherwise two nodes / two request cycles would stomp each other's cache.
        RecordingCacheRepository cacheA = new RecordingCacheRepository();
        RecordingCacheRepository cacheB = new RecordingCacheRepository();
        GuardianProvider delegate = List::of;

        new CachingGuardianProvider(cacheA, delegate, GuardianPropertiesTestDefaults.defaults()).get();
        new CachingGuardianProvider(cacheB, delegate, GuardianPropertiesTestDefaults.defaults()).get();

        assertEquals(cacheA.keysWritten.getFirst(), cacheB.keysWritten.getFirst());
    }

    /** Rebuilds a {@link GuardianProperties} with a single field changed — reflection-free. */
    private static GuardianProperties copyWithLogbackWarning(GuardianProperties p, double newValue) {
        return new GuardianProperties(
                p.minSamplesExecution(),
                newValue, p.logbackInfoThreshold(),
                p.log4jWarningThreshold(), p.log4jInfoThreshold(),
                p.hashMapCollisionWarningThreshold(), p.hashMapCollisionInfoThreshold(),
                p.regexWarningThreshold(), p.regexInfoThreshold(),
                p.reflectionWarningThreshold(), p.reflectionInfoThreshold(),
                p.classLoadingWarningThreshold(), p.classLoadingInfoThreshold(),
                p.serializationWarningThreshold(), p.serializationInfoThreshold(),
                p.xmlParsingWarningThreshold(), p.xmlParsingInfoThreshold(),
                p.jsonProcessingWarningThreshold(), p.jsonProcessingInfoThreshold(),
                p.exceptionWarningThreshold(), p.exceptionInfoThreshold(),
                p.stringConcatWarningThreshold(), p.stringConcatInfoThreshold(),
                p.threadSyncWarningThreshold(), p.threadSyncInfoThreshold(),
                p.cryptoWarningThreshold(), p.cryptoInfoThreshold(),
                p.compressWarningThreshold(), p.compressInfoThreshold(),
                p.jitCompilationWarningThreshold(), p.jitCompilationInfoThreshold(),
                p.deoptimizationWarningThreshold(), p.deoptimizationInfoThreshold(),
                p.safepointWarningThreshold(), p.safepointInfoThreshold(),
                p.vmOperationWarningThreshold(), p.vmOperationInfoThreshold(),
                p.gcWarningThreshold(), p.gcInfoThreshold(),
                p.finalizerCleanerWarningThreshold(), p.finalizerCleanerInfoThreshold(),
                p.minSamplesAllocation(),
                p.logbackAllocWarningThreshold(), p.logbackAllocInfoThreshold(),
                p.log4jAllocWarningThreshold(), p.log4jAllocInfoThreshold(),
                p.hashMapCollisionAllocWarningThreshold(), p.hashMapCollisionAllocInfoThreshold(),
                p.regexAllocWarningThreshold(), p.regexAllocInfoThreshold(),
                p.stringConcatAllocWarningThreshold(), p.stringConcatAllocInfoThreshold(),
                p.exceptionAllocWarningThreshold(), p.exceptionAllocInfoThreshold(),
                p.boxingAllocWarningThreshold(), p.boxingAllocInfoThreshold(),
                p.collectionAllocWarningThreshold(), p.collectionAllocInfoThreshold(),
                p.tlabWasteWarningThreshold(), p.tlabWasteInfoThreshold(),
                p.minSamplesWallClock(),
                p.minSamplesBlocking(),
                p.lockContentionWarningThreshold(), p.lockContentionInfoThreshold(),
                p.ioBlockingWarningThreshold(), p.ioBlockingInfoThreshold(),
                p.dbPoolBlockingWarningThreshold(), p.dbPoolBlockingInfoThreshold(),
                p.httpClientBlockingWarningThreshold(), p.httpClientBlockingInfoThreshold(),
                p.logbackBlockingWarningThreshold(), p.logbackBlockingInfoThreshold(),
                p.log4jBlockingWarningThreshold(), p.log4jBlockingInfoThreshold(),
                p.safepointOutlierWarningMillis(), p.safepointOutlierInfoMillis(),
                p.vthreadPinnedOutlierWarningMillis(), p.vthreadPinnedOutlierInfoMillis());
    }

    /** Minimal in-memory cache that records every key written. */
    private static final class RecordingCacheRepository implements ProfileCacheRepository {
        private final Map<String, Object> store = new HashMap<>();
        private final List<String> keysWritten = new java.util.ArrayList<>();

        @Override public void put(String key, Object content) {
            keysWritten.add(key);
            store.put(key, content);
        }
        @Override public boolean contains(String key) { return store.containsKey(key); }
        @SuppressWarnings("unchecked")
        @Override public <T> Optional<T> get(String key, Class<T> type) {
            return Optional.ofNullable((T) store.get(key));
        }
        @SuppressWarnings("unchecked")
        @Override public <T> Optional<T> get(String key, TypeReference<T> type) {
            return Optional.ofNullable((T) store.get(key));
        }
        @Override public void clearAll() { store.clear(); }
    }
}
