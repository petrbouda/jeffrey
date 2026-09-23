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

import cafe.jeffrey.storage.recording.api.file.ManagedFile;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Processor for additional files found in recording storage.
 * Different implementations handle different file types (e.g., performance counters, heap dumps).
 */
public interface AdditionalFileProcessor {

    /**
     * Processes the given file and returns the result of processing.
     * The result type indicates how the processing outcome should be handled:
     * <ul>
     *   <li>{@link ProcessingResult.CacheableResult} - content should be stored in the cache repository</li>
     *   <li>{@link ProcessingResult.FileTransferResult} - file was copied to destination</li>
     *   <li>{@link ProcessingResult.NoOpResult} - no action taken</li>
     * </ul>
     *
     * @param filePath the path to the file to be processed
     * @return the processing result, or empty if processing failed
     */
    Optional<ProcessingResult> process(Path filePath);

    /**
     * The file type this processor handles.
     */
    ManagedFile managedFile();
}
