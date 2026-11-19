/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
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

package cafe.jeffrey.microscope.core;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The JFR chunks a recording file holds, one after another, cut where each chunk's header says it
 * ends — what {@code jfr disassemble --max-chunks 1} writes, done in memory. A chunk is a complete
 * JFR file on its own.
 */
final class JfrFileChunks {

    private static final byte[] MAGIC = {'F', 'L', 'R', 0};
    private static final int HEADER_SIZE = 68;
    private static final int SIZE_OFFSET = 8;
    private static final int START_NANOS_OFFSET = 32;
    private static final long NANOS_PER_SECOND = 1_000_000_000L;

    private JfrFileChunks() {
    }

    /** Every chunk of the file, in the order the file holds them. */
    static List<byte[]> split(byte[] file) {
        ByteBuffer buffer = ByteBuffer.wrap(file);
        List<byte[]> chunks = new ArrayList<>();
        int position = 0;
        while (position < file.length) {
            if (file.length - position < HEADER_SIZE
                    || !Arrays.equals(Arrays.copyOfRange(file, position, position + MAGIC.length), MAGIC)) {
                throw new IllegalArgumentException("No JFR chunk header at offset " + position);
            }
            long size = buffer.getLong(position + SIZE_OFFSET);
            if (size < HEADER_SIZE || position + size > file.length) {
                throw new IllegalArgumentException("A JFR chunk at offset " + position + " declares " + size + " bytes");
            }
            chunks.add(Arrays.copyOfRange(file, position, position + (int) size));
            position += (int) size;
        }
        return chunks;
    }

    /** When the chunk's recording began, as its header states it. */
    static Instant startOf(byte[] chunk) {
        long nanos = ByteBuffer.wrap(chunk).getLong(START_NANOS_OFFSET);
        return Instant.ofEpochSecond(nanos / NANOS_PER_SECOND, nanos % NANOS_PER_SECOND);
    }
}
