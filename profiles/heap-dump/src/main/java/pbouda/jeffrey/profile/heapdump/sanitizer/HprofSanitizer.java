/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
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

package pbouda.jeffrey.profile.heapdump.sanitizer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import static pbouda.jeffrey.profile.heapdump.sanitizer.HprofConstants.*;

/**
 * Repairs/sanitizes HPROF heap dump files that may be structurally invalid
 * due to ungraceful JVM termination (OOMKill, SIGKILL, crash).
 *
 * <p>Known corruption patterns handled:</p>
 * <ol>
 *   <li>Zero-length HEAP_DUMP_SEGMENT records (deferred length write-back)</li>
 *   <li>Truncated last record (unflushed write buffer)</li>
 *   <li>Missing HEAP_DUMP_END terminator</li>
 *   <li>Truncated sub-records within segments</li>
 *   <li>Negative/overflowed record lengths (&gt;2GB segments)</li>
 * </ol>
 *
 * <p>The sanitizer uses streaming I/O to handle large heap dumps (10+ GB)
 * without loading the entire file into memory. Sub-record scanning for
 * corrupted segments uses a bounded read buffer.</p>
 */
public class HprofSanitizer {

    private static final Logger LOG = LoggerFactory.getLogger(HprofSanitizer.class);

    /**
     * Buffer size for reading/writing data (256 KB).
     */
    private static final int IO_BUFFER_SIZE = 256 * 1024;

    /**
     * Maximum segment size to buffer for sub-record scanning (512 MB).
     * Segments larger than this during zero-length repair are scanned
     * incrementally without full buffering.
     */
    private static final int MAX_SEGMENT_SCAN_BUFFER = 512 * 1024 * 1024;

    /**
     * The 9-byte HEAP_DUMP_END record to append when missing.
     * Tag 0x2C, timestamp 0x00000000, length 0x00000000.
     */
    private static final byte[] HEAP_DUMP_END_RECORD = {
            0x2C, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00
    };

    private HprofSanitizer() {
    }

    /**
     * Checks if the HPROF file likely needs sanitization.
     * Performs a quick check by scanning record tags to detect obvious corruption,
     * such as a missing HEAP_DUMP_END marker, zero-length segments, or truncated records.
     *
     * @param hprofFile path to the HPROF file
     * @return true if the file appears to need sanitization
     * @throws IOException if the file cannot be read
     */
    public static boolean needsSanitization(Path hprofFile) throws IOException {
        long fileSize = Files.size(hprofFile);
        if (fileSize < MIN_HEADER_SIZE) {
            return true;
        }

        try (FileChannel channel = FileChannel.open(hprofFile, StandardOpenOption.READ)) {
            // Check for gzip
            ByteBuffer magic = ByteBuffer.allocate(2);
            channel.read(magic, 0);
            magic.flip();
            if (magic.get(0) == GZIP_MAGIC_1 && magic.get(1) == GZIP_MAGIC_2) {
                LOG.debug("HPROF file is gzip-compressed, sanitization check requires decompression: path={}", hprofFile);
                return false; // Gzip files should be decompressed first
            }

            // Parse header to get idSize
            int idSize = parseIdSize(channel);
            if (idSize != 4 && idSize != 8) {
                return true; // Invalid header
            }

            // Determine header length by scanning for null terminator in version string
            int headerLength = parseHeaderLength(channel);
            if (headerLength < 0) {
                return true;
            }

            // Quick scan through records looking for problems
            long position = headerLength;
            boolean foundEnd = false;
            ByteBuffer recordHeader = ByteBuffer.allocate(RECORD_HEADER_SIZE);
            recordHeader.order(ByteOrder.BIG_ENDIAN);

            while (position + RECORD_HEADER_SIZE <= fileSize) {
                recordHeader.clear();
                int bytesRead = channel.read(recordHeader, position);
                if (bytesRead < RECORD_HEADER_SIZE) {
                    return true; // Truncated
                }
                recordHeader.flip();

                int tag = recordHeader.get(0) & 0xFF;
                int length = recordHeader.getInt(5); // bytes 5-8

                if (!KNOWN_TAGS.contains(tag)) {
                    return true; // Unknown tag = corruption
                }

                if (tag == TAG_HEAP_DUMP_END) {
                    foundEnd = true;
                    position += RECORD_HEADER_SIZE;
                    continue;
                }

                if (tag == TAG_HEAP_DUMP_SEGMENT || tag == TAG_HEAP_DUMP) {
                    if (length == 0) {
                        return true; // Zero-length segment
                    }
                    if (length < 0) {
                        return true; // Overflowed length
                    }
                }

                long bodyLength = Integer.toUnsignedLong(length);
                long nextPosition = position + RECORD_HEADER_SIZE + bodyLength;
                if (nextPosition > fileSize) {
                    return true; // Truncated record
                }

                position = nextPosition;
            }

            // Check if there are leftover bytes after the last record
            if (position < fileSize && !foundEnd) {
                return true;
            }

            return !foundEnd;
        }
    }

    /**
     * Sanitizes the HPROF file and writes a repaired version.
     * If the file is already valid, copies it as-is.
     *
     * @param input  path to the potentially corrupt HPROF file
     * @param output path for the repaired HPROF file
     * @return result with metadata about what was fixed
     * @throws IOException if the file cannot be read or written
     */
    public static SanitizeResult sanitize(Path input, Path output) throws IOException {
        long fileSize = Files.size(input);
        if (fileSize == 0) {
            throw new IOException("HPROF file is empty: " + input);
        }

        try (FileChannel inChannel = FileChannel.open(input, StandardOpenOption.READ)) {
            // Check for gzip magic
            ByteBuffer magic = ByteBuffer.allocate(2);
            inChannel.read(magic, 0);
            magic.flip();
            if (magic.get(0) == GZIP_MAGIC_1 && magic.get(1) == GZIP_MAGIC_2) {
                throw new IOException(
                        "HPROF file is gzip-compressed. Decompress it before sanitization: " + input);
            }

            // Parse and validate header
            int headerLength = parseHeaderLength(inChannel);
            if (headerLength < 0) {
                throw new IOException("Invalid HPROF file header: " + input);
            }

            int idSize = parseIdSize(inChannel);
            if (idSize != 4 && idSize != 8) {
                throw new IOException("Invalid HPROF identifier size: " + idSize + " in " + input);
            }

            LOG.info("Sanitizing HPROF file: path={} fileSize={} idSize={}", input, fileSize, idSize);

            SanitizeResult sanitizeResult;
            try (OutputStream out = new BufferedOutputStream(
                    Files.newOutputStream(output, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING),
                    IO_BUFFER_SIZE)) {

                // Copy header
                copyBytes(inChannel, 0, headerLength, out);

                // Process records
                sanitizeResult = processRecords(inChannel, headerLength, fileSize, idSize, out);
            }

            long bytesWritten = Files.size(output);
            LOG.info("Sanitization complete: modified={} bytesRead={} bytesWritten={} message={}",
                    sanitizeResult.wasModified(), sanitizeResult.totalBytesRead(), bytesWritten,
                    sanitizeResult.summaryMessage());

            // Return result with actual bytes written
            return new SanitizeResult(
                    sanitizeResult.wasModified(),
                    sanitizeResult.hadZeroLengthSegments(),
                    sanitizeResult.wasTruncated(),
                    sanitizeResult.hadMissingEndMarker(),
                    sanitizeResult.hadTruncatedSubRecords(),
                    sanitizeResult.hadOverflowedLengths(),
                    sanitizeResult.zeroLengthSegmentsFixed(),
                    sanitizeResult.totalRecordsProcessed(),
                    sanitizeResult.totalBytesRead(),
                    bytesWritten,
                    sanitizeResult.estimatedObjectsRecovered(),
                    sanitizeResult.summaryMessage()
            );
        }
    }

    /**
     * Processes all top-level records, repairing corruption as encountered.
     */
    private static SanitizeResult processRecords(
            FileChannel inChannel, long startPosition, long fileSize,
            int idSize, OutputStream out) throws IOException {

        SanitizeResult.Builder result = new SanitizeResult.Builder();
        long position = startPosition;
        long totalRecords = 0;
        boolean foundEnd = false;

        ByteBuffer recordHeader = ByteBuffer.allocate(RECORD_HEADER_SIZE);
        recordHeader.order(ByteOrder.BIG_ENDIAN);

        while (position < fileSize) {
            // Check if we can read the record header
            long remaining = fileSize - position;
            if (remaining < RECORD_HEADER_SIZE) {
                LOG.warn("Truncated record header at end of file: position={} remaining={}", position, remaining);
                result.truncated();
                break;
            }

            // Read record header
            recordHeader.clear();
            int bytesRead = inChannel.read(recordHeader, position);
            if (bytesRead < RECORD_HEADER_SIZE) {
                LOG.warn("Could not read full record header: position={} bytesRead={}", position, bytesRead);
                result.truncated();
                break;
            }
            recordHeader.flip();

            int tag = recordHeader.get(0) & 0xFF;
            int timestampDelta = recordHeader.getInt(1);
            int declaredLength = recordHeader.getInt(5);

            // Check for unknown tag
            if (!KNOWN_TAGS.contains(tag)) {
                LOG.warn("Unknown record tag, stopping: tag=0x{} position={}", Integer.toHexString(tag), position);
                result.truncated();
                break;
            }

            totalRecords++;

            // Handle HEAP_DUMP_END
            if (tag == TAG_HEAP_DUMP_END) {
                writeRecordHeader(out, tag, timestampDelta, 0);
                foundEnd = true;
                position += RECORD_HEADER_SIZE;
                LOG.debug("Found HEAP_DUMP_END: position={}", position);
                continue;
            }

            // Handle heap dump segments (0x1C) and heap dump (0x0C)
            if (tag == TAG_HEAP_DUMP_SEGMENT || tag == TAG_HEAP_DUMP) {
                position = processHeapSegment(
                        inChannel, position, fileSize, idSize, tag, timestampDelta,
                        declaredLength, out, result);
                continue;
            }

            // Handle all other records
            long bodyLength = Integer.toUnsignedLong(declaredLength);
            long bodyEnd = position + RECORD_HEADER_SIZE + bodyLength;

            if (bodyEnd > fileSize) {
                LOG.warn("Truncated record body: tag={} position={} declaredLength={} available={}",
                        HprofConstants.tagName(tag), position, bodyLength, remaining - RECORD_HEADER_SIZE);
                result.truncated();
                break;
            }

            // Copy record as-is
            writeRecordHeader(out, tag, timestampDelta, declaredLength);
            copyBytes(inChannel, position + RECORD_HEADER_SIZE, bodyLength, out);
            position = bodyEnd;
        }

        // Append HEAP_DUMP_END if missing
        if (!foundEnd) {
            LOG.info("Appending missing HEAP_DUMP_END marker");
            out.write(HEAP_DUMP_END_RECORD);
            result.missingEndMarker();
            totalRecords++;
        }

        out.flush();
        result.totalRecordsProcessed(totalRecords);
        result.totalBytesRead(position);

        return result.build();
    }

    /**
     * Processes a HEAP_DUMP_SEGMENT or HEAP_DUMP record, handling corruption patterns.
     *
     * @return the new file position after this record
     */
    private static long processHeapSegment(
            FileChannel inChannel, long position, long fileSize,
            int idSize, int tag, int timestampDelta, int declaredLength,
            OutputStream out, SanitizeResult.Builder result) throws IOException {

        long remaining = fileSize - position - RECORD_HEADER_SIZE;
        boolean needsScan = false;
        long scanLimit;

        if (declaredLength == 0) {
            // Pattern 1: Zero-length segment — scan forward to determine real length
            LOG.info("Zero-length {} at position {}, scanning sub-records", HprofConstants.tagName(tag), position);
            needsScan = true;
            scanLimit = remaining;
        } else if (declaredLength < 0) {
            // Pattern 5: Overflowed length — treat as needing sub-record scan
            LOG.info("Negative length {} in {} at position {}, scanning sub-records",
                    declaredLength, HprofConstants.tagName(tag), position);
            needsScan = true;
            scanLimit = remaining;
        } else {
            long bodyLength = Integer.toUnsignedLong(declaredLength);
            if (position + RECORD_HEADER_SIZE + bodyLength > fileSize) {
                // Pattern 2: Truncated record body
                LOG.info("Truncated {} at position {}: declared={} available={}",
                        HprofConstants.tagName(tag), position, bodyLength, remaining);
                needsScan = true;
                scanLimit = remaining;
            } else {
                scanLimit = bodyLength;
            }
        }

        if (needsScan) {
            // Scan sub-records to determine valid content length
            long dataStart = position + RECORD_HEADER_SIZE;
            ScanResult scanResult = scanSubRecords(inChannel, dataStart, scanLimit, idSize);

            if (declaredLength == 0) {
                result.zeroLengthSegmentFixed(scanResult.subRecordCount);
            } else if (declaredLength < 0) {
                result.overflowedLength(scanResult.subRecordCount);
            } else if (scanResult.validLength < scanLimit) {
                // Only flag as truncated sub-records when the declared length was valid
                // but internal sub-records were truncated (Pattern 4).
                // For zero-length/overflowed segments, the scan naturally stops before
                // scanLimit (which is the remainder of the file), so this is not truncation.
                result.truncatedSubRecords(scanResult.subRecordCount);
            }

            if (scanResult.validLength > 0) {
                // Write corrected record
                int correctedLength = safeCastToInt(scanResult.validLength);
                writeRecordHeader(out, tag, timestampDelta, correctedLength);
                copyBytes(inChannel, dataStart, scanResult.validLength, out);
                LOG.info("Repaired {} at position {}: correctedLength={} subRecords={}",
                        HprofConstants.tagName(tag), position, scanResult.validLength, scanResult.subRecordCount);
            } else {
                LOG.warn("Skipping empty/invalid {} at position {}", HprofConstants.tagName(tag), position);
            }

            // Advance past whatever data we could identify
            if (declaredLength > 0) {
                long bodyLength = Integer.toUnsignedLong(declaredLength);
                long bodyEnd = position + RECORD_HEADER_SIZE + bodyLength;
                return Math.min(bodyEnd, fileSize);
            }
            return dataStart + scanResult.scanAdvance;
        }

        // Normal segment with valid declared length — validate sub-records within it
        long bodyLength = Integer.toUnsignedLong(declaredLength);
        long dataStart = position + RECORD_HEADER_SIZE;

        ScanResult scanResult = scanSubRecords(inChannel, dataStart, bodyLength, idSize);

        if (scanResult.validLength < bodyLength) {
            // Pattern 4: Truncated sub-records within a segment with declared length
            LOG.info("Truncated sub-records in {} at position {}: declared={} valid={} subRecords={}",
                    HprofConstants.tagName(tag), position, bodyLength, scanResult.validLength, scanResult.subRecordCount);

            int correctedLength = safeCastToInt(scanResult.validLength);
            writeRecordHeader(out, tag, timestampDelta, correctedLength);
            copyBytes(inChannel, dataStart, scanResult.validLength, out);
            result.truncatedSubRecords(scanResult.subRecordCount);
        } else {
            // Segment is valid — copy as-is
            writeRecordHeader(out, tag, timestampDelta, declaredLength);
            copyBytes(inChannel, dataStart, bodyLength, out);
        }

        return position + RECORD_HEADER_SIZE + bodyLength;
    }

    /**
     * Scans sub-records within a heap dump segment body to determine how many
     * bytes of valid sub-record data it contains.
     *
     * @param inChannel the file channel to read from
     * @param dataStart absolute file position of the segment body start
     * @param limit     maximum bytes to scan
     * @param idSize    HPROF identifier size (4 or 8)
     * @return scan result with valid length and count
     */
    private static ScanResult scanSubRecords(
            FileChannel inChannel, long dataStart, long limit, int idSize) throws IOException {

        long validLength = 0;
        long subRecordCount = 0;
        long scanPos = 0;

        // Read in chunks for sub-record scanning
        int bufferSize = (int) Math.min(limit, MAX_SEGMENT_SCAN_BUFFER);
        ByteBuffer buffer = ByteBuffer.allocate(bufferSize);
        buffer.order(ByteOrder.BIG_ENDIAN);

        long bufferFileStart = dataStart;
        int bytesInBuffer = 0;

        // Initial fill
        buffer.clear();
        bytesInBuffer = readFully(inChannel, buffer, bufferFileStart);
        buffer.flip();

        while (scanPos < limit) {
            long remainingInSegment = limit - scanPos;

            // Check if we need to refill the buffer
            int posInBuffer = (int) (scanPos - (bufferFileStart - dataStart));
            if (posInBuffer >= bytesInBuffer) {
                // Refill buffer
                bufferFileStart = dataStart + scanPos;
                buffer.clear();
                bytesInBuffer = readFully(inChannel, buffer, bufferFileStart);
                buffer.flip();
                posInBuffer = 0;

                if (bytesInBuffer == 0) {
                    break; // EOF
                }
            }

            int availableInBuffer = bytesInBuffer - posInBuffer;
            if (availableInBuffer < 1) {
                break;
            }

            // Read sub-tag
            int subTag = buffer.get(posInBuffer) & 0xFF;

            // Position buffer after the sub-tag for SubRecordSizer
            buffer.position(posInBuffer + 1);
            long remainingForSizer = Math.min(remainingInSegment - 1, availableInBuffer - 1);

            if (remainingForSizer <= 0) {
                break;
            }

            long subRecordBodySize = SubRecordSizer.computeSize(subTag, idSize, buffer, remainingForSizer);
            if (subRecordBodySize < 0) {
                // Unknown sub-tag or insufficient data — stop here
                break;
            }

            long totalSubRecordSize = 1 + subRecordBodySize; // sub-tag byte + body
            if (scanPos + totalSubRecordSize > limit) {
                break; // Would extend beyond segment boundary
            }

            scanPos += totalSubRecordSize;
            validLength = scanPos;
            subRecordCount++;
        }

        return new ScanResult(validLength, subRecordCount, scanPos);
    }

    /**
     * Result of scanning sub-records within a segment.
     */
    private record ScanResult(long validLength, long subRecordCount, long scanAdvance) {
    }

    // --- Header parsing helpers ---

    /**
     * Parses the HPROF header length by finding the null terminator of the version string.
     *
     * @return the total header length (version string + null + idSize + timestamp), or -1 if invalid
     */
    private static int parseHeaderLength(FileChannel channel) throws IOException {
        // Read enough for the version string + some margin
        ByteBuffer headerBuf = ByteBuffer.allocate(64);
        int read = channel.read(headerBuf, 0);
        if (read < MIN_HEADER_SIZE) {
            return -1;
        }
        headerBuf.flip();

        // Find null terminator
        int nullPos = -1;
        for (int i = 0; i < Math.min(read, 32); i++) {
            if (headerBuf.get(i) == 0) {
                nullPos = i;
                break;
            }
        }
        if (nullPos < 0) {
            return -1;
        }

        // Validate version string
        byte[] versionBytes = new byte[nullPos];
        headerBuf.position(0);
        headerBuf.get(versionBytes, 0, nullPos);
        String version = new String(versionBytes, java.nio.charset.StandardCharsets.US_ASCII);
        if (!VERSION_101.equals(version) && !VERSION_102.equals(version)) {
            LOG.warn("Unknown HPROF version: {}", version);
            // Continue anyway — might still be parseable
        }

        // Header = version string + null byte + idSize (4 bytes) + timestamp (8 bytes)
        return nullPos + 1 + 4 + 8;
    }

    /**
     * Reads the identifier size (4 or 8) from the HPROF header.
     */
    private static int parseIdSize(FileChannel channel) throws IOException {
        ByteBuffer headerBuf = ByteBuffer.allocate(64);
        int read = channel.read(headerBuf, 0);
        if (read < MIN_HEADER_SIZE) {
            return -1;
        }
        headerBuf.flip();

        // Find null terminator
        int nullPos = -1;
        for (int i = 0; i < Math.min(read, 32); i++) {
            if (headerBuf.get(i) == 0) {
                nullPos = i;
                break;
            }
        }
        if (nullPos < 0) {
            return -1;
        }

        // idSize is at nullPos + 1, as a big-endian u4
        int idSizeOffset = nullPos + 1;
        if (idSizeOffset + 4 > read) {
            return -1;
        }
        return ((headerBuf.get(idSizeOffset) & 0xFF) << 24)
                | ((headerBuf.get(idSizeOffset + 1) & 0xFF) << 16)
                | ((headerBuf.get(idSizeOffset + 2) & 0xFF) << 8)
                | (headerBuf.get(idSizeOffset + 3) & 0xFF);
    }

    // --- I/O helpers ---

    /**
     * Writes a 9-byte record header to the output stream.
     */
    private static void writeRecordHeader(OutputStream out, int tag, int timestampDelta, int length) throws IOException {
        out.write(tag & 0xFF);
        writeInt(out, timestampDelta);
        writeInt(out, length);
    }

    /**
     * Writes a 4-byte big-endian integer to the output stream.
     */
    private static void writeInt(OutputStream out, int value) throws IOException {
        out.write((value >>> 24) & 0xFF);
        out.write((value >>> 16) & 0xFF);
        out.write((value >>> 8) & 0xFF);
        out.write(value & 0xFF);
    }

    /**
     * Copies bytes from the file channel to the output stream in chunks.
     */
    private static void copyBytes(FileChannel channel, long position, long length, OutputStream out) throws IOException {
        ByteBuffer copyBuf = ByteBuffer.allocate((int) Math.min(IO_BUFFER_SIZE, length));
        long remaining = length;
        long pos = position;

        while (remaining > 0) {
            copyBuf.clear();
            if (remaining < copyBuf.capacity()) {
                copyBuf.limit((int) remaining);
            }
            int read = channel.read(copyBuf, pos);
            if (read <= 0) {
                throw new IOException("Unexpected end of file at position " + pos + ", expected " + remaining + " more bytes");
            }
            copyBuf.flip();
            out.write(copyBuf.array(), copyBuf.position(), copyBuf.remaining());
            pos += read;
            remaining -= read;
        }
    }

    /**
     * Reads as many bytes as possible into the buffer from the channel at the given position.
     *
     * @return the number of bytes read
     */
    private static int readFully(FileChannel channel, ByteBuffer buffer, long position) throws IOException {
        int totalRead = 0;
        long pos = position;
        while (buffer.hasRemaining()) {
            int read = channel.read(buffer, pos);
            if (read <= 0) {
                break;
            }
            totalRead += read;
            pos += read;
        }
        return totalRead;
    }

    /**
     * Casts a long to int for use as an HPROF record length.
     * For segments exceeding Integer.MAX_VALUE, the length field will overflow —
     * this is the expected HPROF format behavior for very large segments.
     */
    private static int safeCastToInt(long value) {
        return (int) value;
    }
}
