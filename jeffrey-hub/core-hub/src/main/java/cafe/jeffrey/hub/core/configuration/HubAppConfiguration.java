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

package cafe.jeffrey.hub.core.configuration;

import cafe.jeffrey.hub.core.HubJeffreyDirs;
import cafe.jeffrey.hub.core.configuration.properties.DefaultWorkspaceProperties;
import cafe.jeffrey.hub.core.configuration.properties.LivenessProperties;
import cafe.jeffrey.hub.core.configuration.properties.SchedulerJobsProperties;
import cafe.jeffrey.hub.core.configuration.properties.WorkspacesProperties;
import cafe.jeffrey.hub.core.volume.FileHeartbeatReader;
import cafe.jeffrey.hub.core.volume.SessionLiveness;
import cafe.jeffrey.shared.ui.version.VersionFeatureConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.PropertySource;

import java.nio.file.Path;
import java.time.Clock;

/**
 * Configuration beans of the hub: its home directory, its clock, and how a session's liveness is
 * read. The volume under the home directory is the hub's only state.
 */
@Configuration
@Import({
        SchedulerConfiguration.class,
        HeartbeatConfiguration.class,
        VersionFeatureConfiguration.class
})
@EnableConfigurationProperties({
        SchedulerJobsProperties.class,
        DefaultWorkspaceProperties.class,
        WorkspacesProperties.class,
        LivenessProperties.class
})
@PropertySource("classpath:scheduler-defaults.properties")
public class HubAppConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(HubAppConfiguration.class);

    @Bean
    public Clock applicationClock() {
        return Clock.systemUTC();
    }

    @Bean
    public HubJeffreyDirs jeffreyDir(@Value("${jeffrey.hub.home.dir:${user.home}/.jeffrey-hub}") String homeDir) {
        HubJeffreyDirs jeffreyDirs = new HubJeffreyDirs(Path.of(homeDir));
        jeffreyDirs.initialize();
        LOG.info("Using Jeffrey directory: HOME={}", jeffreyDirs.homeDir());
        return jeffreyDirs;
    }

    @Bean
    public SessionLiveness sessionLiveness(Clock applicationClock, LivenessProperties livenessProperties) {
        return new SessionLiveness(applicationClock, new FileHeartbeatReader(), livenessProperties.deadlines());
    }
}
