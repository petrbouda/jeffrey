/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package cafe.jeffrey.profile.manager.additional;

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
