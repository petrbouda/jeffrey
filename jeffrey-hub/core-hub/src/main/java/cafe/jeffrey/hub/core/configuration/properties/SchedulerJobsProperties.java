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

import cafe.jeffrey.shared.common.model.job.JobType;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Resolves scheduler job configuration from the Spring {@code Environment}.
 * Built-in defaults live in {@code scheduler-defaults.properties} (loaded as
 * a low-precedence {@link org.springframework.context.annotation.PropertySource}
 * on {@code HubAppConfiguration}); {@code application.properties} overrides
 * any field by redeclaring the same key.
 * <p>
 * Property keys use the lower-kebab-case form of {@link JobType} (for example
 * {@code projects-synchronizer} for {@code PROJECTS_SYNCHRONIZER}).
 */
@ConfigurationProperties("jeffrey.hub.scheduler")
public class SchedulerJobsProperties {

    private Map<String, JobConfig> jobs = new HashMap<>();

    public Map<String, JobConfig> getJobs() {
        return jobs;
    }

    public void setJobs(Map<String, JobConfig> jobs) {
        this.jobs = jobs;
    }

    public JobConfig forType(JobType jobType) {
        JobConfig config = jobs.get(toKey(jobType));
        if (config == null) {
            return new JobConfig(false, Duration.ofMinutes(1), Map.of());
        }
        return config;
    }

    public static String toKey(JobType jobType) {
        return jobType.name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    public static class JobConfig {
        private boolean enabled;
        private Duration period;
        private Map<String, String> params = new HashMap<>();

        public JobConfig() {
        }

        public JobConfig(boolean enabled, Duration period, Map<String, String> params) {
            this.enabled = enabled;
            this.period = period;
            this.params = params;
        }

        public boolean enabled() {
            return enabled;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public Duration period() {
            return period;
        }

        public Duration getPeriod() {
            return period;
        }

        public void setPeriod(Duration period) {
            this.period = period;
        }

        public Map<String, String> params() {
            return params;
        }

        public Map<String, String> getParams() {
            return params;
        }

        public void setParams(Map<String, String> params) {
            this.params = params;
        }

        /**
         * Resolves a required duration param. Every job param has a built-in default in
         * {@code scheduler-defaults.properties} (the single source of default values), so a
         * missing key means a broken configuration — fail fast with a clear message instead
         * of falling back to a value hidden in code. Supports the {@code 31d}/{@code 1h}/
         * {@code 5m}/{@code 10s} shorthand notation in addition to ISO-8601.
         */
        public Duration durationParam(String name) {
            return Duration.parse(normalizeDuration(requiredParam(name)));
        }

        /**
         * Resolves a required integer param — same contract as {@link #durationParam}.
         */
        public int intParam(String name) {
            String value = requiredParam(name);
            try {
                return Integer.parseInt(value.trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(
                        "Scheduler job param is not a valid integer: param=" + name + " value=" + value);
            }
        }

        private String requiredParam(String name) {
            String value = params.get(name);
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException(
                        "Missing scheduler job param (no default in scheduler-defaults.properties?): param=" + name);
            }
            return value;
        }

        /**
         * Allows users to write {@code 31d} / {@code 5m} / {@code 1h} in property values
         * (Spring's {@code @DurationUnit}-style notation) by translating them to ISO-8601
         * before {@link Duration#parse} consumes them.
         */
        private static String normalizeDuration(String value) {
            String trimmed = value.trim().toLowerCase(Locale.ROOT);
            if (trimmed.startsWith("p") || trimmed.startsWith("-p")) {
                return value.trim().toUpperCase(Locale.ROOT);
            }
            if (trimmed.endsWith("d")) {
                return "P" + trimmed.substring(0, trimmed.length() - 1) + "D";
            }
            if (trimmed.endsWith("h")) {
                return "PT" + trimmed.substring(0, trimmed.length() - 1) + "H";
            }
            if (trimmed.endsWith("m")) {
                return "PT" + trimmed.substring(0, trimmed.length() - 1) + "M";
            }
            if (trimmed.endsWith("s")) {
                return "PT" + trimmed.substring(0, trimmed.length() - 1) + "S";
            }
            return "PT" + trimmed + "S";
        }
    }
}
