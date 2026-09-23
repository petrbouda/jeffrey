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

package cafe.jeffrey.otlpparser;

import io.opentelemetry.proto.profiles.v1development.ProfilesData;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Writes {@code .otlp} test files in both variants of Jeffrey's file convention.
 */
public final class OtlpTestFiles {

    private OtlpTestFiles() {
    }

    public static void writeFramed(Path file, List<ProfilesData> frames) {
        writeFramed(file, frames, OtlpFileFormat.VERSION);
    }

    public static void writeFramed(Path file, List<ProfilesData> frames, int version) {
        try (OutputStream output = Files.newOutputStream(file)) {
            output.write(OtlpFileFormat.MAGIC);
            output.write(version & 0xFF);
            output.write((version >> 8) & 0xFF);
            output.write((version >> 16) & 0xFF);
            output.write((version >> 24) & 0xFF);
            for (ProfilesData frame : frames) {
                frame.writeDelimitedTo(output);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static void writeRaw(Path file, ProfilesData data) {
        try (OutputStream output = Files.newOutputStream(file)) {
            data.writeTo(output);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
