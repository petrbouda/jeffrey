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

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The volume scheduler's jobs; each visits every project of every workspace once per run.
 */
public enum JobType {
    RETENTION("retention"),
    COMPRESSION("compression");

    private static final Map<String, JobType> BY_ID = Arrays.stream(values())
            .collect(Collectors.toUnmodifiableMap(JobType::id, Function.identity()));

    private final String id;

    JobType(String id) {
        this.id = id;
    }

    /**
     * The job's one name outside the code: its properties group
     * ({@code jeffrey.volume-scheduler.retention.*}) and its REST path
     * ({@code /api/v1/jobs/retention}).
     */
    public String id() {
        return id;
    }

    /** The job an {@link #id()} names, or empty for any other text, the constant's own name included. */
    public static Optional<JobType> ofId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(BY_ID.get(id));
    }
}
