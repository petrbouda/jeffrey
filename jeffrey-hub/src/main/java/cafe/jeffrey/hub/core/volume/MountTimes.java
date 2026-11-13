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

package cafe.jeffrey.hub.core.volume;

import cafe.jeffrey.shared.volume.VolumePath;

import java.io.UncheckedIOException;
import java.nio.file.NoSuchFileException;
import java.time.Instant;
import java.util.Optional;

/**
 * When a directory on the hub's mount was created. The hub reads its own filesystem, whose
 * listings and looks always carry a time, so a time missing means the directory vanished while it
 * was read — unlike an Azure NFS share, which Microscope reads and which lists no times at all.
 */
public final class MountTimes {

    private MountTimes() {
    }

    public static Instant createdAt(Optional<Instant> listed, VolumePath directory) {
        return listed.orElseThrow(() -> new UncheckedIOException(
                "Directory vanished while it was read: path=" + directory,
                new NoSuchFileException(directory.toString())));
    }
}
