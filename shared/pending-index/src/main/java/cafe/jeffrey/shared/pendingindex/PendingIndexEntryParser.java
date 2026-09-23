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

package cafe.jeffrey.shared.pendingindex;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Pluggable readiness check for index entries. Parses raw file content into a typed pointer.
 * Return {@link Optional#empty()} when the file is not ready (partially written, malformed) —
 * the reader skips it and retries on the next tick rather than consuming it.
 *
 * @param <T> the type of the parsed pointer
 */
@FunctionalInterface
public interface PendingIndexEntryParser<T> {

    /**
     * Parses the file content and returns the result.
     *
     * @param filePath the path of the file being parsed
     * @param content  the raw string content of the file
     * @return the parsed pointer, or {@link Optional#empty()} if the file is not ready
     */
    Optional<T> parse(Path filePath, String content);
}
