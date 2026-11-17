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

package cafe.jeffrey.volumescheduler.scheduler;

import java.time.Duration;
import java.time.Instant;

/**
 * What one run on demand did.
 *
 * @param startedAt when it was asked for; it may have waited for a scheduled run in flight first
 * @param took      how long the run itself took
 * @param summary   the job's own one line about what it did
 */
public record JobRun(JobType type, Instant startedAt, Duration took, String summary) {
}
