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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OtlpStreamReaderTest {

    @TempDir
    Path tempDir;

    private final OtlpStreamReader reader = new OtlpStreamReader();

    private static ProfilesData frameWithProfiles(int profileCount) {
        OtlpTestFixtures fixtures = new OtlpTestFixtures();
        for (int i = 0; i < profileCount; i++) {
            fixtures.profile(fixtures.profileBuilder("cpu", "nanoseconds", 1_000_000 + i).build());
        }
        return fixtures.build();
    }

    @Test
    void readsAllFramesOfFramedFile() {
        Path file = tempDir.resolve("framed.otlp");
        OtlpTestFiles.writeFramed(file, List.of(frameWithProfiles(1), frameWithProfiles(2), frameWithProfiles(3)));

        List<ProfilesData> frames = new ArrayList<>();
        reader.read(file, frames::add);

        assertEquals(3, frames.size());
        assertEquals(1, frames.get(0).getResourceProfiles(0).getScopeProfiles(0).getProfilesCount());
        assertEquals(3, frames.get(2).getResourceProfiles(0).getScopeProfiles(0).getProfilesCount());
    }

    @Test
    void readsRawFileAsSingleFrame() {
        Path file = tempDir.resolve("raw.otlp");
        OtlpTestFiles.writeRaw(file, frameWithProfiles(2));

        List<ProfilesData> frames = new ArrayList<>();
        reader.read(file, frames::add);

        assertEquals(1, frames.size());
        assertEquals(2, frames.getFirst().getResourceProfiles(0).getScopeProfiles(0).getProfilesCount());
    }

    @Test
    void rejectsUnsupportedFormatVersion() {
        Path file = tempDir.resolve("future.otlp");
        OtlpTestFiles.writeFramed(file, List.of(frameWithProfiles(1)), OtlpFileFormat.VERSION + 1);

        assertThrows(IllegalArgumentException.class, () -> reader.read(file, _ -> {
        }));
    }

    @Test
    void failsOnTruncatedFrame() {
        Path file = tempDir.resolve("truncated.otlp");
        OtlpTestFiles.writeFramed(file, List.of(frameWithProfiles(1)));
        truncate(file);

        assertThrows(UncheckedIOException.class, () -> reader.read(file, _ -> {
        }));
    }

    private static void truncate(Path file) {
        try {
            byte[] content = Files.readAllBytes(file);
            Files.write(file, Arrays.copyOf(content, content.length - 3));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
