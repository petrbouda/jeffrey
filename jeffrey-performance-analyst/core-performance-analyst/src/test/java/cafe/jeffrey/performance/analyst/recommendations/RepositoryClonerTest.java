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

package cafe.jeffrey.performance.analyst.recommendations;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import cafe.jeffrey.performance.analyst.persistence.Platform;
import cafe.jeffrey.shared.common.exception.JeffreyClientException;
import cafe.jeffrey.shared.common.filesystem.TempDirFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RepositoryClonerTest {

    @TempDir
    Path tempBase;

    @Test
    void deletesTheTempCheckoutWhenCloneFails() throws IOException {
        RepositoryCloner cloner = new RepositoryCloner(TempDirFactory.of(tempBase));

        // A file:// URL to a non-existent local repository fails fast without any network access.
        String missingRepo = tempBase.resolve("does-not-exist.git").toUri().toString();

        assertThrows(JeffreyClientException.class,
                () -> cloner.clone(missingRepo, null, Platform.GITHUB));

        // The throwaway checkout directory must not be left behind on failure.
        try (Stream<Path> entries = Files.list(tempBase)) {
            assertEquals(0, entries.count(), "no temp checkout should remain after a failed clone");
        }
    }
}
