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
import java.time.Duration;
import java.time.Instant;
import java.util.Set;

/**
 * JDK-inspired ChunkHeader implementation for robust JFR chunk parsing.
 * Based on jdk.jfr.internal.consumer.ChunkHeader with Jeffrey-specific adaptations.
 */
public final class JeffreyChunkHeader {

    private static final Logger LOG = LoggerFactory.getLogger(JeffreyChunkHeader.class);
    
    // JFR format constants based on JDK implementation
    public static final long HEADER_SIZE = 68;
    static final byte UPDATING_CHUNK_HEADER = (byte) 255;
    public static final long CHUNK_SIZE_POSITION = 8;
    static final long DURATION_NANOS_POSITION = 40;
    static final long FILE_STATE_POSITION = 64;
    static final long FLAG_BYTE_POSITION = 67;
    static final byte[] FILE_MAGIC = { 'F', 'L', 'R', '\0' };
    static final int MASK_FINAL_CHUNK = 1 << 1;

    private final short major;
    private final short minor;
    private final long chunkStartTicks;
    private final long ticksPerSecond;
    private final long chunkStartNanos;
    private final long absoluteChunkStart;
    private final JeffreyRecordingInput input;
    private final long chunkId;
    
    private long absoluteEventStart;
    private long chunkSize = 0;
    private long constantPoolPosition = 0;
    private long metadataPosition = 0;
    private long durationNanos;
    private long absoluteChunkEnd;
    private boolean finished;
    private boolean finalChunk;

    public JeffreyChunkHeader(JeffreyRecordingInput input) throws IOException {
        this(input, 0, 0);
    }

    private JeffreyChunkHeader(JeffreyRecordingInput input, long absoluteChunkStart, long chunkId) throws IOException {
        this.absoluteChunkStart = absoluteChunkStart;
        this.absoluteEventStart = absoluteChunkStart + HEADER_SIZE;
        this.chunkId = chunkId;
        
        if (input.getFileSize() < HEADER_SIZE) {
            throw new IOException("Not a complete Chunk header (file size: " + input.getFileSize() + ")");
        }
        
        input.setValidSize(absoluteChunkStart + HEADER_SIZE);
        input.position(absoluteChunkStart);
        
        if (input.position() >= input.size()) {
            throw new IOException("Chunk contains no data at position: " + input.position());
        }
        
        verifyMagic(input);
        this.input = input;
        
        LOG.debug("Processing chunk: id={} start={}", chunkId, absoluteChunkStart);
        
        // Read chunk header fields
        major = input.readRawShort();
        LOG.debug("Chunk major version: {}", major);
        
        minor = input.readRawShort();
        LOG.debug("Chunk minor version: {}", minor);
        
        // Validate JFR version
        if (major != 1 && major != 2) {
            throw new IOException("File version " + major + "." + minor + 
                ". Only Flight Recorder files of version 1.x and 2.x can be read.");
        }
        
        // Skip chunk size, constant pool position and metadata position
        // These are updated by JVM and not reliable during reading
        input.skipBytesVoid(3 * Long.BYTES);
        
        chunkStartNanos = input.readRawLong();
        LOG.debug("Chunk start nanos: {}", chunkStartNanos);
        
        // Duration nanos, updated by JVM and not reliable
        input.skipBytesVoid(Long.BYTES);
        
        chunkStartTicks = input.readRawLong();
        LOG.debug("Chunk start ticks: {}", chunkStartTicks);
        
        ticksPerSecond = input.readRawLong();
        LOG.debug("Chunk ticks per second: {}", ticksPerSecond);
        
        // Skip file state and flag bit, updated by JVM
        input.skipBytesVoid(Integer.BYTES);
        
        refresh();
        input.position(absoluteEventStart);
    }

    private byte readFileState() throws IOException {
        byte fs;
        input.positionPhysical(absoluteChunkStart + FILE_STATE_POSITION);
        fs = input.readPhysicalByte();
        return fs;
    }

    public void refresh() throws IOException {
        byte fileState = readFileState();
        
        input.positionPhysical(absoluteChunkStart + CHUNK_SIZE_POSITION);
        long chunkSize = input.readPhysicalLong();
        long constantPoolPosition = input.readPhysicalLong();
        long metadataPosition = input.readPhysicalLong();
        
        input.positionPhysical(absoluteChunkStart + DURATION_NANOS_POSITION);
        long durationNanos = input.readPhysicalLong();
        
        input.positionPhysical(absoluteChunkStart + FLAG_BYTE_POSITION);
        int flagByte = input.readPhysicalByte();
        
        // For static files (Jeffrey's case), we assume finished state
        finished = true;
        
        if (metadataPosition != 0) {
            LOG.debug("Setting input size to: {}", absoluteChunkStart + chunkSize);
            input.setValidSize(absoluteChunkStart + chunkSize);
            
            this.chunkSize = chunkSize;
            LOG.debug("Chunk size: {}", chunkSize);
            
            this.constantPoolPosition = constantPoolPosition;
            LOG.debug("Constant pool position: {}", constantPoolPosition);
            
            this.metadataPosition = metadataPosition;
            LOG.debug("Metadata position: {}", metadataPosition);
            
            this.durationNanos = durationNanos;
            LOG.debug("Duration nanos: {}", durationNanos);
            
            this.finalChunk = (flagByte & MASK_FINAL_CHUNK) != 0;
            LOG.debug("Final chunk: {}", finalChunk);
            
            absoluteChunkEnd = absoluteChunkStart + chunkSize;
        } else {
            throw new IOException("No metadata found in chunk. This may indicate a corrupted or incomplete JFR file.");
        }
    }

    public boolean isLastChunk() {
        // For static files, check if this chunk reaches end of file
        return input.getFileSize() == absoluteChunkEnd;
    }

    public boolean isFinalChunk() {
        return finalChunk;
    }

    public boolean isFinished() {
        return finished;
    }

    public JeffreyChunkHeader nextHeader() throws IOException {
        if (isLastChunk()) {
            throw new IOException("No more chunks available");
        }
        return new JeffreyChunkHeader(input, absoluteChunkEnd, chunkId + 1);
    }

    // Getters based on JDK implementation
    public short getMajor() {
        return major;
    }

    public short getMinor() {
        return minor;
    }

    public long getAbsoluteChunkStart() {
        return absoluteChunkStart;
    }

    public long getAbsoluteEventStart() {
        return absoluteEventStart;
    }

    public long getConstantPoolPosition() {
        return constantPoolPosition;
    }

    public long getMetadataPosition() {
        return metadataPosition;
    }

    public long getStartTicks() {
        return chunkStartTicks;
    }

    public long getChunkSize() {
        return chunkSize;
    }

    public double getTicksPerSecond() {
        return ticksPerSecond;
    }

    public long getStartNanos() {
        return chunkStartNanos;
    }

    public long getEnd() {
        return absoluteChunkEnd;
    }

    public long getSize() {
        return chunkSize;
    }

    public long getDurationNanos() {
        return durationNanos;
    }

    public JeffreyRecordingInput getInput() {
        return input;
    }

    public long getEventStart() {
        return absoluteEventStart;
    }

    public static long headerSize() {
        return HEADER_SIZE;
    }

    public long getLastNanos() {
        return getStartNanos() + getDurationNanos();
    }

    public long getChunkId() {
        return chunkId;
    }

    // Jeffrey-specific convenience methods
    public Instant getStartTime() {
        return Instant.ofEpochSecond(
            chunkStartNanos / 1_000_000_000,
            chunkStartNanos % 1_000_000_000
        );
    }

    public Duration getDuration() {
        return Duration.ofNanos(durationNanos);
    }

    public Instant getEndTime() {
        return getStartTime().plus(getDuration());
    }

    private static void verifyMagic(JeffreyRecordingInput input) throws IOException {
        for (byte expectedByte : FILE_MAGIC) {
            byte actualByte = input.readByte();
            if (actualByte != expectedByte) {
                throw new IOException(String.format(
                    "Invalid JFR file magic. Expected: %02X, got: %02X at position: %d", 
                    expectedByte & 0xFF, actualByte & 0xFF, input.position() - 1));
            }
        }
        LOG.debug("JFR file magic verified successfully");
    }
}