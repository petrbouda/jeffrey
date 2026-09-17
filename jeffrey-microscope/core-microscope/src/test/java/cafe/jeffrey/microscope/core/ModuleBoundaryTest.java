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
 * Microscope talks to a hub over gRPC and nothing else: it maps the proto onto records of its
 * own and never reaches for the hub's. {@code cafe.jeffrey.hub.client} and
 * {@code cafe.jeffrey.hub.api} are Microscope's — the client and the contract — and are not
 * matched here.
 */
class ModuleBoundaryTest {

    private static final Path MICROSCOPE_ROOT = Path.of("..").toAbsolutePath().normalize();

    private static final Pattern HUB_IMPORT = Pattern.compile(
            "^import (static )?cafe\\.jeffrey\\.hub\\.(core|model|persistence)\\.");

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
