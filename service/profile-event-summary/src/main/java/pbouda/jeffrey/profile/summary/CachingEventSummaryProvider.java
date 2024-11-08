/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
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

package pbouda.jeffrey.profile.summary;

import com.fasterxml.jackson.core.type.TypeReference;
import pbouda.jeffrey.common.persistence.CacheKey;
import pbouda.jeffrey.common.persistence.CacheRepository;
import pbouda.jeffrey.profile.summary.event.EventSummary;

import java.util.List;
import java.util.Optional;

public class CachingEventSummaryProvider implements EventSummaryProvider {

    private static final TypeReference<List<EventSummary>> EVENT_SUMMARIES_TYPE =
            new TypeReference<List<EventSummary>>() {
            };

    private final EventSummaryProvider eventSummaryProvider;
    private final CacheRepository cacheRepository;

    public CachingEventSummaryProvider(
            EventSummaryProvider eventSummaryProvider,
            CacheRepository cacheRepository) {

        this.eventSummaryProvider = eventSummaryProvider;
        this.cacheRepository = cacheRepository;
    }

    @Override
    public List<EventSummary> get() {
        Optional<List<EventSummary>> cachedSummaries =
                cacheRepository.get(CacheKey.EVENT_SUMMARY, EVENT_SUMMARIES_TYPE);

        if (cachedSummaries.isPresent()) {
            return cachedSummaries.get();
        } else {
            List<EventSummary> eventSummaries = eventSummaryProvider.get();
            cacheRepository.insert(CacheKey.EVENT_SUMMARY, eventSummaries);
            return eventSummaries;
        }
    }
}
