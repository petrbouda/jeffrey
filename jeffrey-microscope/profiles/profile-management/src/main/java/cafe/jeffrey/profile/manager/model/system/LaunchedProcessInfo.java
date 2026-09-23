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

package cafe.jeffrey.profile.manager.model.system;

/**
 * A subprocess launched by the JVM during the recording, from {@code jdk.ProcessStart}.
 *
 * @param timeOffsetMillis offset from recording start
 * @param pid              the started process id
 * @param command          the executed command
 * @param directory        the working directory
 * @param thread           the JVM thread that launched it
 */
public record LaunchedProcessInfo(
        long timeOffsetMillis,
        long pid,
        String command,
        String directory,
        String thread) {
}
