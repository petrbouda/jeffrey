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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Sanitizes corrupted HPROF heap dump files by detecting and fixing common
 * structural issues caused by ungraceful JVM shutdowns (OOMKill, SIGKILL, crash).
 * <p>
 * The sanitizer handles five corruption patterns:
 * <ol>
 *   <li>Zero-length HEAP_DUMP_SEGMENT records - rescans to compute actual length</li>
 *   <li>Truncated file mid-record - truncates to last complete record</li>
 *   <li>Missing HEAP_DUMP_END marker - appends one</li>
 *   <li>Truncated sub-records within segments - trims to last valid sub-record</li>
 *   <li>Overflowed segment lengths - rescans to find actual valid length</li>
 * </ol>
 */
public final class HprofSanitizer {

    private static final Logger LOG = LoggerFactory.getLogger(HprofSanitizer.class);

    private static final int RECORD_HEADER_SIZE = 9;
    private static final int COPY_BUFFER_SIZE = 8 * 1024 * 1024; // 8MB

    private HprofSanitizer() {
    }

    /**
     * Sanitizes an HPROF file, writing the repaired version to the output path.
     *
     * @param input  the corrupted HPROF file
     * @param output the path to write the sanitized file
     * @return the sanitization result with repair metadata
     * @throws IOException if an I/O error occurs
     */
    public static SanitizeResult sanitize(Path input, Path output) throws IOException {
        LOG.info("Starting HPROF sanitization: input={}", input);

        try (FileChannel inChannel = FileChannel.open(input, StandardOpenOption.READ);
             FileChannel outChannel = FileChannel.open(output,
                     StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {

            long fileSize = inChannel.size();
            if (fileSize == 0) {
                throw new IOException("HPROF file is empty");
            }

            // Parse header
            int headerReadSize = (int) Math.min(256, fileSize);
            ByteBuffer headerBuf = ByteBuffer.allocate(headerReadSize);
            headerBuf.order(ByteOrder.BIG_ENDIAN);
            inChannel.position(0);
            readFully(inChannel, headerBuf);
            headerBuf.flip();

            HprofHeader header = HprofHeaderParser.parse(headerBuf);
            int idSize = header.idSize();
            LOG.debug("HPROF header parsed: version={} idSize={} headerSize={}", header.version(), idSize, header.headerSize());

            // Copy header to output
            inChannel.position(0);
            copyBytes(inChannel, outChannel, header.headerSize());

            // Process records
            inChannel.position(header.headerSize());
            ByteBuffer recordBuf = ByteBuffer.allocate(RECORD_HEADER_SIZE);
            recordBuf.order(ByteOrder.BIG_ENDIAN);

            boolean modified = false;
            boolean hadZeroLengthSegments = false;
            boolean wasTruncated = false;
            boolean hadMissingEndMarker = false;
            boolean hadTruncatedSubRecords = false;
            boolean hadOverflowedLengths = false;
            int zeroLengthSegmentsFixed = 0;
            int totalRecordsProcessed = 0;
            long estimatedObjectsRecovered = 0;
            boolean sawEndMarker = false;

            List<String> repairs = new ArrayList<>();

            while (true) {
                long recordOffset = inChannel.position();

                // Check if there's enough data for a record header
                if (recordOffset + RECORD_HEADER_SIZE > fileSize) {
                    if (recordOffset < fileSize) {
                        // Truncated mid-record-header
                        wasTruncated = true;
                        modified = true;
                        repairs.add("Truncated bytes at end of file discarded");
                        LOG.debug("Truncated record header at offset {}", recordOffset);
                    }
                    break;
                }

                HprofRecordReader.RecordHeader recHeader = HprofRecordReader.readHeader(inChannel, recordBuf);
                if (recHeader == null) {
                    break; // EOF
                }

                totalRecordsProcessed++;
                HprofRecordTag tag = HprofRecordTag.fromByte(recHeader.tag());

                if (tag == HprofRecordTag.HEAP_DUMP_END) {
                    sawEndMarker = true;
                    // Write the end marker record header (9 bytes, body length 0)
                    writeRecordHeader(outChannel, recHeader);
                    // Skip any body (should be 0)
                    long bodyLen = recHeader.unsignedBodyLength();
                    if (bodyLen > 0) {
                        skipOrCopy(inChannel, null, bodyLen);
                    }
                    continue;
                }

                long bodyLength = recHeader.unsignedBodyLength();
                long bodyEnd = inChannel.position() + bodyLength;

                if (tag != null && tag.isHeapDumpData()) {
                    // Handle heap dump segments - this is where corruption usually is
                    long availableBody = fileSize - inChannel.position();

                    if (bodyLength == 0) {
                        // Pattern 1: Zero-length segment - rescan to find actual data
                        hadZeroLengthSegments = true;
                        modified = true;
                        zeroLengthSegmentsFixed++;

                        long bodyStart = recHeader.fileOffset() + RECORD_HEADER_SIZE;
                        SubRecordScanner.ScanResult scanResult = SubRecordScanner.scanFromChannel(
                                inChannel, bodyStart, availableBody, idSize);

                        long validBytes = scanResult.validBytes();
                        if (validBytes > 0) {
                            writeRecordHeaderWithLength(outChannel, recHeader, validBytes);
                            inChannel.position(bodyStart);
                            copyBytes(inChannel, outChannel, validBytes);
                            estimatedObjectsRecovered += scanResult.subRecordCount();
                            repairs.add("Zero-length segment rescanned: recovered " + scanResult.subRecordCount() + " objects (" + validBytes + " bytes)");
                            LOG.debug("Fixed zero-length segment at offset {}: {} valid bytes {} sub-records",
                                    recordOffset, validBytes, scanResult.subRecordCount());
                        }
                        // Position channel past the valid bytes we consumed
                        inChannel.position(bodyStart + validBytes);
                        continue;
                    }

                    if (bodyLength > availableBody) {
                        // Pattern 5: Overflowed length - body extends past file end
                        hadOverflowedLengths = true;
                        modified = true;

                        SubRecordScanner.ScanResult scanResult = SubRecordScanner.scanFromChannel(
                                inChannel, inChannel.position(), availableBody, idSize);

                        long validBytes = scanResult.validBytes();
                        if (validBytes > 0) {
                            writeRecordHeaderWithLength(outChannel, recHeader, validBytes);
                            inChannel.position(recHeader.fileOffset() + RECORD_HEADER_SIZE);
                            copyBytes(inChannel, outChannel, validBytes);
                            estimatedObjectsRecovered += scanResult.subRecordCount();
                            repairs.add("Overflowed segment trimmed: " + validBytes + " of " + bodyLength + " bytes valid");
                        }

                        wasTruncated = true;
                        break; // Nothing useful after this
                    }

                    // Normal-length segment: verify sub-records are valid
                    SubRecordScanner.ScanResult scanResult = SubRecordScanner.scanFromChannel(
                            inChannel, inChannel.position(), bodyLength, idSize);

                    if (scanResult.truncated() && scanResult.validBytes() < bodyLength) {
                        // Pattern 4: Truncated sub-records
                        hadTruncatedSubRecords = true;
                        modified = true;

                        long validBytes = scanResult.validBytes();
                        if (validBytes > 0) {
                            writeRecordHeaderWithLength(outChannel, recHeader, validBytes);
                            inChannel.position(recHeader.fileOffset() + RECORD_HEADER_SIZE);
                            copyBytes(inChannel, outChannel, validBytes);
                            estimatedObjectsRecovered += scanResult.subRecordCount();
                            repairs.add("Truncated sub-records in segment trimmed: " + validBytes + " of " + bodyLength + " bytes valid");
                        }

                        // Skip past the declared body
                        inChannel.position(recHeader.fileOffset() + RECORD_HEADER_SIZE + bodyLength);
                    } else {
                        // Segment is valid - copy as-is
                        writeRecordHeader(outChannel, recHeader);
                        inChannel.position(recHeader.fileOffset() + RECORD_HEADER_SIZE);
                        copyBytes(inChannel, outChannel, bodyLength);
                    }
                } else {
                    // Non-heap record - copy as-is if body fits in file
                    if (bodyEnd > fileSize) {
                        // Pattern 2: Truncated at non-heap record
                        wasTruncated = true;
                        modified = true;
                        repairs.add("File truncated mid-record at offset " + recordOffset);
                        break;
                    }

                    writeRecordHeader(outChannel, recHeader);
                    copyBytes(inChannel, outChannel, bodyLength);
                }
            }

            // Pattern 3: Missing end marker
            if (!sawEndMarker) {
                hadMissingEndMarker = true;
                modified = true;
                repairs.add("Missing HEAP_DUMP_END marker appended");
                appendEndMarker(outChannel);
            }

            long totalBytesRead = inChannel.position();
            long totalBytesWritten = outChannel.position();

            String summaryMessage = modified
                    ? "Repairs applied: " + String.join("; ", repairs)
                    : "No repairs needed";

            SanitizeResult result = new SanitizeResult(
                    modified, hadZeroLengthSegments, wasTruncated, hadMissingEndMarker,
                    hadTruncatedSubRecords, hadOverflowedLengths, zeroLengthSegmentsFixed,
                    totalRecordsProcessed, totalBytesRead, totalBytesWritten,
                    estimatedObjectsRecovered, summaryMessage
            );

            LOG.info("HPROF sanitization complete: modified={} records={} bytesRead={} bytesWritten={} summary={}",
                    modified, totalRecordsProcessed, totalBytesRead, totalBytesWritten, summaryMessage);

            return result;
        }
    }

    private static void writeRecordHeader(FileChannel outChannel, HprofRecordReader.RecordHeader header) throws IOException {
        ByteBuffer buf = ByteBuffer.allocate(RECORD_HEADER_SIZE);
        buf.order(ByteOrder.BIG_ENDIAN);
        buf.put((byte) header.tag());
        buf.putInt(header.timestampDelta());
        buf.putInt(header.bodyLength());
        buf.flip();
        writeFully(outChannel, buf);
    }

    private static void writeRecordHeaderWithLength(FileChannel outChannel, HprofRecordReader.RecordHeader header, long bodyLength) throws IOException {
        ByteBuffer buf = ByteBuffer.allocate(RECORD_HEADER_SIZE);
        buf.order(ByteOrder.BIG_ENDIAN);
        buf.put((byte) header.tag());
        buf.putInt(header.timestampDelta());
        buf.putInt((int) bodyLength);
        buf.flip();
        writeFully(outChannel, buf);
    }

    private static void appendEndMarker(FileChannel outChannel) throws IOException {
        ByteBuffer buf = ByteBuffer.allocate(RECORD_HEADER_SIZE);
        buf.order(ByteOrder.BIG_ENDIAN);
        buf.put((byte) HprofRecordTag.HEAP_DUMP_END.value());
        buf.putInt(0); // timestamp delta
        buf.putInt(0); // body length
        buf.flip();
        writeFully(outChannel, buf);
    }

    private static void copyBytes(FileChannel src, FileChannel dst, long count) throws IOException {
        long remaining = count;
        while (remaining > 0) {
            long transferred = src.transferTo(src.position(), remaining, dst);
            if (transferred == 0) {
                throw new IOException("Failed to transfer bytes");
            }
            src.position(src.position() + transferred);
            remaining -= transferred;
        }
    }

    private static void skipOrCopy(FileChannel channel, FileChannel outChannel, long count) throws IOException {
        if (outChannel != null) {
            copyBytes(channel, outChannel, count);
        } else {
            channel.position(channel.position() + count);
        }
    }

    private static void readFully(FileChannel channel, ByteBuffer buffer) throws IOException {
        while (buffer.hasRemaining()) {
            int read = channel.read(buffer);
            if (read < 0) {
                throw new IOException("Unexpected end of file");
            }
        }
    }

    private static void writeFully(FileChannel channel, ByteBuffer buffer) throws IOException {
        while (buffer.hasRemaining()) {
            channel.write(buffer);
        }
    }
}
