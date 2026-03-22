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

import pbouda.jeffrey.shared.common.model.repository.SupportedRecordingFile;

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
     * Returns the supported recording file type for this processor.
     *
     * @return the supported recording file type
     */
    SupportedRecordingFile supportedRecordingFile();
}
