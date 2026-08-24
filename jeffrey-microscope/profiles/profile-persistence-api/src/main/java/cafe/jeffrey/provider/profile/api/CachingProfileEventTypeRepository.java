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

package cafe.jeffrey.provider.profile.api;

import tools.jackson.core.type.TypeReference;
import cafe.jeffrey.shared.common.CacheKey;
import cafe.jeffrey.shared.common.model.EventSummary;
import cafe.jeffrey.shared.common.model.SpanScope;
import cafe.jeffrey.shared.common.model.Type;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Caches the profile-wide event summaries, the way {@code CachingThreadProvider} and
 * {@code CachingGuardianProvider} cache theirs.
 * <p>
 * {@link #eventSummaries()} is a {@code GROUP BY event_type} over every event in the profile, and
 * it is asked for constantly: the Event Viewer, the Guardian, the feature checks that decide which
 * pages are available, the flamegraph event panels and both export managers all open with it. The
 * answer cannot change — a profile's events are written once during initialization and never
 * updated — so paying for that scan more than once is pure repetition.
 * <p>
 * Only the no-argument variant is cached. The two narrowed overloads take a type list and an
 * optional {@link SpanScope}, so their answers are per-call rather than per-profile and a single
 * cache entry could not represent them.
 * <p>
 * An empty answer is never written to the cache. The entry outlives the process, and a profile is
 * reachable over HTTP from the moment its row is inserted — which is before initialization has
 * written {@code event_types}. A request arriving in that window would otherwise persist "this
 * profile has no event types" for the life of the profile. Recomputing for a genuinely empty
 * profile costs an aggregate over an empty table, which is what makes the rule safe to apply
 * bluntly.
 */
public class CachingProfileEventTypeRepository implements ProfileEventTypeRepository {

    private static final TypeReference<List<EventSummary>> SUMMARIES_TYPE = new TypeReference<>() {
    };

    private final ProfileEventTypeRepository delegate;
    private final ProfileCacheRepository cacheRepository;

    public CachingProfileEventTypeRepository(
            ProfileEventTypeRepository delegate,
            ProfileCacheRepository cacheRepository) {

        this.delegate = delegate;
        this.cacheRepository = cacheRepository;
    }

    /**
     * Read-through by hand rather than through {@link CachingSupplier}, which writes whatever the
     * delegate returned: the empty-answer rule above is the reason this cache exists at all rather
     * than being a liability.
     */
    @Override
    public List<EventSummary> eventSummaries() {
        Optional<List<EventSummary>> cached =
                cacheRepository.get(CacheKey.PROFILE_EVENT_SUMMARY, SUMMARIES_TYPE);
        if (cached.isPresent()) {
            return cached.get();
        }

        List<EventSummary> summaries = delegate.eventSummaries();
        if (!summaries.isEmpty()) {
            cacheRepository.put(CacheKey.PROFILE_EVENT_SUMMARY, summaries);
        }
        return summaries;
    }

    @Override
    public Optional<EventTypeWithFields> singleFieldsByEventType(Type type) {
        return delegate.singleFieldsByEventType(type);
    }

    @Override
    public Map<Type, EventTypeWithFields> singleFieldsByEventTypes(List<Type> types) {
        return delegate.singleFieldsByEventTypes(types);
    }

    @Override
    public List<FieldDescription> eventColumns(Type type) {
        return delegate.eventColumns(type);
    }

    @Override
    public List<EventSummary> eventSummaries(List<Type> types) {
        return delegate.eventSummaries(types);
    }

    @Override
    public List<EventSummary> eventSummaries(List<Type> types, SpanScope spanScope) {
        return delegate.eventSummaries(types, spanScope);
    }
}
