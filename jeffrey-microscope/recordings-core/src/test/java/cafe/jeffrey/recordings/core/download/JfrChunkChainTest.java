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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A JFR file is whole when its chunk headers chain to its end: what decides whether a recording
 * taken off the volume is kept or asked for again.
 */
class JfrChunkChainTest {

    @TempDir
    Path dir;

    private boolean isComplete(byte[] content) throws IOException {
        Path file = Files.write(dir.resolve("profile.jfr"), content);
        return JfrChunkChain.isComplete(file);
    }

    @Test
    void oneFinishedChunkIsWhole() throws IOException {
        assertTrue(isComplete(JfrChunks.whole("events".getBytes(StandardCharsets.UTF_8))));
    }

    /** With jfrsync the JDK's chunks come first and async-profiler's own follows them. */
    @Test
    void severalChunksOneAfterAnotherAreWhole() throws IOException {
        assertTrue(isComplete(JfrChunks.concatenated(
                JfrChunks.whole(new byte[512]), JfrChunks.whole(new byte[64]), JfrChunks.whole(new byte[0]))));
    }

    @Test
    void aChunkCutShortIsNot() throws IOException {
        assertFalse(isComplete(JfrChunks.cutShort()));
    }

    @Test
    void aWholeChunkFollowedByAPartOfTheNextIsNot() throws IOException {
        byte[] second = JfrChunks.whole(new byte[128]);
        assertFalse(isComplete(JfrChunks.concatenated(JfrChunks.whole(new byte[32]), Arrays.copyOf(second, 40))));
    }

    @Test
    void aChunkAsyncProfilerHasNotFinishedIsNot() throws IOException {
        assertFalse(isComplete(JfrChunks.concatenated(JfrChunks.whole(new byte[32]), JfrChunks.unfinished())));
    }

    @Test
    void anEmptyFileAndOneThatIsNotJfrAreNot() throws IOException {
        assertFalse(isComplete(new byte[0]));
        assertFalse(isComplete("not a recording at all, just text that is long enough to hold a header"
                .getBytes(StandardCharsets.UTF_8)));
    }
}
