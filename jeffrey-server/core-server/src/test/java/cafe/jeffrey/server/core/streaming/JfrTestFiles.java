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

package cafe.jeffrey.server.core.streaming;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Test utility for resolving JFR test fixture files from classpath.
 */
abstract class JfrTestFiles {

    static final String PROFILE_1 = "profile-1.jfr";
    static final String PROFILE_2 = "profile-2.jfr";
    static final String PROFILE_3 = "profile-3.jfr";
    static final String PROFILE_4 = "profile-4.jfr";

    static Path resolve(String name) {
        try {
            return Path.of(JfrTestFiles.class.getClassLoader()
                    .getResource("jfrs/" + name).toURI());
        } catch (URISyntaxException e) {
            throw new RuntimeException("Failed to resolve JFR test file: " + name, e);
        }
    }

    static List<Path> allProfiles() {
        return List.of(resolve(PROFILE_1), resolve(PROFILE_2), resolve(PROFILE_3), resolve(PROFILE_4));
    }

    /**
     * Creates a corrupted JFR file by copying the first half of profile-1.jfr.
     */
    static Path createCorruptedFile(Path targetDir) throws IOException {
        Path source = resolve(PROFILE_1);
        Path corrupted = targetDir.resolve("corrupted.jfr");
        byte[] bytes = Files.readAllBytes(source);
        try (OutputStream out = Files.newOutputStream(corrupted)) {
            out.write(bytes, 0, bytes.length / 2);
        }
        return corrupted;
    }
}
