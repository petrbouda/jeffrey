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

package cafe.jeffrey.microscope.model.repository;

import java.util.List;
import java.util.Objects;

/**
 * How many files a session holds and the bytes they occupy. A listing carries this instead of
 * the files themselves, so a list of sessions never ships every file of every session; only a
 * single-session read carries the files, and then this is measured from them.
 *
 * @param bytes files whose size could not be determined count as zero
 */
public record SessionSize(int fileCount, long bytes) {

    public SessionSize {
        if (fileCount < 0 || bytes < 0) {
            throw new IllegalArgumentException("A session size cannot be negative: fileCount=" + fileCount + " bytes=" + bytes);
        }
    }

    public static SessionSize of(List<RepositoryFile> files) {
        long bytes = files.stream()
                .map(RepositoryFile::size)
                .filter(Objects::nonNull)
                .mapToLong(Long::longValue)
                .sum();
        return new SessionSize(files.size(), bytes);
    }
}
