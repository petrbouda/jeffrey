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

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A job's key is its one name outside the code: the properties and the REST API both use it.
 */
class JobTypeTest {

    @Nested
    class Key {

        @Test
        void isTheConstantInLowerKebabCase() {
            assertEquals("project-retention", JobType.PROJECT_RETENTION.key());
            assertEquals("repository-jfr-compression", JobType.REPOSITORY_JFR_COMPRESSION.key());
        }

        @Test
        void namesItsJobBack() {
            for (JobType jobType : JobType.values()) {
                assertEquals(Optional.of(jobType), JobType.ofKey(jobType.key()));
            }
        }

        @Test
        void anyOtherTextNamesNoJob() {
            assertEquals(Optional.empty(), JobType.ofKey("PROJECT_RETENTION"));
            assertEquals(Optional.empty(), JobType.ofKey("project-cleanup"));
            assertEquals(Optional.empty(), JobType.ofKey(""));
        }
    }
}
