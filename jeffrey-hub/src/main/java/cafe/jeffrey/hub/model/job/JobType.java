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

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * The hub's scheduled jobs; each visits every project of every workspace once per tick.
 */
public enum JobType {
    PROJECT_RETENTION,
    REPOSITORY_JFR_COMPRESSION;

    /**
     * The job's one name outside the code: its constant in lower kebab case, as its properties
     * spell it ({@code jeffrey.hub.scheduler.jobs.project-retention.*}) and the REST API addresses
     * it ({@code /api/v1/jobs/project-retention}).
     */
    public String key() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    /** The job a {@link #key()} names, or empty for any other text, the constant's own name included. */
    public static Optional<JobType> ofKey(String key) {
        return Arrays.stream(values())
                .filter(jobType -> jobType.key().equals(key))
                .findFirst();
    }
}
