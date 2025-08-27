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
import pbouda.jeffrey.common.model.EventSource;
import pbouda.jeffrey.provider.api.model.recording.RecordingInformation;

import java.nio.file.Path;

/**
 * Drop-in replacement for the original Recordings class with JDK-style robustness.
 * Provides the same interface as the original but with enhanced error handling,
 * validation, and recovery mechanisms.
 */
public abstract class JdkStyleRecordings {

    private static final Logger LOG = LoggerFactory.getLogger(JdkStyleRecordings.class);

    /**
     * Safe replacement for Recordings.aggregatedRecordingInfo() that handles
     * corrupted files gracefully without throwing exceptions.
     * 
     * @param recording Path to JFR file
     * @return RecordingInformation, never null
     */
    public static RecordingInformation aggregatedRecordingInfo(Path recording) {
        LOG.debug("Getting aggregated recording info for: {}", recording);
        
        try {
            return JdkStyleRecordingParser.getRecordingInformation(
                recording, 
                JdkStyleRecordingParser.RecoveryStrategy.GRACEFUL_DEGRADATION
            );
        } catch (Exception e) {
            LOG.warn("Failed to get recording info for {}: {} - returning empty recording", 
                recording, e.getMessage());
            return createSafeEmptyRecording();
        }
    }

    /**
     * Validates a recording file before processing.
     * Unlike the original validateRecording, this provides comprehensive validation.
     * 
     * @param recording Path to validate
     * @throws IllegalArgumentException if file is invalid and cannot be processed
     */
    public static void validateRecording(Path recording) {
        JfrFileValidator.ValidationResult result = JfrFileValidator.validate(recording);
        if (!result.isValid()) {
            throw new IllegalArgumentException("Recording validation failed: " + result.error());
        }
    }

    /**
     * Safe validation that doesn't throw exceptions.
     * 
     * @param recording Path to validate
     * @return true if recording is valid and can be processed
     */
    public static boolean isValidRecording(Path recording) {
        try {
            JfrFileValidator.ValidationResult result = JfrFileValidator.validate(recording);
            return result.isValid();
        } catch (Exception e) {
            LOG.debug("Validation failed for {}: {}", recording, e.getMessage());
            return false;
        }
    }

    /**
     * Fast validation suitable for batch processing.
     * 
     * @param recording Path to validate
     * @return true if recording passes basic validation
     */
    public static boolean isLikelyValidRecording(Path recording) {
        try {
            JfrFileValidator.ValidationResult result = JfrFileValidator.validateFast(recording);
            return result.isValid();
        } catch (Exception e) {
            LOG.debug("Fast validation failed for {}: {}", recording, e.getMessage());
            return false;
        }
    }

    /**
     * Safe replacement for the original mergeRecordings method with enhanced validation.
     * 
     * @param recordings List of recordings to merge
     * @param outputPath Output path for merged recording
     */
    public static void mergeRecordings(java.util.List<Path> recordings, Path outputPath) {
        LOG.debug("Merging {} recordings to: {}", recordings.size(), outputPath);
        
        // Validate all input files first
        for (Path recording : recordings) {
            if (!isValidRecording(recording)) {
                throw new IllegalArgumentException("Invalid recording cannot be merged: " + recording);
            }
        }
        
        try {
            // Delegate to original implementation after validation
            pbouda.jeffrey.provider.reader.jfr.chunk.Recordings.mergeRecordings(recordings, outputPath);
            LOG.debug("Successfully merged {} recordings to: {}", recordings.size(), outputPath);
        } catch (Exception e) {
            LOG.error("Failed to merge recordings to {}: {}", outputPath, e.getMessage(), e);
            throw new RuntimeException("Recording merge failed: " + e.getMessage(), e);
        }
    }

    /**
     * Get comprehensive diagnostics for a recording file.
     * Useful for debugging parsing issues.
     * 
     * @param recording Path to analyze
     * @return Diagnostic information
     */
    public static String getDiagnostics(Path recording) {
        SafeJfrRecordingInformationParser parser = new SafeJfrRecordingInformationParser();
        return parser.getDiagnostics(recording);
    }

    /**
     * Get chunk-level information from a recording.
     * 
     * @param recording Path to analyze
     * @return List of chunk information
     */
    public static java.util.List<JdkStyleRecordingParser.ChunkInfo> getChunkInfo(Path recording) {
        return JdkStyleRecordingParser.getChunkInformation(recording);
    }

    private static RecordingInformation createSafeEmptyRecording() {
        return new RecordingInformation(0, EventSource.JDK, null, null);
    }

    /**
     * Factory method to create a safe recording information parser.
     * 
     * @return SafeJfrRecordingInformationParser instance
     */
    public static SafeJfrRecordingInformationParser createSafeParser() {
        return new SafeJfrRecordingInformationParser();
    }
}