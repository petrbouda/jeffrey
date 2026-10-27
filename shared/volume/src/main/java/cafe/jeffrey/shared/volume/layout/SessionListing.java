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

import cafe.jeffrey.shared.volume.VolumeEntry;

import java.io.UncheckedIOException;
import java.util.List;

/**
 * One listing of a session directory, which answers everything a reader asks of the directory
 * itself: its files, and whether its marker is still there.
 * On a file share each question asked of the volume is a round-trip, so a session is listed once
 * and every answer is read off that one listing.
 *
 * <p>A listing that failed answers none of it: each question falls back to asking the volume, and
 * the files are the failure itself, as listing them would have been.
 */
public sealed interface SessionListing {

    /**
     * What lies directly in the session directory.
     *
     * @throws UncheckedIOException the failure, when the directory could not be listed
     */
    List<VolumeEntry> entries();

    /**
     * Whether a file by this name was listed. A listing that failed cannot say, and answers that it
     * does: the session is then read as it lies rather than dropped for a failure of the share.
     */
    boolean holdsFile(String name);

    /** The session directory as it was listed; empty when nothing is there. */
    record Listed(List<VolumeEntry> entries) implements SessionListing {

        public Listed {
            if (entries == null) {
                throw new IllegalArgumentException("A session listing needs its entries");
            }
            entries = List.copyOf(entries);
        }

        @Override
        public boolean holdsFile(String name) {
            return entries.stream().anyMatch(entry -> entry.isFile() && entry.name().equals(name));
        }
    }

    /** The session directory is there and could not be listed. */
    record Unlisted(UncheckedIOException failure) implements SessionListing {

        public Unlisted {
            if (failure == null) {
                throw new IllegalArgumentException("A failed session listing needs its failure");
            }
        }

        @Override
        public List<VolumeEntry> entries() {
            throw failure;
        }

        @Override
        public boolean holdsFile(String name) {
            return true;
        }
    }
}
