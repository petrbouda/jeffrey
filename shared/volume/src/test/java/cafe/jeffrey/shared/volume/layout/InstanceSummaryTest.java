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

package cafe.jeffrey.shared.volume.layout;

import cafe.jeffrey.shared.volume.VolumePath;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;

class InstanceSummaryTest {

    private static final Instant T0 = Instant.parse("2026-02-20T12:00:00Z");
    private static final VolumePath INSTANCE = VolumePath.root().resolve("project").resolve("pod-a");
    private static final InstanceDirectory DIRECTORY = new InstanceDirectory(INSTANCE, Optional.of(T0));

    private static VolumeSession newest() {
        return new VolumeSession(
                new SessionDirectory(INSTANCE.resolve("s-1"), T0), true,
                new SessionListing.Listed(List.of()));
    }

    @Test
    void hasANewestSessionExactlyWhenItHasSessions() {
        assertThrows(IllegalArgumentException.class, () -> new InstanceSummary(DIRECTORY, 0, Optional.of(newest())));
        assertThrows(IllegalArgumentException.class, () -> new InstanceSummary(DIRECTORY, 2, Optional.empty()));
    }
}
