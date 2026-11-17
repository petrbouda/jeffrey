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

package cafe.jeffrey.volumescheduler.config;

import cafe.jeffrey.volumescheduler.retention.RetentionPolicy;
import cafe.jeffrey.volumescheduler.scheduler.JobSchedule;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * How the volume scheduler's configuration binds: one required path, every other value a default,
 * and each environment variable spelled the plain Spring way.
 */
class VolumeSchedulerPropertiesTest {

    private static final long GIB = 1024L * 1024L * 1024L;

    /** Spring maps variable names onto properties only for a source named as the system environment. */
    private static final String TEST_ENVIRONMENT = "test-" + StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME;

    @TempDir
    Path volume;

    @Configuration
    @EnableConfigurationProperties(VolumeSchedulerProperties.class)
    static class PropertiesOnly {
    }

    private final ApplicationContextRunner properties = new ApplicationContextRunner()
            .withUserConfiguration(PropertiesOnly.class);

    private final ApplicationContextRunner application = new ApplicationContextRunner()
            .withUserConfiguration(VolumeSchedulerConfiguration.class);

    @Nested
    class Defaults {

        @Test
        void everyValueButThePathHasOne() {
            properties.withPropertyValues("jeffrey.volume-scheduler.volume.path=" + volume)
                    .run(context -> {
                        VolumeSchedulerProperties bound = context.getBean(VolumeSchedulerProperties.class);
                        assertThat(bound.volume().path()).isEqualTo(volume.toAbsolutePath().normalize());
                        assertThat(bound.volume().workspaces()).isEqualTo(volume.resolve("workspaces"));
                        assertThat(bound.retention().schedule()).isEqualTo(new JobSchedule(true, Duration.ofMinutes(5)));
                        assertThat(bound.retention().policy()).isEqualTo(new RetentionPolicy(
                                Duration.ofDays(7), 10, Duration.ofDays(7), 20 * GIB, Duration.ofDays(14)));
                        assertThat(bound.compression().schedule()).isEqualTo(new JobSchedule(true, Duration.ofMinutes(1)));
                    });
        }
    }

    @Nested
    class Overrides {

        @Test
        void bindTheKebabCaseKeys() {
            properties.withPropertyValues(
                            "jeffrey.volume-scheduler.volume.path=" + volume,
                            "jeffrey.volume-scheduler.retention.max-size=50GB",
                            "jeffrey.volume-scheduler.retention.newest-session-file-retention=2d",
                            "jeffrey.volume-scheduler.compression.enabled=false")
                    .run(context -> {
                        VolumeSchedulerProperties bound = context.getBean(VolumeSchedulerProperties.class);
                        assertThat(bound.retention().policy().maxProjectBytes()).isEqualTo(50 * GIB);
                        assertThat(bound.retention().policy().newestSessionFileRetention()).isEqualTo(Duration.ofDays(2));
                        assertThat(bound.compression().schedule().enabled()).isFalse();
                    });
        }

        /** An environment variable cannot carry a dash, and Spring's own binding takes it as it is. */
        @Test
        void bindTheEnvironmentVariableSpellings() {
            properties.withInitializer(context -> context.getEnvironment().getPropertySources().addFirst(
                            new SystemEnvironmentPropertySource(TEST_ENVIRONMENT, Map.of(
                                    "JEFFREY_VOLUMESCHEDULER_VOLUME_PATH", volume.toString(),
                                    "JEFFREY_VOLUMESCHEDULER_RETENTION_MAXSESSIONS", "3",
                                    "JEFFREY_VOLUMESCHEDULER_COMPRESSION_PERIOD", "30s"))))
                    .run(context -> {
                        VolumeSchedulerProperties bound = context.getBean(VolumeSchedulerProperties.class);
                        assertThat(bound.volume().path()).isEqualTo(volume.toAbsolutePath().normalize());
                        assertThat(bound.retention().policy().maxSessions()).isEqualTo(3);
                        assertThat(bound.compression().schedule().period()).isEqualTo(Duration.ofSeconds(30));
                    });
        }
    }

    @Nested
    class Refusals {

        @Test
        void withoutAVolumePathTheServiceDoesNotStart() {
            application.run(context -> assertThat(context).hasFailed()
                    .getFailure().rootCause().hasMessageContaining("jeffrey.volume-scheduler.volume.path is required"));
        }

        @Test
        void aVolumePathThatIsNotADirectoryIsNoMountedVolume() {
            application.withPropertyValues("jeffrey.volume-scheduler.volume.path=" + volume.resolve("not-mounted"))
                    .run(context -> assertThat(context).hasFailed()
                            .getFailure().rootCause().hasMessageContaining("is it mounted?"));
        }

        @Test
        void aNonPositiveSessionCapIsRefusedAtStartup() {
            application.withPropertyValues(
                            "jeffrey.volume-scheduler.volume.path=" + volume,
                            "jeffrey.volume-scheduler.retention.max-sessions=0")
                    .run(context -> assertThat(context).hasFailed()
                            .getFailure().rootCause().hasMessageContaining("max-sessions must be positive"));
        }

        @Test
        void aZeroPeriodIsRefusedAtStartup() {
            application.withPropertyValues(
                            "jeffrey.volume-scheduler.volume.path=" + volume,
                            "jeffrey.volume-scheduler.compression.period=0s")
                    .run(context -> assertThat(context).hasFailed()
                            .getFailure().rootCause().hasMessageContaining("period must be positive"));
        }

        /** Its {@code workspaces/} may be missing: the provisioner creates it with the first session. */
        @Test
        void aVolumeWithoutWorkspacesYetStarts() {
            application.withPropertyValues("jeffrey.volume-scheduler.volume.path=" + volume)
                    .run(context -> assertThat(context).hasNotFailed());
        }
    }
}
