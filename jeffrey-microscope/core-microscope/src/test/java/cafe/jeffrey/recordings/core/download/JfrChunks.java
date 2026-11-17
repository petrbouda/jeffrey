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

import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * JFR files as far as their chunk headers go: a header naming the chunk's size and duration,
 * followed by whatever the chunk holds. Enough for {@link JfrChunkChain}, which reads nothing else.
 */
public final class JfrChunks {

    private static final byte[] MAGIC = {'F', 'L', 'R', 0};
    private static final int HEADER_SIZE = 68;
    private static final int SIZE_OFFSET = 8;
    private static final int DURATION_OFFSET = 40;
    private static final long ONE_SECOND_IN_NANOS = 1_000_000_000L;
    private static final long ASYNC_PROFILER_PLACEHOLDER_SIZE = 1024L * 1024L * 1024L;

    private JfrChunks() {
    }

    /** One finished chunk holding {@code content}. */
    public static byte[] whole(byte[] content) {
        return chunk(HEADER_SIZE + content.length, ONE_SECOND_IN_NANOS, content);
    }

    /** Finished chunks one after another, as the JDK and async-profiler leave them in one file. */
    public static byte[] concatenated(byte[]... chunks) {
        ByteBuffer joined = ByteBuffer.allocate(Arrays.stream(chunks).mapToInt(chunk -> chunk.length).sum());
        for (byte[] chunk : chunks) {
            joined.put(chunk);
        }
        return joined.array();
    }

    /** A chunk whose header says it is longer than the bytes that follow: a copy taken mid-write. */
    public static byte[] cutShort() {
        byte[] whole = whole(new byte[256]);
        return Arrays.copyOf(whole, whole.length - 100);
    }

    /** A chunk async-profiler has started and not finished: its header still holds the placeholders. */
    public static byte[] unfinished() {
        return chunk(ASYNC_PROFILER_PLACEHOLDER_SIZE, 0, new byte[0]);
    }

    private static byte[] chunk(long declaredSize, long durationNanos, byte[] content) {
        ByteBuffer chunk = ByteBuffer.allocate(HEADER_SIZE + content.length);
        chunk.put(MAGIC);
        chunk.putLong(SIZE_OFFSET, declaredSize);
        chunk.putLong(DURATION_OFFSET, durationNanos);
        chunk.put(HEADER_SIZE, content);
        return chunk.array();
    }
}
