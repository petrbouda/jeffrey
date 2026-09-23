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
