/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cafe.jeffrey.hub.core.configuration.properties;

import cafe.jeffrey.hub.model.job.JobType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.convert.DurationStyle;

import java.time.Duration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Resolves scheduler job configuration from the Spring {@code Environment}.
 * Built-in defaults live in {@code scheduler-defaults.properties} (loaded as
 * a low-precedence {@link org.springframework.context.annotation.PropertySource}
 * on {@code HubAppConfiguration}); {@code application.properties} overrides
 * any field by redeclaring the same key.
 * <p>
 * Property keys use the lower-kebab-case form of {@link JobType} (for example
 * {@code workspace-reconciler} for {@code WORKSPACE_RECONCILER}).
 */
@ConfigurationProperties("jeffrey.hub.scheduler")
public class SchedulerJobsProperties {

    private static final Logger LOG = LoggerFactory.getLogger(SchedulerJobsProperties.class);

    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^a-zA-Z0-9]");

    private static final int DEFAULT_FAN_OUT_POOL_SIZE = 2;

    /** The period a job with no configuration at all reports; it is never scheduled anyway. */
    private static final Duration UNCONFIGURED_JOB_PERIOD = Duration.ofMinutes(1);

    private Map<String, JobConfig> jobs = new HashMap<>();

    /** Pool size for the PROJECT_FAN_OUT executor group (see {@code Job.ExecutorGroup}) */
    private int fanOutPoolSize = DEFAULT_FAN_OUT_POOL_SIZE;

    public Map<String, JobConfig> getJobs() {
        return jobs;
    }

    public void setJobs(Map<String, JobConfig> jobs) {
        this.jobs = jobs;
    }

    public int getFanOutPoolSize() {
        return fanOutPoolSize;
    }

    public void setFanOutPoolSize(int fanOutPoolSize) {
        this.fanOutPoolSize = fanOutPoolSize;
    }

    public JobConfig forType(JobType jobType) {
        JobConfig config = findConfig(jobType);
        if (config == null) {
            LOG.warn("No scheduler configuration found for job, it stays disabled: job={} expected_key={}",
                    jobType, toKey(jobType));
            return new JobConfig(false, UNCONFIGURED_JOB_PERIOD, Map.of());
        }
        return config;
    }

    /**
     * Resolves a job's configuration tolerantly of how the property source spelled the map key.
     * The canonical key is lower-kebab-case, but environment variables cannot carry a dash:
     * Spring's relaxed binding turns {@code JEFFREY_HUB_SCHEDULER_JOBS_WORKSPACERECONCILER_PERIOD}
     * into the key {@code workspacereconciler}. Matching on the canonical key alone would miss
     * that and silently fall back to a disabled job — the operator's attempt to tune a period
     * would switch the job off instead.
     */
    private JobConfig findConfig(JobType jobType) {
        JobConfig exact = jobs.get(toKey(jobType));
        if (exact != null) {
            return exact;
        }

        String normalized = normalize(jobType.name());
        return jobs.entrySet().stream()
                .filter(entry -> normalize(entry.getKey()).equals(normalized))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }

    /** Reduces a key to letters and digits, so dashes, underscores and casing stop mattering. */
    private static String normalize(String key) {
        return NON_ALPHANUMERIC.matcher(key).replaceAll("").toLowerCase(Locale.ROOT);
    }

    public static String toKey(JobType jobType) {
        return jobType.name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    public static class JobConfig {

        private static final Map<Character, Long> SIZE_MULTIPLIERS = Map.of(
                'K', 1024L,
                'M', 1024L * 1024L,
                'G', 1024L * 1024L * 1024L,
                'T', 1024L * 1024L * 1024L * 1024L);
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
         * of falling back to a value hidden in code. Accepts the same notation Spring binds
         * the job's {@code period} with: {@code 500ms}, {@code 10s}, {@code 5m}, {@code 1h},
         * {@code 7d}, or ISO-8601.
         */
        public Duration durationParam(String name) {
            String value = requiredParam(name);
            try {
                return DurationStyle.detectAndParse(value.trim());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(
                        "Scheduler job param is not a valid duration: param=" + name + " value=" + value, e);
            }
        }

        /**
         * Resolves a required byte-size param written either as a plain number of bytes
         * ({@code 1048576}) or with a binary unit suffix ({@code 512K}, {@code 100M},
         * {@code 20G}, {@code 2T}); binary units throughout, since that is how disk budgets
         * are reasoned about operationally. Must be positive.
         */
        public long bytesParam(String name) {
            String value = requiredParam(name).trim().toUpperCase(Locale.ROOT);
            char suffix = value.charAt(value.length() - 1);
            Long multiplier = SIZE_MULTIPLIERS.get(suffix);
            long bytes;
            try {
                bytes = multiplier == null
                        ? Long.parseLong(value)
                        : Long.parseLong(value.substring(0, value.length() - 1).trim()) * multiplier;
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(
                        "Scheduler job param is not a valid size: param=" + name + " value=" + value, e);
            }
            if (bytes <= 0) {
                throw new IllegalArgumentException(
                        "Scheduler job param must be positive: param=" + name + " value=" + value);
            }
            return bytes;
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

    }
}
