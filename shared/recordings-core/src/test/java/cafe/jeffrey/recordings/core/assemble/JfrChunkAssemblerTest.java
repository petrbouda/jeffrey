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

package cafe.jeffrey.recordings.core.assemble;

import cafe.jeffrey.shared.common.compression.Lz4Compressor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JfrChunkAssemblerTest {

    @TempDir
    Path dir;

    /**
     * A recording is its chunks back to back; a chunk the hub already compressed is decompressed
     * first, so raw and compressed chunks mix in one recording, which is compressed once.
     */
    @Test
    void concatenatesRawAndCompressedChunksOldestFirst() throws IOException {
        Path first = Files.writeString(dir.resolve("profile-1.jfr"), "first-");
        Path secondRaw = Files.writeString(dir.resolve("second.jfr"), "second-");
        Path second = Lz4Compressor.compress(secondRaw, dir.resolve("profile-2.jfr.lz4"));
        Path third = Files.writeString(dir.resolve("profile-3.jfr"), "third");
        Path work = Files.createDirectory(dir.resolve("work"));

        Path recording = JfrChunkAssembler.assemble(List.of(first, second, third), work, "session");

        assertEquals(work.resolve("session.jfr.lz4"), recording);
        assertTrue(Lz4Compressor.isLz4Compressed(recording));
        Path plain = dir.resolve("plain.jfr");
        Lz4Compressor.decompress(recording, plain);
        assertArrayEquals("first-second-third".getBytes(StandardCharsets.UTF_8), Files.readAllBytes(plain));
        assertFalse(Files.exists(work.resolve("session.jfr")), "the intermediate raw file is removed");
    }

    /**
     * A chunk is decompressed by what it holds, not by what it is called: one that arrived
     * compressed under a raw name is still a compressed chunk.
     */
    @Test
    void decompressesACompressedChunkWhateverItsName() throws IOException {
        Path raw = Files.writeString(dir.resolve("raw.jfr"), "content");
        Path misnamed = Lz4Compressor.compress(raw, dir.resolve("misnamed.lz4"));
        Files.move(misnamed, dir.resolve("profile-1.jfr"));
        Path work = Files.createDirectory(dir.resolve("work"));

        Path recording = JfrChunkAssembler.assemble(List.of(dir.resolve("profile-1.jfr")), work, "session");

        Path plain = dir.resolve("plain.jfr");
        Lz4Compressor.decompress(recording, plain);
        assertEquals("content", Files.readString(plain));
    }

    @Test
    void refusesChunksThatAddUpToNothing() throws IOException {
        Path empty = Files.createFile(dir.resolve("profile-1.jfr"));
        Path work = Files.createDirectory(dir.resolve("work"));

        assertThrows(IllegalArgumentException.class,
                () -> JfrChunkAssembler.assemble(List.of(empty), work, "session"));
        assertFalse(Files.exists(work.resolve("session.jfr")));
    }

    @Test
    void refusesNothingToAssemble() {
        assertThrows(IllegalArgumentException.class,
                () -> JfrChunkAssembler.assemble(List.of(), dir, "session"));
    }
}
