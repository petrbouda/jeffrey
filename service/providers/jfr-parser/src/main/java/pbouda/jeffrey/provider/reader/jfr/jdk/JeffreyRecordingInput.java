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

import java.io.DataInput;
import java.io.EOFException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * JDK-inspired RecordingInput implementation for robust JFR file reading.
 * Based on jdk.jfr.internal.consumer.RecordingInput with Jeffrey-specific adaptations.
 */
public final class JeffreyRecordingInput implements DataInput, AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(JeffreyRecordingInput.class);
    private static final int DEFAULT_BLOCK_SIZE = 64_000;

    /**
     * Internal block cache for efficient file reading
     */
    private static final class Block {
        private byte[] bytes = new byte[0];
        private long blockPosition;
        private long blockPositionEnd;

        boolean contains(long position) {
            return position >= blockPosition && position < blockPositionEnd;
        }

        public void read(RandomAccessFile file, int amount) throws IOException {
            blockPosition = file.getFilePointer();
            if (amount > bytes.length) {
                bytes = new byte[amount];
            }
            this.blockPositionEnd = blockPosition + amount;
            file.readFully(bytes, 0, amount);
        }

        public byte get(long position) {
            return bytes[(int) (position - blockPosition)];
        }

        public void reset() {
            blockPosition = 0;
            blockPositionEnd = 0;
        }
    }

    private final int blockSize;
    private RandomAccessFile file;
    private String filename;
    private Block currentBlock = new Block();
    private Block previousBlock = new Block();
    private long position;
    private long fileSize;
    private long validSize = -1; // Size up to which data is considered valid

    public JeffreyRecordingInput(Path filePath) throws IOException {
        this(filePath, DEFAULT_BLOCK_SIZE);
    }

    public JeffreyRecordingInput(Path filePath, int blockSize) throws IOException {
        this.blockSize = blockSize;
        initialize(filePath);
    }

    private void initialize(Path filePath) throws IOException {
        this.filename = filePath.toAbsolutePath().toString();
        this.file = new RandomAccessFile(filePath.toFile(), "r");
        this.position = 0;
        this.fileSize = Files.size(filePath);
        this.validSize = -1;
        this.currentBlock.reset();
        this.previousBlock.reset();
        
        if (fileSize < 8) {
            throw new IOException("Not a valid Flight Recorder file. File length is only " + fileSize + " bytes.");
        }
        
        LOG.debug("Initialized JFR input: file={} size={}", filename, fileSize);
    }

    public long getFileSize() {
        return fileSize;
    }

    public String getFilename() {
        return filename;
    }

    public void setValidSize(long size) {
        this.validSize = size;
        LOG.trace("Set valid size to: {}", size);
    }

    public long size() {
        return validSize != -1 ? validSize : fileSize;
    }

    public void position(long newPosition) throws IOException {
        if (newPosition < 0 || newPosition > size()) {
            throw new IOException("Position out of bounds: " + newPosition + " (size: " + size() + ")");
        }
        this.position = newPosition;
        LOG.trace("Position set to: {}", newPosition);
    }

    public long position() {
        return position;
    }

    public void positionPhysical(long newPosition) throws IOException {
        if (newPosition < 0 || newPosition > fileSize) {
            throw new IOException("Physical position out of bounds: " + newPosition);
        }
        file.seek(newPosition);
        LOG.trace("Physical position set to: {}", newPosition);
    }

    public byte readPhysicalByte() throws IOException {
        return file.readByte();
    }

    public long readPhysicalLong() throws IOException {
        return file.readLong();
    }

    public short readRawShort() throws IOException {
        ensureAvailable(2);
        short value = (short) (((readByte0() & 0xFF) << 8) | (readByte0() & 0xFF));
        LOG.trace("Read raw short: {}", value);
        return value;
    }

    public long readRawLong() throws IOException {
        ensureAvailable(8);
        long value = 0;
        for (int i = 0; i < 8; i++) {
            value = (value << 8) | (readByte0() & 0xFF);
        }
        LOG.trace("Read raw long: {}", value);
        return value;
    }

    public void skipBytesVoid(int bytes) throws IOException {
        ensureAvailable(bytes);
        position += bytes;
        LOG.trace("Skipped {} bytes, position now: {}", bytes, position);
    }

    private void ensureAvailable(int bytes) throws IOException {
        if (position + bytes > size()) {
            throw new EOFException("Unexpected end of file at position: " + position + ", requested: " + bytes);
        }
    }

    private byte readByte0() throws IOException {
        if (!currentBlock.contains(position)) {
            // Swap blocks for better cache locality
            Block temp = currentBlock;
            currentBlock = previousBlock;
            previousBlock = temp;

            if (!currentBlock.contains(position)) {
                // Need to read new block
                long blockStart = position - (position % blockSize);
                int readSize = (int) Math.min(blockSize, size() - blockStart);
                
                file.seek(blockStart);
                currentBlock.read(file, readSize);
                LOG.trace("Read new block: start={} size={}", blockStart, readSize);
            }
        }
        return currentBlock.get(position++);
    }

    // DataInput implementation
    @Override
    public void readFully(byte[] b) throws IOException {
        readFully(b, 0, b.length);
    }

    @Override
    public void readFully(byte[] b, int off, int len) throws IOException {
        ensureAvailable(len);
        for (int i = 0; i < len; i++) {
            b[off + i] = readByte0();
        }
    }

    @Override
    public int skipBytes(int n) throws IOException {
        int skipped = (int) Math.min(n, size() - position);
        position += skipped;
        return skipped;
    }

    @Override
    public boolean readBoolean() throws IOException {
        return readByte() != 0;
    }

    @Override
    public byte readByte() throws IOException {
        ensureAvailable(1);
        return readByte0();
    }

    @Override
    public int readUnsignedByte() throws IOException {
        return readByte() & 0xFF;
    }

    @Override
    public short readShort() throws IOException {
        return readRawShort();
    }

    @Override
    public int readUnsignedShort() throws IOException {
        return readRawShort() & 0xFFFF;
    }

    @Override
    public char readChar() throws IOException {
        return (char) readRawShort();
    }

    @Override
    public int readInt() throws IOException {
        ensureAvailable(4);
        int value = 0;
        for (int i = 0; i < 4; i++) {
            value = (value << 8) | (readByte0() & 0xFF);
        }
        return value;
    }

    @Override
    public long readLong() throws IOException {
        return readRawLong();
    }

    @Override
    public float readFloat() throws IOException {
        return Float.intBitsToFloat(readInt());
    }

    @Override
    public double readDouble() throws IOException {
        return Double.longBitsToDouble(readLong());
    }

    @Override
    public String readLine() throws IOException {
        throw new UnsupportedOperationException("readLine not supported");
    }

    @Override
    public String readUTF() throws IOException {
        throw new UnsupportedOperationException("readUTF not supported");
    }

    @Override
    public void close() throws IOException {
        if (file != null) {
            file.close();
            LOG.debug("Closed JFR input: {}", filename);
        }
    }

    /**
     * No-op method for JDK compatibility (streaming mode not needed in Jeffrey)
     */
    public void pollWait() {
        // Jeffrey processes static files, no waiting needed
    }
}