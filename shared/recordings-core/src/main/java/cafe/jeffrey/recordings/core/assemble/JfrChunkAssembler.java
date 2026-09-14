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
import cafe.jeffrey.shared.common.exception.Exceptions;
import cafe.jeffrey.shared.common.filesystem.FileSystemUtils;
import cafe.jeffrey.shared.common.model.repository.FileExtensions;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Assembles the JFR chunks downloaded from a hub session into the one recording a profile is
 * built from.
 *
 * <p>A JFR file is a sequence of self-contained chunks, so concatenating several chunk files
 * yields one valid recording. Each chunk is written into a single {@code .jfr.lz4} frame as it
 * is read — decompressed first when the hub had already compressed it — so the recording is
 * written once, not raw and then compressed again. This runs here, on the machine that consumes
 * the recording, rather than on the hub: the hub only serves files, and the profile pipeline
 * splits the recording back into chunks anyway.
 */
public final class JfrChunkAssembler {

    private static final String EXTENSION_SEPARATOR = ".";

    private JfrChunkAssembler() {
    }

    /**
     * @param chunks   the chunks oldest first, raw {@code .jfr} or {@code .jfr.lz4}
     * @param workDir  directory the recording is written to
     * @param baseName name of the recording without its extension
     * @return the assembled {@code <baseName>.jfr.lz4}
     * @throws IllegalArgumentException when there is nothing to assemble, or the chunks add up
     *                                  to an empty recording
     */
    public static Path assemble(List<Path> chunks, Path workDir, String baseName) {
        if (chunks.isEmpty()) {
            throw new IllegalArgumentException("No chunks to assemble: baseName=" + baseName);
        }

        Path recording = workDir.resolve(baseName + EXTENSION_SEPARATOR + FileExtensions.JFR_LZ4);
        long assembledBytes;
        try (CountingOutputStream out = new CountingOutputStream(Lz4Compressor.compressStream(recording))) {
            for (Path chunk : chunks) {
                // By content rather than by name: the chunk arrived over the wire under whatever
                // name the hub listed it by, and a wrongly named chunk would otherwise be copied
                // still compressed into a file that claims to be raw.
                if (Lz4Compressor.startsWithLz4Frame(chunk)) {
                    Lz4Compressor.decompressTo(chunk, out);
                } else {
                    Files.copy(chunk, out);
                }
            }
            assembledBytes = out.written();
        } catch (IOException | RuntimeException e) {
            // A half-written recording is worse than none: the next attempt would find a file
            // at the path and nothing would say it is incomplete.
            FileSystemUtils.removeFile(recording);
            throw Exceptions.compressionError(
                    "Failed to assemble JFR chunks: baseName=" + baseName + " chunks=" + chunks, e);
        }

        if (assembledBytes <= 0) {
            FileSystemUtils.removeFile(recording);
            throw new IllegalArgumentException("Assembled recording is empty: baseName=" + baseName + " chunks=" + chunks);
        }
        return recording;
    }

    /**
     * Counts what passes through, because the compressed file's size says nothing about whether
     * anything was written into it: an empty frame still has a header.
     */
    private static final class CountingOutputStream extends FilterOutputStream {

        private long written;

        private CountingOutputStream(OutputStream out) {
            super(out);
        }

        long written() {
            return written;
        }

        @Override
        public void write(int b) throws IOException {
            out.write(b);
            written++;
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            out.write(b, off, len);
            written += len;
        }
    }
}
