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

package cafe.jeffrey.microscope.core.manager.download;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DownloadTaskRegistryTest {

    private static final Instant FINISHED_AT = Instant.parse("2026-03-01T10:00:00Z");
    private static final Clock TASK_CLOCK = Clock.fixed(FINISHED_AT, ZoneOffset.UTC);
    private static final String SESSION_ID = "session-1";
    private static final List<String> FILE_IDS = List.of("rec-1");

    private DownloadTaskRegistry registry;

    @AfterEach
    void closeRegistry() {
        registry.close();
    }

    private DownloadTaskRegistry registryAt(Duration afterFinish) {
        registry = new DownloadTaskRegistry(Clock.fixed(FINISHED_AT.plus(afterFinish), ZoneOffset.UTC));
        return registry;
    }

    private static DownloadTask task() {
        return new DownloadTask(SESSION_ID, FILE_IDS, TASK_CLOCK);
    }

    @Nested
    class Cleanup {

        @Test
        void dropsATaskFinishedLongerAgoThanItsTimeToLive() {
            DownloadTaskRegistry registry = registryAt(Duration.ofMinutes(6));
            DownloadTask task = task();
            registry.register(task);
            task.onComplete();

            registry.cleanupCompletedTasks();

            assertFalse(registry.getTask(task.getTaskId()).isPresent());
        }

        @Test
        void keepsATaskThatFinishedRecently() {
            DownloadTaskRegistry registry = registryAt(Duration.ofMinutes(4));
            DownloadTask task = task();
            registry.register(task);
            task.onError("share unreachable");

            registry.cleanupCompletedTasks();

            assertTrue(registry.getTask(task.getTaskId()).isPresent());
        }

        @Test
        void keepsATaskStillRunningHoweverOldItIs() {
            DownloadTaskRegistry registry = registryAt(Duration.ofHours(1));
            DownloadTask task = task();
            registry.register(task);
            task.onStart(1, 100);

            registry.cleanupCompletedTasks();

            assertTrue(registry.getTask(task.getTaskId()).isPresent());
        }
    }
}
