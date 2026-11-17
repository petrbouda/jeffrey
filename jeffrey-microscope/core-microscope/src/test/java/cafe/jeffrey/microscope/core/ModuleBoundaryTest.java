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

package cafe.jeffrey.microscope.core;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Microscope never talks to the hub: both read the recording volume through {@code shared/volume},
 * and Microscope maps its layout onto records of its own ({@code cafe.jeffrey.microscope.connector.*})
 * instead of reaching for the hub's.
 */
class ModuleBoundaryTest {

    private static final Path MICROSCOPE_ROOT = Path.of("..").toAbsolutePath().normalize();

    private static final Pattern HUB_IMPORT = Pattern.compile(
            "^import (static )?cafe\\.jeffrey\\.hub\\.");

    @Test
    void noMicroscopeSourceImportsAHubPackage() throws IOException {
        List<String> offenders;
        try (Stream<Path> files = Files.walk(MICROSCOPE_ROOT)) {
            offenders = files
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !path.toString().contains("/target/"))
                    .filter(path -> !path.toString().contains("/node_modules/"))
                    .filter(path -> !path.toString().contains("/pages-microscope/"))
                    .filter(ModuleBoundaryTest::importsHub)
                    .map(MICROSCOPE_ROOT::relativize)
                    .map(Path::toString)
                    .sorted()
                    .toList();
        }
        assertEquals(List.of(), offenders);
    }

    private static boolean importsHub(Path file) {
        try (Stream<String> lines = Files.lines(file)) {
            return lines.anyMatch(line -> HUB_IMPORT.matcher(line).find());
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read " + file, e);
        }
    }
}
