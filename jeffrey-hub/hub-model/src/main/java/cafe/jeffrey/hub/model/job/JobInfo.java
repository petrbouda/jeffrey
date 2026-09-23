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

package cafe.jeffrey.hub.model.job;

import java.time.Duration;
import java.util.Map;

/**
 * Read-only view of a configured scheduler job, resolved from
 * {@code application.properties} at startup. There is exactly one
 * {@code JobInfo} per {@link JobType}.
 *
 * @param manualTriggerSupported whether an operator may run this job on demand. A capability of
 *                               the job itself, not a setting — the UI renders a control from
 *                               this flag alone and so never needs to know which jobs they are.
 */
public record JobInfo(
        JobType jobType,
        JobType.ExecutionLevel executionLevel,
        Duration period,
        Map<String, String> params,
        boolean enabled,
        boolean manualTriggerSupported) {
}
