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

package cafe.jeffrey.microscope.connector.local;

import cafe.jeffrey.microscope.model.connector.LocalDirectoryAddress;

/**
 * Opens the volume a {@link LocalDirectoryAddress} names. Nothing is checked here: the directory is
 * looked at on every call, so one that is missing now — not created yet, or removed — reads as a
 * volume that cannot be reached, and one that appears later is read from then on.
 */
public final class LocalVolumes {

    private LocalVolumes() {
    }

    public static ContainedFilesystemVolume open(LocalDirectoryAddress address) {
        return new ContainedFilesystemVolume(address.root());
    }
}
