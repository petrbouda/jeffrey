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

package cafe.jeffrey.provider.profile.jdbc;

import cafe.jeffrey.microscope.model.FrameResolutionMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertSame;

class DuckDBProfilePersistenceProviderTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-03-01T00:00:00Z"), ZoneOffset.UTC);

    @TempDir
    Path profilesDir;

    /**
     * Every caller has to get the same repositories, because they hold the one frames cache a frame
     * edit (rename, collapse) invalidates. With an instance per caller, the edit cleared only the
     * editor's cache and the flamegraph kept serving the frames from before it.
     */
    @Test
    void handsEveryCallerTheSameRepositoriesSoTheyShareOneFramesCache() {
        DuckDBProfilePersistenceProvider provider =
                new DuckDBProfilePersistenceProvider(profilesDir, FrameResolutionMode.CACHE, CLOCK);

        assertSame(provider.repositories(), provider.repositories());
    }
}
