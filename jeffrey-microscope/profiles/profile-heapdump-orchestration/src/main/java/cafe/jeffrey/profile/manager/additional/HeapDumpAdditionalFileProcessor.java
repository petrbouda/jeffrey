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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.storage.recording.api.file.ManagedFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;

/**
 * Processor that copies heap dump files from recording storage to the profile's heap-dump folder.
 */
public class HeapDumpAdditionalFileProcessor implements AdditionalFileProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(HeapDumpAdditionalFileProcessor.class);

    private final Path heapDumpAnalysisPath;
    private final ManagedFile supportedType;

    /**
     * Creates a processor for the specified heap dump type.
     *
     * @param heapDumpAnalysisPath destination folder for heap dumps
     * @param supportedType        either HEAP_DUMP or HEAP_DUMP_GZ
     */
    public HeapDumpAdditionalFileProcessor(Path heapDumpAnalysisPath, ManagedFile supportedType) {
        this.heapDumpAnalysisPath = heapDumpAnalysisPath;
        this.supportedType = supportedType;

        if (supportedType != ManagedFile.HEAP_DUMP &&
                supportedType != ManagedFile.HEAP_DUMP_GZ) {
            throw new IllegalArgumentException("Unsupported type: " + supportedType +
                    ". Only HEAP_DUMP and HEAP_DUMP_GZ are supported.");
        }
    }

    @Override
    public Optional<ProcessingResult> process(Path filePath) {
        try {
            // Create the destination directory if it doesn't exist. Checked first: an unguarded
            // createDirectories on an existing directory throws and catches
            // FileAlreadyExistsException, which a recording captures as two throws.
            if (!Files.isDirectory(heapDumpAnalysisPath)) {
                Files.createDirectories(heapDumpAnalysisPath);
            }

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
    public ManagedFile managedFile() {
        return supportedType;
    }
}
