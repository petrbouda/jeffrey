/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package cafe.jeffrey.profile.manager.model.thread;

/**
 * Different thread counts for gauge visualization on UI.
 *
 * @param accumulated       the total number of threads created since the JVM started
 * @param peak              the peak number of threads created since the JVM started
 * @param sleepCount        the number of times threads have been put to sleep
 * @param parkCount         the number of times threads have been parked
 * @param monitorBlockCount the number of times threads have blocked on a monitor
 */
public record ThreadStats(
        long accumulated,
        long peak,
        long sleepCount,
        long parkCount,
        long monitorBlockCount) {
}
