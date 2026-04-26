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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import cafe.jeffrey.shared.common.model.EventSummary;
import cafe.jeffrey.shared.common.model.Type;
import cafe.jeffrey.profile.feature.FeatureType;
import cafe.jeffrey.provider.profile.repository.ProfileCacheRepository;
import cafe.jeffrey.provider.profile.repository.ProfileEventRepository;
import cafe.jeffrey.provider.profile.repository.ProfileEventTypeRepository;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProfileFeaturesManagerImpl")
class ProfileFeaturesManagerImplTest {

    @Mock
    ProfileEventRepository eventRepository;

    @Mock
    ProfileEventTypeRepository eventTypeRepository;

    @Mock
    ProfileCacheRepository cacheRepository;

    private static EventSummary summary(Type type, long samples) {
        return new EventSummary(type.code(), type.code(), null, null, samples, 0, false, false, List.of(), Map.of(), Map.of());
    }

    @Nested
    @DisplayName("Feature detection with no events")
    class NoEvents {

        @Test
        @DisplayName("All sample-based features are disabled when no event summaries exist")
        void allSampleFeaturesDisabledWhenEmpty() {
            when(eventTypeRepository.eventSummaries()).thenReturn(List.of());
            // Container checker: no container events
            when(eventRepository.latestJsonFields(any())).thenReturn(Optional.empty());
            // Perf counter checker: no cache
            when(cacheRepository.contains(any())).thenReturn(false);

            var manager = new ProfileFeaturesManagerImpl(eventRepository, eventTypeRepository, cacheRepository);
            List<FeatureType> disabledFeatures = manager.getDisabledFeatures();

            assertTrue(disabledFeatures.contains(FeatureType.HTTP_SERVER_DASHBOARD));
            assertTrue(disabledFeatures.contains(FeatureType.HTTP_CLIENT_DASHBOARD));
            assertTrue(disabledFeatures.contains(FeatureType.JDBC_STATEMENTS_DASHBOARD));
            assertTrue(disabledFeatures.contains(FeatureType.JDBC_POOL_DASHBOARD));
            assertTrue(disabledFeatures.contains(FeatureType.TRACING_DASHBOARD));
            assertTrue(disabledFeatures.contains(FeatureType.CONTAINER_DASHBOARD));
            assertTrue(disabledFeatures.contains(FeatureType.PERF_COUNTERS_DASHBOARD));
        }
    }

    @Nested
    @DisplayName("Individual feature detection")
    class IndividualFeatures {

        @Test
        @DisplayName("HTTP server feature is enabled when HTTP_SERVER_EXCHANGE has samples")
        void httpServerEnabled() {
            when(eventTypeRepository.eventSummaries()).thenReturn(List.of(
                    summary(Type.HTTP_SERVER_EXCHANGE, 10)));
            when(eventRepository.latestJsonFields(any())).thenReturn(Optional.empty());
            when(cacheRepository.contains(any())).thenReturn(false);

            var manager = new ProfileFeaturesManagerImpl(eventRepository, eventTypeRepository, cacheRepository);
            List<FeatureType> disabledFeatures = manager.getDisabledFeatures();

            assertFalse(disabledFeatures.contains(FeatureType.HTTP_SERVER_DASHBOARD));
            assertTrue(disabledFeatures.contains(FeatureType.HTTP_CLIENT_DASHBOARD));
        }

        @Test
        @DisplayName("Tracing feature is enabled when METHOD_TRACE has samples")
        void tracingEnabled() {
            when(eventTypeRepository.eventSummaries()).thenReturn(List.of(
                    summary(Type.METHOD_TRACE, 5)));
            when(eventRepository.latestJsonFields(any())).thenReturn(Optional.empty());
            when(cacheRepository.contains(any())).thenReturn(false);

            var manager = new ProfileFeaturesManagerImpl(eventRepository, eventTypeRepository, cacheRepository);
            List<FeatureType> disabledFeatures = manager.getDisabledFeatures();

            assertFalse(disabledFeatures.contains(FeatureType.TRACING_DASHBOARD));
        }

        @Test
        @DisplayName("JDBC statements feature is enabled when any JDBC statement type has samples")
        void jdbcStatementsEnabled() {
            when(eventTypeRepository.eventSummaries()).thenReturn(List.of(
                    summary(Type.JDBC_QUERY, 3)));
            when(eventRepository.latestJsonFields(any())).thenReturn(Optional.empty());
            when(cacheRepository.contains(any())).thenReturn(false);

            var manager = new ProfileFeaturesManagerImpl(eventRepository, eventTypeRepository, cacheRepository);
            List<FeatureType> disabledFeatures = manager.getDisabledFeatures();

            assertFalse(disabledFeatures.contains(FeatureType.JDBC_STATEMENTS_DASHBOARD));
        }
    }
}
