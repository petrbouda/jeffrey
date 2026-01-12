/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package pbouda.jeffrey.profile.manager.additional;

import java.nio.file.Path;

/**
 * Result of processing an additional file.
 * Implementations indicate whether the result should be cached or represents a file transfer.
 */
public sealed interface ProcessingResult {

    /**
     * Indicates that a file was processed and produces a cacheable object.
     *
     * @param cacheKey the key to use for caching
     * @param content  the content to cache
     */
    record CacheableResult(String cacheKey, Object content) implements ProcessingResult {
    }

    /**
     * Indicates that a file was transferred to a destination.
     *
     * @param destinationPath the path where the file was copied
     */
    record FileTransferResult(Path destinationPath) implements ProcessingResult {
    }

    /**
     * Indicates no action was taken (file skipped or not applicable).
     */
    record NoOpResult() implements ProcessingResult {
    }
}
