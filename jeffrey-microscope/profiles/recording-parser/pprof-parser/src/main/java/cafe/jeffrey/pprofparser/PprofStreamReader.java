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

package cafe.jeffrey.pprofparser;

import com.google.perftools.profiles.ProfileProto.Profile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPInputStream;

/**
 * Reads a pprof recording ({@code perftools.profiles.Profile}) from disk. pprof payloads are
 * conventionally gzip-compressed protobuf (e.g. Go's {@code runtime/pprof} output, {@code .pprof} /
 * {@code .pb.gz}); this reader transparently decompresses gzip and also accepts raw protobuf.
 */
public class PprofStreamReader {

    private static final int GZIP_MAGIC_BYTE_1 = 0x1f;
    private static final int GZIP_MAGIC_BYTE_2 = 0x8b;

    public Profile read(Path file) {
        try {
            byte[] bytes = Files.readAllBytes(file);
            byte[] protobuf = isGzip(bytes) ? gunzip(bytes) : bytes;
            return Profile.parseFrom(protobuf);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read pprof recording: " + file, e);
        }
    }

    private static boolean isGzip(byte[] bytes) {
        return bytes.length >= 2
                && (bytes[0] & 0xFF) == GZIP_MAGIC_BYTE_1
                && (bytes[1] & 0xFF) == GZIP_MAGIC_BYTE_2;
    }

    private static byte[] gunzip(byte[] bytes) throws IOException {
        try (InputStream input = new GZIPInputStream(new ByteArrayInputStream(bytes))) {
            return input.readAllBytes();
        }
    }
}
