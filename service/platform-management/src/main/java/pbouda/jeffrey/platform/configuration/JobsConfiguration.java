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

package pbouda.jeffrey.platform.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import pbouda.jeffrey.platform.appinitializer.JfrEventListenerInitializer;
import pbouda.jeffrey.platform.appinitializer.SchedulerInitializer;
import pbouda.jeffrey.platform.scheduler.Scheduler;

import java.time.Duration;
import java.util.List;

/**
 * Core scheduler infrastructure configuration.
 * Defines the schedulers and initializers for job execution.
 * Job beans are defined in separate configuration classes:
 * - {@link GlobalJobsConfiguration} for GLOBAL/Workspace-level jobs
 * - {@link ProjectJobsConfiguration} for PROJECT-level jobs
 */
@Configuration
@Import({ProfileFactoriesConfiguration.class, GlobalJobsConfiguration.class, ProjectJobsConfiguration.class})
public class JobsConfiguration {

    @Bean
    @ConditionalOnProperty(name = "jeffrey.job.scheduler.enabled", havingValue = "true", matchIfMissing = true)
    public SchedulerInitializer schedulerInitializer(List<Scheduler> schedulers) {
        return new SchedulerInitializer(schedulers);
    }

    @Bean
    @ConditionalOnProperty(name = "jeffrey.logging.jfr-events.application.enabled", havingValue = "true")
    public JfrEventListenerInitializer jfrEventListenerInitializer(@Value("${jeffrey.logging.jfr-events.application.threshold:}") Duration threshold) {
        Duration resolvedThreshold = threshold == null || threshold.isNegative() ? Duration.ZERO : threshold;
        return new JfrEventListenerInitializer(resolvedThreshold);
    }
}
