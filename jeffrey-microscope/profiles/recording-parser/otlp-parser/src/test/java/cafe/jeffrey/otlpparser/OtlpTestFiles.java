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
