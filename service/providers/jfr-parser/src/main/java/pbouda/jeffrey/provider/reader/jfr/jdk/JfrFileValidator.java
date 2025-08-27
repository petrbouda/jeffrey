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

package pbouda.jeffrey.provider.reader.jfr.jdk;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;

import static java.nio.file.StandardOpenOption.READ;

/**
 * Validator for JFR files based on JDK specifications.
 * Provides comprehensive validation including magic bytes, file size, and basic structural integrity.
 */
public final class JfrFileValidator {

    private static final Logger LOG = LoggerFactory.getLogger(JfrFileValidator.class);
    
    // JFR format constants from JDK
    private static final byte[] FILE_MAGIC = { 'F', 'L', 'R', '\0' };
    private static final long MIN_JFR_FILE_SIZE = JeffreyChunkHeader.HEADER_SIZE; // 68 bytes
    private static final int MAJOR_VERSION_OFFSET = 4;
    private static final int MINOR_VERSION_OFFSET = 6;

    /**
     * Validation result containing success status and error details.
     */
    public static final class ValidationResult {
        private final boolean valid;
        private final String error;
        private final ValidationLevel level;

        private ValidationResult(boolean valid, String error, ValidationLevel level) {
            this.valid = valid;
            this.error = error;
            this.level = level;
        }

        public boolean isValid() {
            return valid;
        }

        public String error() {
            return error;
        }

        public ValidationLevel level() {
            return level;
        }

        public static ValidationResult valid() {
            return new ValidationResult(true, null, ValidationLevel.SUCCESS);
        }

        public static ValidationResult invalid(String error) {
            return invalid(error, ValidationLevel.ERROR);
        }

        public static ValidationResult invalid(String error, ValidationLevel level) {
            return new ValidationResult(false, error, level);
        }

        public static ValidationResult warning(String message) {
            return new ValidationResult(true, message, ValidationLevel.WARNING);
        }
    }

    /**
     * Validation severity levels.
     */
    public enum ValidationLevel {
        SUCCESS,
        WARNING,
        ERROR
    }

    /**
     * Comprehensive JFR file validation.
     * 
     * @param jfrFile Path to the JFR file to validate
     * @return ValidationResult indicating success/failure with details
     */
    public static ValidationResult validate(Path jfrFile) {
        try {
            // Basic file existence and readability
            ValidationResult basicCheck = validateBasicFile(jfrFile);
            if (!basicCheck.isValid()) {
                return basicCheck;
            }

            // JFR format validation
            ValidationResult formatCheck = validateJfrFormat(jfrFile);
            if (!formatCheck.isValid()) {
                return formatCheck;
            }

            // Structural validation
            ValidationResult structureCheck = validateJfrStructure(jfrFile);
            if (!structureCheck.isValid()) {
                return structureCheck;
            }

            LOG.debug("JFR file validation successful: {}", jfrFile);
            return ValidationResult.valid();

        } catch (Exception e) {
            LOG.warn("JFR file validation failed with exception: {} - {}", jfrFile, e.getMessage());
            return ValidationResult.invalid("Validation failed: " + e.getMessage());
        }
    }

    /**
     * Fast validation that only checks file magic and minimum size.
     * Suitable for performance-critical scenarios.
     */
    public static ValidationResult validateFast(Path jfrFile) {
        try {
            ValidationResult basicCheck = validateBasicFile(jfrFile);
            if (!basicCheck.isValid()) {
                return basicCheck;
            }

            ValidationResult magicCheck = validateMagicBytes(jfrFile);
            if (!magicCheck.isValid()) {
                return magicCheck;
            }

            return ValidationResult.valid();

        } catch (Exception e) {
            return ValidationResult.invalid("Fast validation failed: " + e.getMessage());
        }
    }

    private static ValidationResult validateBasicFile(Path jfrFile) {
        if (jfrFile == null) {
            return ValidationResult.invalid("File path is null");
        }

        if (!Files.exists(jfrFile)) {
            return ValidationResult.invalid("File does not exist: " + jfrFile);
        }

        if (!Files.isRegularFile(jfrFile)) {
            return ValidationResult.invalid("Path is not a regular file: " + jfrFile);
        }

        if (!Files.isReadable(jfrFile)) {
            return ValidationResult.invalid("File is not readable: " + jfrFile);
        }

        try {
            long fileSize = Files.size(jfrFile);
            if (fileSize < MIN_JFR_FILE_SIZE) {
                return ValidationResult.invalid(
                    String.format("File too small: %d bytes (minimum: %d)", fileSize, MIN_JFR_FILE_SIZE));
            }
            
            if (fileSize == 0) {
                return ValidationResult.invalid("File is empty");
            }

        } catch (IOException e) {
            return ValidationResult.invalid("Cannot read file size: " + e.getMessage());
        }

        return ValidationResult.valid();
    }

    private static ValidationResult validateMagicBytes(Path jfrFile) {
        try (FileChannel channel = FileChannel.open(jfrFile, READ)) {
            ByteBuffer buffer = ByteBuffer.allocate(FILE_MAGIC.length);
            int bytesRead = channel.read(buffer);
            
            if (bytesRead < FILE_MAGIC.length) {
                return ValidationResult.invalid("Cannot read JFR magic bytes - file too short");
            }
            
            buffer.flip();
            for (int i = 0; i < FILE_MAGIC.length; i++) {
                byte expected = FILE_MAGIC[i];
                byte actual = buffer.get(i);
                if (actual != expected) {
                    return ValidationResult.invalid(
                        String.format("Invalid JFR magic at byte %d: expected 0x%02X, got 0x%02X", 
                            i, expected & 0xFF, actual & 0xFF));
                }
            }
            
            LOG.trace("JFR magic bytes validated successfully: {}", jfrFile);
            return ValidationResult.valid();
            
        } catch (IOException e) {
            return ValidationResult.invalid("Cannot read JFR magic bytes: " + e.getMessage());
        }
    }

    private static ValidationResult validateJfrFormat(Path jfrFile) {
        ValidationResult magicCheck = validateMagicBytes(jfrFile);
        if (!magicCheck.isValid()) {
            return magicCheck;
        }

        return validateVersions(jfrFile);
    }

    private static ValidationResult validateVersions(Path jfrFile) {
        try (FileChannel channel = FileChannel.open(jfrFile, READ)) {
            ByteBuffer buffer = ByteBuffer.allocate(8); // Magic (4) + Major (2) + Minor (2)
            channel.read(buffer);
            buffer.flip();
            
            // Skip magic bytes
            buffer.position(MAJOR_VERSION_OFFSET);
            
            short major = buffer.getShort();
            short minor = buffer.getShort();
            
            // JDK only supports versions 1.x and 2.x
            if (major != 1 && major != 2) {
                return ValidationResult.invalid(
                    String.format("Unsupported JFR version: %d.%d (only 1.x and 2.x supported)", major, minor));
            }
            
            if (major < 1 || major > 2 || minor < 0) {
                return ValidationResult.warning(
                    String.format("Unusual JFR version: %d.%d - parsing may fail", major, minor));
            }
            
            LOG.trace("JFR version validated: {}.{} - {}", major, minor, jfrFile);
            return ValidationResult.valid();
            
        } catch (IOException e) {
            return ValidationResult.invalid("Cannot read JFR version: " + e.getMessage());
        }
    }

    private static ValidationResult validateJfrStructure(Path jfrFile) {
        // Basic structural validation - ensure we can read the first chunk header
        try (JeffreyRecordingInput input = new JeffreyRecordingInput(jfrFile)) {
            JeffreyChunkHeader firstChunk = new JeffreyChunkHeader(input);
            
            // Validate chunk size is reasonable
            long chunkSize = firstChunk.getChunkSize();
            long fileSize = Files.size(jfrFile);
            
            if (chunkSize <= 0) {
                return ValidationResult.invalid("Invalid chunk size: " + chunkSize);
            }
            
            if (chunkSize > fileSize) {
                return ValidationResult.invalid(
                    String.format("Chunk size (%d) exceeds file size (%d)", chunkSize, fileSize));
            }
            
            // Validate start time is reasonable (not negative, not too far in future)
            long startNanos = firstChunk.getStartNanos();
            if (startNanos < 0) {
                return ValidationResult.warning("Chunk start time is negative: " + startNanos);
            }
            
            // Validate duration
            long durationNanos = firstChunk.getDurationNanos();
            if (durationNanos < 0) {
                return ValidationResult.warning("Chunk duration is negative: " + durationNanos);
            }
            
            LOG.trace("JFR structure validated successfully: {}", jfrFile);
            return ValidationResult.valid();
            
        } catch (IOException e) {
            LOG.debug("JFR structure validation failed: {} - {}", jfrFile, e.getMessage());
            return ValidationResult.invalid("Structure validation failed: " + e.getMessage());
        }
    }

    /**
     * Quick check to determine if a file is likely a JFR file.
     * Only checks magic bytes for performance.
     */
    public static boolean isLikelyJfrFile(Path filePath) {
        ValidationResult result = validateMagicBytes(filePath);
        return result.isValid();
    }

    /**
     * Get detailed file information for diagnostics.
     */
    public static String getFileInfo(Path jfrFile) {
        try {
            if (!Files.exists(jfrFile)) {
                return "File does not exist";
            }
            
            long size = Files.size(jfrFile);
            boolean readable = Files.isReadable(jfrFile);
            boolean isFile = Files.isRegularFile(jfrFile);
            
            StringBuilder info = new StringBuilder();
            info.append("File: ").append(jfrFile).append("\n");
            info.append("Size: ").append(size).append(" bytes\n");
            info.append("Readable: ").append(readable).append("\n");
            info.append("Regular file: ").append(isFile).append("\n");
            
            if (size >= 8 && readable) {
                try (FileChannel channel = FileChannel.open(jfrFile, READ)) {
                    ByteBuffer buffer = ByteBuffer.allocate(8);
                    channel.read(buffer);
                    buffer.flip();
                    
                    info.append("Magic bytes: ");
                    for (int i = 0; i < 4; i++) {
                        info.append(String.format("0x%02X ", buffer.get(i) & 0xFF));
                    }
                    info.append("\n");
                    
                    short major = buffer.getShort(4);
                    short minor = buffer.getShort(6);
                    info.append("Version: ").append(major).append(".").append(minor).append("\n");
                }
            }
            
            return info.toString();
            
        } catch (IOException e) {
            return "Error reading file info: " + e.getMessage();
        }
    }
}