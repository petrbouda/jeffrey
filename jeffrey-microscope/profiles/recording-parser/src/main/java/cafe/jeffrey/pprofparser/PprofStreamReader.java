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
