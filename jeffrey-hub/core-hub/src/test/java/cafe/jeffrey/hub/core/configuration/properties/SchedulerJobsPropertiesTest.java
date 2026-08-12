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

package cafe.jeffrey.hub.core.configuration.properties;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import cafe.jeffrey.hub.core.configuration.properties.SchedulerJobsProperties.JobConfig;
import cafe.jeffrey.shared.common.model.job.JobType;

import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SchedulerJobsPropertiesTest {

    private static JobConfig configWithParams(Map<String, String> params) {
        return new JobConfig(true, Duration.ofMinutes(1), params);
    }

    private static SchedulerJobsProperties propertiesWithJob(String key, Duration period) {
        var properties = new SchedulerJobsProperties();
        properties.setJobs(Map.of(key, new JobConfig(true, period, Map.of())));
        return properties;
    }

    /**
     * The scan period is the discovery latency, so operators tune it. It has to resolve however
     * the property source spelled the map key — an environment variable cannot carry a dash.
     */
    @Nested
    class JobLookup {

        @Test
        void resolvesTheCanonicalKebabCaseKey() {
            var properties = propertiesWithJob("workspace-reconciler", Duration.ofSeconds(2));

            assertEquals(Duration.ofSeconds(2),
                    properties.forType(JobType.WORKSPACE_RECONCILER).period());
        }

        @Test
        void resolvesTheKeyEnvironmentVariableBindingProduces() {
            // JEFFREY_HUB_SCHEDULER_JOBS_WORKSPACERECONCILER_PERIOD — relaxed binding drops the dash
            var properties = propertiesWithJob("workspacereconciler", Duration.ofSeconds(10));

            assertEquals(Duration.ofSeconds(10),
                    properties.forType(JobType.WORKSPACE_RECONCILER).period());
        }

        @Test
        void resolvesUnderscoreAndUppercaseSpellings() {
            assertEquals(Duration.ofSeconds(7),
                    propertiesWithJob("workspace_reconciler", Duration.ofSeconds(7))
                            .forType(JobType.WORKSPACE_RECONCILER).period());
            assertEquals(Duration.ofSeconds(8),
                    propertiesWithJob("WORKSPACE_RECONCILER", Duration.ofSeconds(8))
                            .forType(JobType.WORKSPACE_RECONCILER).period());
        }

        @Test
        void aJobWithNoConfigurationStaysDisabled() {
            var properties = new SchedulerJobsProperties();

            assertFalse(properties.forType(JobType.WORKSPACE_RECONCILER).enabled());
        }

        @Test
        void doesNotConfuseDistinctJobs() {
            var properties = propertiesWithJob("workspace-reconciler", Duration.ofSeconds(2));

            assertFalse(properties.forType(JobType.EXPIRED_INSTANCE_CLEANER).enabled());
        }
    }

    @Nested
    class DurationParam {

        @Test
        void parsesShorthandNotation() {
            JobConfig config = configWithParams(Map.of(
                    "days", "31d",
                    "hours", "12h",
                    "minutes", "5m",
                    "seconds", "30s"));

            assertEquals(Duration.ofDays(31), config.durationParam("days"));
            assertEquals(Duration.ofHours(12), config.durationParam("hours"));
            assertEquals(Duration.ofMinutes(5), config.durationParam("minutes"));
            assertEquals(Duration.ofSeconds(30), config.durationParam("seconds"));
        }

        @Test
        void parsesIso8601Notation() {
            JobConfig config = configWithParams(Map.of("retention", "P14D"));

            assertEquals(Duration.ofDays(14), config.durationParam("retention"));
        }

        @Test
        void failsFast_whenParamMissing() {
            JobConfig config = configWithParams(Map.of());

            IllegalArgumentException e = assertThrows(
                    IllegalArgumentException.class, () -> config.durationParam("retention"));
            assertTrue(e.getMessage().contains("retention"),
                    "Error message should name the missing param");
        }
    }

    @Nested
    class IntParam {

        @Test
        void parsesInteger() {
            JobConfig config = configWithParams(Map.of("max-versions", "5"));

            assertEquals(5, config.intParam("max-versions"));
        }

        @Test
        void failsFast_whenParamMissing() {
            JobConfig config = configWithParams(Map.of());

            assertThrows(IllegalArgumentException.class, () -> config.intParam("max-versions"));
        }

        @Test
        void failsFast_whenNotANumber() {
            JobConfig config = configWithParams(Map.of("max-versions", "many"));

            IllegalArgumentException e = assertThrows(
                    IllegalArgumentException.class, () -> config.intParam("max-versions"));
            assertTrue(e.getMessage().contains("max-versions"));
        }
    }
}
