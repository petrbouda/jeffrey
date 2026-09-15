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

package cafe.jeffrey.profile.parser;

import cafe.jeffrey.provider.profile.api.RecordingSources;
import cafe.jeffrey.shared.common.compression.Lz4Compressor;
import cafe.jeffrey.shared.common.filesystem.TempDirFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

@DisplayName("SourceParseMode")
class SourceParseModeTest {

    private static final int THRESHOLD = 4;

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("splits into chunks while there are fewer files than the threshold")
    void splitsBelowTheThreshold() {
        assertInstanceOf(ChunkedSources.class, mode(plainFiles(THRESHOLD - 1)));
    }

    @Test
    @DisplayName("parses whole files once there are at least the threshold of them")
    void parsesWholeFilesAtTheThreshold() {
        assertInstanceOf(WholeFileSources.class, mode(plainFiles(THRESHOLD)));
    }

    @Test
    @DisplayName("parses whole files past the threshold")
    void parsesWholeFilesPastTheThreshold() {
        assertInstanceOf(WholeFileSources.class, mode(plainFiles(THRESHOLD * 3)));
    }

    /**
     * A single uploaded recording is the case the split exists for: one file is one parse unit, and
     * without splitting it the parse would be single-threaded however many cores are free.
     */
    @Test
    @DisplayName("splits a lone recording")
    void splitsALoneRecording() {
        assertInstanceOf(ChunkedSources.class, mode(plainFiles(1)));
    }

    /**
     * EventStream cannot open an LZ4 file, so a compressed source has to be decompressed whatever
     * the count — and splitting decompresses and writes the pieces in the same single pass, for
     * more parse units at the same cost.
     */
    @Test
    @DisplayName("splits compressed files however many there are")
    void splitsCompressedFilesRegardlessOfCount() {
        assertInstanceOf(ChunkedSources.class, mode(compressedFiles(THRESHOLD * 3)));
    }

    /**
     * A recording is compressed or it is not; a mixed set is not a shape the profiler produces, and
     * reading it as compressed is what keeps a file EventStream cannot open out of whole-file mode.
     */
    @Test
    @DisplayName("splits a mixed set rather than handing a compressed file to EventStream")
    void splitsAMixedSet() {
        List<Path> mixed = new ArrayList<>(plainFiles(THRESHOLD * 2).files());
        mixed.add(tempDir.resolve("late.jfr.lz4"));

        assertInstanceOf(ChunkedSources.class, mode(new RecordingSources(mixed)));
    }

    private SourceParseMode mode(RecordingSources sources) {
        TempDirFactory tempDirFactory = TempDirFactory.of(tempDir);
        return SourceParseMode.of(sources, THRESHOLD, new Lz4Compressor(tempDirFactory));
    }

    private RecordingSources plainFiles(int count) {
        return files(count, ".jfr");
    }

    private RecordingSources compressedFiles(int count) {
        return files(count, ".jfr.lz4");
    }

    private RecordingSources files(int count, String extension) {
        return new RecordingSources(IntStream.range(0, count)
                .mapToObj(index -> tempDir.resolve("recording-" + index + extension))
                .toList());
    }
}
