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
import pbouda.jeffrey.provider.api.RecordingInformationParser;
import pbouda.jeffrey.provider.api.model.recording.RecordingInformation;

import java.nio.file.Path;

/**
 * Safe implementation of RecordingInformationParser that uses JDK-style parsing
 * with comprehensive error handling and recovery mechanisms.
 * 
 * This class serves as a drop-in replacement for the existing JfrRecordingInformationParser
 * but provides much more robust handling of corrupted or malformed JFR files.
 */
public class SafeJfrRecordingInformationParser implements RecordingInformationParser {

    private static final Logger LOG = LoggerFactory.getLogger(SafeJfrRecordingInformationParser.class);

    @Override
    public RecordingInformation provide(Path recording) {
        LOG.debug("Parsing recording information for: {}", recording);
        
        try {
            // Use JDK-style parser with graceful degradation
            RecordingInformation result = JdkStyleRecordingParser.getRecordingInformation(
                recording, 
                JdkStyleRecordingParser.RecoveryStrategy.GRACEFUL_DEGRADATION
            );
            
            LOG.debug("Successfully parsed recording: {} -> size={} start={} end={} source={}", 
                recording, result.sizeInBytes(), result.recordingStartedAt(), 
                result.recordingFinishedAt(), result.eventSource());
            
            return result;
            
        } catch (Exception e) {
            LOG.error("Critical error parsing recording {}: {}", recording, e.getMessage(), e);
            
            // Last resort - return empty recording to prevent system failure
            return JdkStyleRecordingParser.getRecordingInformation(
                recording, 
                JdkStyleRecordingParser.RecoveryStrategy.GRACEFUL_DEGRADATION
            );
        }
    }
    
    /**
     * Enhanced parsing with custom recovery strategy.
     * 
     * @param recording Path to JFR file
     * @param strategy Recovery strategy to use
     * @return RecordingInformation, never null
     */
    public RecordingInformation provide(Path recording, JdkStyleRecordingParser.RecoveryStrategy strategy) {
        LOG.debug("Parsing recording information with strategy {}: {}", strategy, recording);
        
        try {
            RecordingInformation result = JdkStyleRecordingParser.getRecordingInformation(recording, strategy);
            
            LOG.debug("Successfully parsed recording with {}: {} -> size={} start={} end={} source={}", 
                strategy, recording, result.sizeInBytes(), result.recordingStartedAt(), 
                result.recordingFinishedAt(), result.eventSource());
            
            return result;
            
        } catch (Exception e) {
            LOG.error("Critical error parsing recording {} with strategy {}: {}", recording, strategy, e.getMessage(), e);
            
            // Fallback to most permissive strategy
            if (strategy != JdkStyleRecordingParser.RecoveryStrategy.GRACEFUL_DEGRADATION) {
                LOG.warn("Falling back to graceful degradation strategy for: {}", recording);
                return provide(recording, JdkStyleRecordingParser.RecoveryStrategy.GRACEFUL_DEGRADATION);
            }
            
            // Ultimate fallback - should never happen, but prevents NPE
            return createEmptyRecording();
        }
    }
    
    /**
     * Fast parsing variant for performance-critical scenarios.
     * 
     * @param recording Path to JFR file
     * @return RecordingInformation, never null
     */
    public RecordingInformation provideFast(Path recording) {
        LOG.debug("Fast parsing recording information: {}", recording);
        
        try {
            RecordingInformation result = JdkStyleRecordingParser.getRecordingInformationFast(recording);
            
            LOG.debug("Fast parsed recording: {} -> size={} start={} end={} source={}", 
                recording, result.sizeInBytes(), result.recordingStartedAt(), 
                result.recordingFinishedAt(), result.eventSource());
            
            return result;
            
        } catch (Exception e) {
            LOG.warn("Fast parsing failed for {}: {} - falling back to safe parsing", recording, e.getMessage());
            return provide(recording);
        }
    }
    
    /**
     * Get detailed diagnostics about a JFR file.
     * Useful for debugging parsing issues.
     * 
     * @param recording Path to JFR file
     * @return Diagnostic information string
     */
    public String getDiagnostics(Path recording) {
        StringBuilder diagnostics = new StringBuilder();
        diagnostics.append("=== JFR File Diagnostics ===\n");
        
        try {
            // Basic file info
            diagnostics.append(JfrFileValidator.getFileInfo(recording));
            diagnostics.append("\n");
            
            // Validation results
            JfrFileValidator.ValidationResult validation = JfrFileValidator.validate(recording);
            diagnostics.append("Validation: ").append(validation.isValid() ? "PASSED" : "FAILED").append("\n");
            if (!validation.isValid()) {
                diagnostics.append("Error: ").append(validation.error()).append("\n");
                diagnostics.append("Level: ").append(validation.level()).append("\n");
            }
            diagnostics.append("\n");
            
            // Chunk information
            var chunks = JdkStyleRecordingParser.getChunkInformation(recording);
            diagnostics.append("Chunks found: ").append(chunks.size()).append("\n");
            for (var chunk : chunks) {
                diagnostics.append("  ").append(chunk.toString()).append("\n");
            }
            diagnostics.append("\n");
            
            // Recording information
            try {
                RecordingInformation info = provide(recording);
                diagnostics.append("Recording info:\n");
                diagnostics.append("  Size: ").append(info.sizeInBytes()).append(" bytes\n");
                diagnostics.append("  Start: ").append(info.recordingStartedAt()).append("\n");
                diagnostics.append("  End: ").append(info.recordingFinishedAt()).append("\n");
                diagnostics.append("  Source: ").append(info.eventSource()).append("\n");
            } catch (Exception e) {
                diagnostics.append("Recording info: ERROR - ").append(e.getMessage()).append("\n");
            }
            
        } catch (Exception e) {
            diagnostics.append("Diagnostics failed: ").append(e.getMessage()).append("\n");
        }
        
        return diagnostics.toString();
    }
    
    private RecordingInformation createEmptyRecording() {
        return new RecordingInformation(0, 
            pbouda.jeffrey.common.model.EventSource.JDK, 
            null, 
            null);
    }
}