/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
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
