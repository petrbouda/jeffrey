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

package cafe.jeffrey.server.core.configuration.properties;

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
 * on {@code ServerAppConfiguration}); {@code application.properties} overrides
 * any field by redeclaring the same key.
 * <p>
 * Property keys use the lower-kebab-case form of {@link JobType} (for example
 * {@code projects-synchronizer} for {@code PROJECTS_SYNCHRONIZER}).
 */
@ConfigurationProperties("jeffrey.server.scheduler")
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
    }
}
