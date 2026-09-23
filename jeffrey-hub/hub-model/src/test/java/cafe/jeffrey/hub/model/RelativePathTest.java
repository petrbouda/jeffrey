/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
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
package cafe.jeffrey.hub.model;

import cafe.jeffrey.shared.common.model.RepositoryType;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RelativePathTest {

    @Test
    void acceptsAPathThatStaysInsideItsDirectory() {
        assertEquals("instance/session", RelativePath.require("instance/session", "path"));
        assertEquals(Path.of("a", "b"), RelativePath.require(Path.of("a", "b"), "path"));
    }

    @Test
    void refusesAnAbsolutePath() {
        assertThrows(IllegalArgumentException.class, () -> RelativePath.require("/etc", "path"));
    }

    @Test
    void refusesAPathThatClimbsOut() {
        assertThrows(IllegalArgumentException.class, () -> RelativePath.require("../../other", "path"));
        assertThrows(IllegalArgumentException.class, () -> RelativePath.require("a/../../b", "path"));
    }

    @Test
    void refusesABlankOrNullPath() {
        assertThrows(IllegalArgumentException.class, () -> RelativePath.require(" ", "path"));
        assertThrows(IllegalArgumentException.class, () -> RelativePath.require((Path) null, "path"));
    }

    /** The two records that hold such a path are the ones a marker file reaches. */
    @Test
    void theRecordsOffTheVolumeAreHeldToIt() {
        assertThrows(IllegalArgumentException.class, () -> new RepositoryInfo(
                "repo", RepositoryType.ASYNC_PROFILER, null, "ws", "../escape"));
        assertThrows(IllegalArgumentException.class, () -> new RepositoryInfo(
                null, RepositoryType.ASYNC_PROFILER, null, "ws", "project"));
    }
}
