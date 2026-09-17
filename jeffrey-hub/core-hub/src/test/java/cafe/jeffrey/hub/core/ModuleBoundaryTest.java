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
            "^import (static )?cafe\\.jeffrey\\.(microscope|hub\\.client|recordings|storage|profile|shared\\.ui\\.hub)\\.");

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
