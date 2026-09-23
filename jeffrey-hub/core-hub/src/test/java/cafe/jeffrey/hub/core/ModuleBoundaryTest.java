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

package cafe.jeffrey.hub.core;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The hub compiles against {@code shared/} and its own modules and nothing of Microscope's.
 * Maven already makes this so — no hub pom names a Microscope artifact — but a pom is edited
 * once and read never, and this is the sentence that fails when someone adds one.
 */
class ModuleBoundaryTest {

    private static final Path HUB_ROOT = Path.of("..").toAbsolutePath().normalize();

    private static final Pattern MICROSCOPE_IMPORT = Pattern.compile(
            "^import (static )?cafe\\.jeffrey\\.(microscope|hub\\.client|recordings|storage|profile|shared\\.ui\\.hub|shared\\.notification)\\.");

    @Test
    void noHubSourceImportsAMicroscopePackage() throws IOException {
        List<String> offenders;
        try (Stream<Path> files = Files.walk(HUB_ROOT)) {
            offenders = files
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !path.toString().contains("/target/"))
                    .filter(path -> !path.toString().contains("/pages-hub/"))
                    .filter(ModuleBoundaryTest::importsMicroscope)
                    .map(HUB_ROOT::relativize)
                    .map(Path::toString)
                    .sorted()
                    .toList();
        }
        assertEquals(List.of(), offenders);
    }

    private static boolean importsMicroscope(Path file) {
        try (Stream<String> lines = Files.lines(file)) {
            return lines.anyMatch(line -> MICROSCOPE_IMPORT.matcher(line).find());
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read " + file, e);
        }
    }
}
