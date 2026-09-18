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

package cafe.jeffrey.hub.core.configuration;

import cafe.jeffrey.heartbeat.JeffreyHeartbeat;
import cafe.jeffrey.shared.common.HeartbeatConstants;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * The library resolves its directory from system properties, the channel the Provisioner's argfile
 * delivers, so the provisioned cases set the real property and clear it afterwards. The enabling
 * property goes through Spring's environment, which is what the condition reads.
 */
class HeartbeatConfigurationTest {

    private static final String ENABLED_PROPERTY = HeartbeatConstants.ENABLED_PROPERTY;
    private static final String DIRECTORY_PROPERTY = HeartbeatConstants.DIRECTORY_PROPERTY;
    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(ClockConfiguration.class, HeartbeatConfiguration.class);

    @Configuration
    static class ClockConfiguration {

        @Bean
        Clock applicationClock() {
            return FIXED_CLOCK;
        }
    }

    @AfterEach
    void clearDirectoryProperty() {
        System.clearProperty(DIRECTORY_PROPERTY);
    }

    @Nested
    class WithoutProvisioner {

        @Test
        void noPropertyMeansNoBean() {
            runner.run(context -> assertThat(context).doesNotHaveBean(JeffreyHeartbeat.class));
        }
    }

    @Nested
    class WhenProvisioned {

        @TempDir
        Path sessionDir;

        @Test
        void beatsIntoTheDirectoryTheProvisionerNamedAndFinishesOnShutdown() {
            Path heartbeatDir = sessionDir.resolve(HeartbeatConstants.HEARTBEAT_DIR);
            System.setProperty(DIRECTORY_PROPERTY, heartbeatDir.toString());

            runner.withPropertyValues(ENABLED_PROPERTY + "=true")
                    .run(context -> {
                        assertThat(context).hasSingleBean(JeffreyHeartbeat.class);
                        assertThat(context.getBean(JeffreyHeartbeat.class).running()).isTrue();
                        await().untilAsserted(() -> assertThat(
                                Files.exists(heartbeatDir.resolve(HeartbeatConstants.HEARTBEAT_FILE))).isTrue());
                    });

            assertThat(Files.exists(heartbeatDir.resolve(HeartbeatConstants.FINISHED_FILE)))
                    .as("closing the context writes the clean-exit marker")
                    .isTrue();
        }

        @Test
        void sessionThatDeclaredNoLivenessGetsNoBean() {
            Path heartbeatDir = sessionDir.resolve(HeartbeatConstants.HEARTBEAT_DIR);
            System.setProperty(DIRECTORY_PROPERTY, heartbeatDir.toString());

            runner.withPropertyValues(ENABLED_PROPERTY + "=false")
                    .run(context -> {
                        assertThat(context).doesNotHaveBean(JeffreyHeartbeat.class);
                        assertThat(Files.exists(heartbeatDir)).isFalse();
                    });
        }
    }
}
