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

package pbouda.jeffrey.profile.feature.checker;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import pbouda.jeffrey.shared.common.model.EventSummary;
import pbouda.jeffrey.shared.common.model.Type;
import pbouda.jeffrey.profile.feature.FeatureCheckResult;
import pbouda.jeffrey.profile.feature.FeatureType;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SamplesFeatureChecker")
class SamplesFeatureCheckerTest {

    private static EventSummary summary(Type type, long samples) {
        return new EventSummary(type.code(), type.code(), null, null, samples, 0, false, false, List.of(), Map.of(), Map.of());
    }

    @Nested
    @DisplayName("Single event type")
    class SingleType {

        @Test
        @DisplayName("Feature is enabled when event has samples > 0")
        void enabledWhenSamplesExist() {
            var checker = new SamplesFeatureChecker(FeatureType.HTTP_SERVER_DASHBOARD, Type.HTTP_SERVER_EXCHANGE);
            Map<Type, EventSummary> summaries = Map.of(
                    Type.HTTP_SERVER_EXCHANGE, summary(Type.HTTP_SERVER_EXCHANGE, 42));

            FeatureCheckResult result = checker.check(summaries);

            assertTrue(result.enabled());
            assertEquals(FeatureType.HTTP_SERVER_DASHBOARD, result.type());
        }

        @Test
        @DisplayName("Feature is disabled when event has 0 samples")
        void disabledWhenZeroSamples() {
            var checker = new SamplesFeatureChecker(FeatureType.HTTP_SERVER_DASHBOARD, Type.HTTP_SERVER_EXCHANGE);
            Map<Type, EventSummary> summaries = Map.of(
                    Type.HTTP_SERVER_EXCHANGE, summary(Type.HTTP_SERVER_EXCHANGE, 0));

            FeatureCheckResult result = checker.check(summaries);

            assertFalse(result.enabled());
        }

        @Test
        @DisplayName("Feature is disabled when event type is absent from summaries")
        void disabledWhenEventAbsent() {
            var checker = new SamplesFeatureChecker(FeatureType.HTTP_SERVER_DASHBOARD, Type.HTTP_SERVER_EXCHANGE);
            Map<Type, EventSummary> summaries = Map.of();

            FeatureCheckResult result = checker.check(summaries);

            assertFalse(result.enabled());
        }
    }

    @Nested
    @DisplayName("Multiple event types")
    class MultipleTypes {

        @Test
        @DisplayName("Feature is enabled if at least one type has samples")
        void enabledWhenAtLeastOneTypeHasSamples() {
            var checker = new SamplesFeatureChecker(
                    FeatureType.JDBC_STATEMENTS_DASHBOARD,
                    List.of(Type.JDBC_INSERT, Type.JDBC_QUERY));

            Map<Type, EventSummary> summaries = Map.of(
                    Type.JDBC_INSERT, summary(Type.JDBC_INSERT, 0),
                    Type.JDBC_QUERY, summary(Type.JDBC_QUERY, 10));

            FeatureCheckResult result = checker.check(summaries);

            assertTrue(result.enabled());
        }

        @Test
        @DisplayName("Feature is disabled when all types have 0 samples")
        void disabledWhenAllZero() {
            var checker = new SamplesFeatureChecker(
                    FeatureType.JDBC_STATEMENTS_DASHBOARD,
                    List.of(Type.JDBC_INSERT, Type.JDBC_QUERY));

            Map<Type, EventSummary> summaries = Map.of(
                    Type.JDBC_INSERT, summary(Type.JDBC_INSERT, 0),
                    Type.JDBC_QUERY, summary(Type.JDBC_QUERY, 0));

            FeatureCheckResult result = checker.check(summaries);

            assertFalse(result.enabled());
        }

        @Test
        @DisplayName("Feature is disabled when none of the types are present")
        void disabledWhenNonePresent() {
            var checker = new SamplesFeatureChecker(
                    FeatureType.JDBC_STATEMENTS_DASHBOARD,
                    List.of(Type.JDBC_INSERT, Type.JDBC_QUERY));

            Map<Type, EventSummary> summaries = Map.of();

            FeatureCheckResult result = checker.check(summaries);

            assertFalse(result.enabled());
        }
    }
}
