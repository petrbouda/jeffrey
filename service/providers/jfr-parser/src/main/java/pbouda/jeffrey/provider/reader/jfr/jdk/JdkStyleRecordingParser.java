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

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * JDK-inspired JFR recording parser that provides robust parsing capabilities
 * with comprehensive error handling and recovery mechanisms.
 * 
 * This class serves as the main entry point for JFR file analysis using
 * JDK-compatible parsing logic adapted for Jeffrey's requirements.
 */
public final class JdkStyleRecordingParser {

    private static final Logger LOG = LoggerFactory.getLogger(JdkStyleRecordingParser.class);

    /**
     * Comprehensive recording information extraction with multiple validation levels.
     */
    public static RecordingInformation getRecordingInformation(Path recording) {
        return getRecordingInformation(recording, RecoveryStrategy.GRACEFUL_DEGRADATION);
    }

    /**
     * Get recording information with specified recovery strategy.
     * 
     * @param recording Path to JFR file
     * @param strategy How to handle parsing errors
     * @return RecordingInformation, never null
     */
    public static RecordingInformation getRecordingInformation(Path recording, RecoveryStrategy strategy) {
        try {
            LOG.debug("Starting JDK-style parsing of: {}", recording);
            
            // Validate file first
            JfrFileValidator.ValidationResult validation = JfrFileValidator.validate(recording);
            if (!validation.isValid()) {
                return handleValidationFailure(recording, validation, strategy);
            }
            
            // Parse chunks and extract information
            return parseRecordingInformation(recording, strategy);
            
        } catch (Exception e) {
            LOG.warn("JDK-style parsing failed for {}: {}", recording, e.getMessage(), e);
            return handleParsingException(recording, e, strategy);
        }
    }

    /**
     * Fast recording information extraction with minimal validation.
     * Suitable for performance-critical scenarios where file integrity is assumed.
     */
    public static RecordingInformation getRecordingInformationFast(Path recording) {
        try {
            // Only basic validation
            JfrFileValidator.ValidationResult validation = JfrFileValidator.validateFast(recording);
            if (!validation.isValid()) {
                LOG.warn("Fast validation failed for {}: {}", recording, validation.error());
                return createEmptyRecording();
            }
            
            return parseRecordingInformation(recording, RecoveryStrategy.FAIL_FAST);
            
        } catch (Exception e) {
            LOG.debug("Fast parsing failed for {}: {}", recording, e.getMessage());
            return createEmptyRecording();
        }
    }

    /**
     * Extract chunk information without full recording analysis.
     * Useful for debugging and file inspection.
     */
    public static List<ChunkInfo> getChunkInformation(Path recording) {
        List<ChunkInfo> chunks = new ArrayList<>();
        
        try {
            JfrFileValidator.ValidationResult validation = JfrFileValidator.validate(recording);
            if (!validation.isValid()) {
                LOG.warn("Cannot extract chunk info from invalid file {}: {}", recording, validation.error());
                return chunks;
            }
            
            try (JeffreyRecordingInput input = new JeffreyRecordingInput(recording)) {
                JeffreyChunkHeader header = new JeffreyChunkHeader(input);
                chunks.add(createChunkInfo(header, 0));
                
                while (!header.isLastChunk()) {
                    try {
                        header = header.nextHeader();
                        chunks.add(createChunkInfo(header, chunks.size()));
                    } catch (IOException e) {
                        LOG.warn("Failed to read chunk {} in file {}: {}", chunks.size(), recording, e.getMessage());
                        break;
                    }
                }
            }
            
            LOG.debug("Extracted {} chunk(s) from {}", chunks.size(), recording);
            
        } catch (Exception e) {
            LOG.warn("Failed to extract chunk information from {}: {}", recording, e.getMessage());
        }
        
        return chunks;
    }

    private static RecordingInformation parseRecordingInformation(Path recording, RecoveryStrategy strategy) throws IOException {
        try (JeffreyRecordingInput input = new JeffreyRecordingInput(recording)) {
            List<JeffreyChunkHeader> chunks = new ArrayList<>();
            Set<String> eventTypes = new HashSet<>();
            
            JeffreyChunkHeader header = new JeffreyChunkHeader(input);
            chunks.add(header);
            
            // Collect all chunks
            while (!header.isLastChunk()) {
                try {
                    header = header.nextHeader();
                    chunks.add(header);
                } catch (IOException e) {
                    if (strategy == RecoveryStrategy.FAIL_FAST) {
                        throw e;
                    }
                    LOG.warn("Failed to read chunk {} in {}: {} - using partial data", 
                        chunks.size(), recording, e.getMessage());
                    break;
                }
            }
            
            if (chunks.isEmpty()) {
                LOG.warn("No valid chunks found in {}", recording);
                return createEmptyRecording();
            }
            
            // Calculate aggregated information
            long totalBytes = chunks.stream().mapToLong(JeffreyChunkHeader::getSize).sum();
            
            Instant startTime = chunks.stream()
                .map(JeffreyChunkHeader::getStartTime)
                .min(Instant::compareTo)
                .orElse(null);
            
            Instant endTime = chunks.stream()
                .map(JeffreyChunkHeader::getEndTime)
                .max(Instant::compareTo)
                .orElse(null);
            
            // Determine event source (simplified - would need actual event parsing for accuracy)
            EventSource source = determineEventSource(chunks);
            
            LOG.debug("Parsed recording info: bytes={} start={} end={} source={} chunks={}", 
                totalBytes, startTime, endTime, source, chunks.size());
            
            return new RecordingInformation(totalBytes, source, startTime, endTime);
        }
    }

    private static EventSource determineEventSource(List<JeffreyChunkHeader> chunks) {
        // This is a simplified implementation
        // In a full implementation, we would parse actual events to determine the source
        // For now, we assume JDK source unless we detect async-profiler patterns
        
        // Could be enhanced by:
        // 1. Parsing metadata to look for async-profiler event types
        // 2. Analyzing event type names
        // 3. Looking at custom event patterns
        
        return EventSource.JDK;
    }

    private static RecordingInformation handleValidationFailure(
            Path recording, 
            JfrFileValidator.ValidationResult validation, 
            RecoveryStrategy strategy) {
        
        switch (strategy) {
            case FAIL_FAST:
                throw new RuntimeException("JFR validation failed: " + validation.error());
                
            case GRACEFUL_DEGRADATION:
                LOG.warn("JFR validation failed for {}: {} - returning empty recording", 
                    recording, validation.error());
                return createEmptyRecording();
                
            case BEST_EFFORT:
                LOG.warn("JFR validation failed for {}: {} - attempting best-effort parsing", 
                    recording, validation.error());
                try {
                    return parseRecordingInformation(recording, RecoveryStrategy.BEST_EFFORT);
                } catch (Exception e) {
                    LOG.warn("Best-effort parsing also failed: {}", e.getMessage());
                    return createEmptyRecording();
                }
                
            default:
                return createEmptyRecording();
        }
    }

    private static RecordingInformation handleParsingException(
            Path recording, 
            Exception exception, 
            RecoveryStrategy strategy) {
        
        switch (strategy) {
            case FAIL_FAST:
                if (exception instanceof RuntimeException) {
                    throw (RuntimeException) exception;
                }
                throw new RuntimeException("JFR parsing failed", exception);
                
            case GRACEFUL_DEGRADATION:
            case BEST_EFFORT:
                LOG.warn("JFR parsing failed for {}: {} - returning empty recording", 
                    recording, exception.getMessage());
                return createEmptyRecording();
                
            default:
                return createEmptyRecording();
        }
    }

    private static RecordingInformation createEmptyRecording() {
        return new RecordingInformation(0, EventSource.JDK, null, null);
    }

    private static ChunkInfo createChunkInfo(JeffreyChunkHeader header, int index) {
        return new ChunkInfo(
            index,
            header.getChunkId(),
            header.getSize(),
            header.getStartTime(),
            header.getEndTime(),
            header.getMajor(),
            header.getMinor(),
            header.isFinalChunk()
        );
    }

    /**
     * Strategy for handling parsing errors and recovery.
     */
    public enum RecoveryStrategy {
        /** Throw exceptions immediately on any error */
        FAIL_FAST,
        
        /** Return safe defaults when parsing fails */
        GRACEFUL_DEGRADATION,
        
        /** Try to extract as much information as possible despite errors */
        BEST_EFFORT
    }

    /**
     * Information about a single JFR chunk.
     */
    public static final class ChunkInfo {
        private final int index;
        private final long chunkId;
        private final long size;
        private final Instant startTime;
        private final Instant endTime;
        private final short majorVersion;
        private final short minorVersion;
        private final boolean finalChunk;

        private ChunkInfo(int index, long chunkId, long size, Instant startTime, Instant endTime, 
                         short majorVersion, short minorVersion, boolean finalChunk) {
            this.index = index;
            this.chunkId = chunkId;
            this.size = size;
            this.startTime = startTime;
            this.endTime = endTime;
            this.majorVersion = majorVersion;
            this.minorVersion = minorVersion;
            this.finalChunk = finalChunk;
        }

        public int getIndex() { return index; }
        public long getChunkId() { return chunkId; }
        public long getSize() { return size; }
        public Instant getStartTime() { return startTime; }
        public Instant getEndTime() { return endTime; }
        public short getMajorVersion() { return majorVersion; }
        public short getMinorVersion() { return minorVersion; }
        public boolean isFinalChunk() { return finalChunk; }

        @Override
        public String toString() {
            return String.format("Chunk[%d] id=%d size=%d version=%d.%d start=%s end=%s final=%s",
                index, chunkId, size, majorVersion, minorVersion, startTime, endTime, finalChunk);
        }
    }
}