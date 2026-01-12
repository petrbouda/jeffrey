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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.shared.common.model.repository.SupportedRecordingFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;

/**
 * Processor that copies heap dump files from recording storage to the profile's heap-dump-analysis folder.
 */
public class HeapDumpAdditionalFileProcessor implements AdditionalFileProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(HeapDumpAdditionalFileProcessor.class);

    private final Path heapDumpAnalysisPath;
    private final SupportedRecordingFile supportedType;

    /**
     * Creates a processor for the specified heap dump type.
     *
     * @param heapDumpAnalysisPath destination folder for heap dumps
     * @param supportedType        either HEAP_DUMP or HEAP_DUMP_GZ
     */
    public HeapDumpAdditionalFileProcessor(Path heapDumpAnalysisPath, SupportedRecordingFile supportedType) {
        this.heapDumpAnalysisPath = heapDumpAnalysisPath;
        this.supportedType = supportedType;

        if (supportedType != SupportedRecordingFile.HEAP_DUMP &&
                supportedType != SupportedRecordingFile.HEAP_DUMP_GZ) {
            throw new IllegalArgumentException("Unsupported type: " + supportedType +
                    ". Only HEAP_DUMP and HEAP_DUMP_GZ are supported.");
        }
    }

    @Override
    public Optional<ProcessingResult> process(Path filePath) {
        try {
            // Create the destination directory if it doesn't exist
            Files.createDirectories(heapDumpAnalysisPath);

            // Copy heap dump to the analysis folder, preserving the original filename
            Path destinationPath = heapDumpAnalysisPath.resolve(filePath.getFileName());
            Files.copy(filePath, destinationPath, StandardCopyOption.REPLACE_EXISTING);

            LOG.info("Heap dump copied to profile: source={} destination={}", filePath, destinationPath);

            return Optional.of(new ProcessingResult.FileTransferResult(destinationPath));
        } catch (IOException e) {
            LOG.error("Failed to copy heap dump: source={} destination={}",
                    filePath, heapDumpAnalysisPath, e);
            return Optional.empty();
        }
    }

    @Override
    public SupportedRecordingFile supportedRecordingFile() {
        return supportedType;
    }
}
