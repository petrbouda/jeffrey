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

package cafe.jeffrey.shared.volume;

import java.io.UncheckedIOException;

/**
 * A volume its holder may change: the one change the layout makes, a subtree removed. Nothing
 * creates a directory or writes a file's content here: the provisioner lays the directories down
 * and the profilers write the recordings. Reading stays {@link VolumeAccess}'s, so a reader that
 * must never write is handed only that.
 *
 * <p>Deleting what is gone is not an error: two writers, or a retried call, meet the volume in
 * either state.
 */
public interface WritableVolume extends VolumeAccess {

    /**
     * Removes a file, or a directory with everything under it; nothing there is no error.
     *
     * @throws UncheckedIOException when something there cannot be removed
     */
    void delete(VolumePath path);
}
