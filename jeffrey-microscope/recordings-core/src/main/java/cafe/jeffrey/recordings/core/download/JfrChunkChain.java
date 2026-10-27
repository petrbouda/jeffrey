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

package cafe.jeffrey.recordings.core.download;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;

/**
 * Whether a JFR file arrived whole, read off its chunk headers alone.
 *
 * <p>The profiler writes a recording whole when its loop ends — with {@code jfrsync} the JDK copies
 * its chunks into the file and async-profiler appends its own after them — so a file taken while
 * that copy is under way is a prefix of itself. Every chunk starts with a header naming its own
 * size, so a whole file is a chain of headers, each size leading exactly to the next header and
 * the last to the end of the file. async-profiler writes a chunk's header with a placeholder size
 * of 1 GiB and no duration, patching both in only when it finishes the chunk; a header still
 * holding them belongs to a chunk the file does not hold the end of.
 *
 * <p>What the chain cannot see is a copy cut exactly at a chunk boundary — the JDK's chunks in
 * place and async-profiler's not appended yet: that prefix is whole JFR. Microscope therefore
 * never hands such a file back from an earlier download ({@code DownloadKey.mayHaveChanged}).
 */
public final class JfrChunkChain {

    private static final byte[] MAGIC = {'F', 'L', 'R', 0};

    /** Magic, version, size, offsets, start, duration, ticks, frequency and features. */
    private static final int HEADER_SIZE = 68;
    private static final int SIZE_OFFSET = 8;
    private static final int DURATION_OFFSET = 40;

    /** What async-profiler writes as a chunk's size until it finishes the chunk. */
    private static final long UNFINISHED_ASYNC_PROFILER_SIZE = 1024L * 1024L * 1024L;

    private JfrChunkChain() {
    }

    /**
     * Whether {@code file} is a whole JFR recording: one or more finished chunks that end exactly
     * where the file does. An empty file, one that is not JFR at all, and one cut short are not.
     *
     * @throws IOException when the file cannot be read
     */
    public static boolean isComplete(Path file) throws IOException {
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ)) {
            long fileSize = channel.size();
            if (fileSize == 0) {
                return false;
            }
            ByteBuffer header = ByteBuffer.allocate(HEADER_SIZE);
            long position = 0;
            while (position < fileSize) {
                if (fileSize - position < HEADER_SIZE) {
                    return false;
                }
                header.clear();
                while (header.hasRemaining()) {
                    if (channel.read(header, position + header.position()) < 0) {
                        return false;
                    }
                }
                if (!startsWithMagic(header)) {
                    return false;
                }
                long chunkSize = header.getLong(SIZE_OFFSET);
                long duration = header.getLong(DURATION_OFFSET);
                boolean unfinished = chunkSize == UNFINISHED_ASYNC_PROFILER_SIZE && duration == 0;
                if (unfinished || chunkSize < HEADER_SIZE || chunkSize > fileSize - position) {
                    return false;
                }
                position += chunkSize;
            }
            return true;
        }
    }

    private static boolean startsWithMagic(ByteBuffer header) {
        return Arrays.equals(header.array(), 0, MAGIC.length, MAGIC, 0, MAGIC.length);
    }
}
