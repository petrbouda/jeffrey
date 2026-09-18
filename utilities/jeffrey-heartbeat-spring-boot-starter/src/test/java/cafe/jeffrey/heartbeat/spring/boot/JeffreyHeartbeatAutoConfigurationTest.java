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

package cafe.jeffrey.heartbeat.spring.boot;

import cafe.jeffrey.heartbeat.HeartbeatFiles;
import cafe.jeffrey.heartbeat.JeffreyHeartbeat;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.nio.file.Files;
import java.nio.file.Path;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

class JeffreyHeartbeatAutoConfigurationTest {

    private static final String JEFFREY_ENABLED = JeffreyHeartbeatAutoConfiguration.JEFFREY_ENABLED_PROPERTY;

    /** A jeffrey-jib pod that opted in: JEFFREY_ENABLED=true, which Spring binds to jeffrey.enabled. */
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(JeffreyHeartbeatAutoConfiguration.class))
            .withPropertyValues(JEFFREY_ENABLED + "=true");

    /** The same jar in a container that never set the master switch. */
    private final ApplicationContextRunner runnerWithoutSwitch = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(JeffreyHeartbeatAutoConfiguration.class));

    @Nested
    class Wiring {

        @Test
        void beatsIntoTheConfiguredDirectory(@TempDir Path tempDir) {
            Path directory = tempDir.resolve(HeartbeatFiles.DIRECTORY);

            runner.withPropertyValues(
                            "jeffrey.heartbeat.dir=" + directory,
                            "jeffrey.heartbeat.interval=20ms")
                    .run(context -> {
                        assertTrue(context.getBean(JeffreyHeartbeat.class).running());
                        await().atMost(5, SECONDS).until(() ->
                                Files.exists(directory.resolve(HeartbeatFiles.HEARTBEAT_FILE)));
                    });
        }

        @Test
        void closingTheContextWritesTheCleanExitMarker(@TempDir Path tempDir) {
            Path directory = tempDir.resolve(HeartbeatFiles.DIRECTORY);

            runner.withPropertyValues("jeffrey.heartbeat.dir=" + directory)
                    .run(context -> {
                        // The context is closed when the callback returns, and Spring calls
                        // close() on a bean that is AutoCloseable — that is what writes the marker
                        assertTrue(context.getBean(JeffreyHeartbeat.class).running());
                    });

            assertTrue(Files.exists(directory.resolve(HeartbeatFiles.FINISHED_FILE)),
                    "a context shutdown is a clean exit and must finish the session at once");
        }

        @Test
        void backsOffWhenTheApplicationDeclaresItsOwn(@TempDir Path tempDir) {
            runner.withPropertyValues("jeffrey.heartbeat.dir=" + tempDir)
                    .withBean(JeffreyHeartbeat.class, JeffreyHeartbeat::startFromEnvironment)
                    .run(context -> assertEquals(1, context.getBeanNamesForType(JeffreyHeartbeat.class).length));
        }
    }

    @Nested
    class SwitchedOff {

        @Test
        void contributesNothingWhenDisabled(@TempDir Path tempDir) {
            // What the Provisioner exports for a deployment that opted out of liveness reporting:
            // the library must not write a heartbeat the hub was told not to expect
            runner.withPropertyValues(
                            "jeffrey.heartbeat.enabled=false",
                            "jeffrey.heartbeat.dir=" + tempDir)
                    .run(context -> assertFalse(context.containsBean("jeffreyHeartbeat")));
        }

        @Test
        void contributesNothingWithoutTheMasterSwitch(@TempDir Path tempDir) {
            // JEFFREY_ENABLED unset: no bean at all, even with a directory to write to
            runnerWithoutSwitch.withPropertyValues("jeffrey.heartbeat.dir=" + tempDir)
                    .run(context -> assertFalse(context.containsBean("jeffreyHeartbeat")));
        }

        @Test
        void contributesNothingWhenTheMasterSwitchIsOff(@TempDir Path tempDir) {
            // JEFFREY_ENABLED=false: the jeffrey-jib entrypoint bypassed the Provisioner, and the
            // starter must not report for a session that was never created
            runnerWithoutSwitch.withPropertyValues(
                            JEFFREY_ENABLED + "=false",
                            "jeffrey.heartbeat.dir=" + tempDir)
                    .run(context -> assertFalse(context.containsBean("jeffreyHeartbeat")));
        }

        @Test
        void startsInertWhenNothingIsProvisioned() {
            // The same jar on a developer's laptop: no session, no directory, no failure
            runner.run(context -> {
                JeffreyHeartbeat heartbeat = context.getBean(JeffreyHeartbeat.class);
                assertFalse(heartbeat.running(), "an unprovisioned application reports nothing and still starts");
            });
        }
    }
}
