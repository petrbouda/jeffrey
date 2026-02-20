/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
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

package pbouda.jeffrey.shared.folderqueue;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Pluggable readiness check for folder queue entries. Parses raw file content
 * into a typed value. Return {@link Optional#empty()} if the file is not ready
 * for processing (e.g., partially written, invalid format) — the consumer will
 * skip it and retry on the next poll tick.
 *
 * @param <T> the type of the parsed result
 */
@FunctionalInterface
public interface FolderQueueEntryParser<T> {

    /**
     * Parses the file content and returns the result.
     *
     * @param filePath the path of the file being parsed
     * @param content  the raw string content of the file
     * @return the parsed value, or {@link Optional#empty()} if the file is not ready
     */
    Optional<T> parse(Path filePath, String content);
}
