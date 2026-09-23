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

package cafe.jeffrey.profile.parser;

import cafe.jeffrey.provider.profile.api.RecordingSources;
import cafe.jeffrey.shared.common.compression.Lz4Compressor;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

/**
 * How a recording's files are turned into the files that are actually parsed.
 * <p>
 * Parsing fans out over independent files, so the only question is how many of them there are.
 * A recording that arrives as one file has to be split into its chunks or the parse is
 * single-threaded; a recording that already arrives as dozens of files does not, and splitting it
 * would be a full read and a full write of every byte to buy parallelism that is already there.
 * <p>
 * Splitting is therefore a way of <em>manufacturing</em> parse units, not a step the format needs.
 */
sealed interface SourceParseMode permits ChunkedSources, WholeFileSources {

    /**
     * Turns one source file into the files to parse, handing each to {@code onUnit} as soon as it
     * exists — so a caller can start parsing the first one while the rest are still being produced.
     *
     * @param scratchDir a directory of this source's own, for whatever the mode has to write
     */
    void expand(Path source, Path scratchDir, Consumer<Path> onUnit);

    /**
     * Picks the mode for a whole recording.
     * <p>
     * Whole files once there are at least {@code threshold} of them: past that point every worker
     * has a file of its own and splitting only adds I/O. Below it, splitting is what gives the
     * parse anything to spread across.
     * <p>
     * A compressed source is always split, whatever the count. {@code EventStream} cannot open an
     * LZ4 file, so it has to be decompressed either way, and splitting decompresses and writes the
     * pieces in the same single pass — the same cost as decompressing it whole, for more parse
     * units. The test is over all the files because a recording is compressed or it is not; a set
     * with one compressed file in it is not a shape the profiler produces, and treating it as
     * compressed is the safe reading.
     */
    static SourceParseMode of(RecordingSources sources, int threshold, Lz4Compressor lz4Compressor) {
        List<Path> files = sources.files();
        boolean anyCompressed = files.stream().anyMatch(Lz4Compressor::isLz4Compressed);
        if (files.size() >= threshold && !anyCompressed) {
            return new WholeFileSources();
        }
        return new ChunkedSources(lz4Compressor);
    }
}
