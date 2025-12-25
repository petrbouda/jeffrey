/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package pbouda.jeffrey;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public abstract class JeffreyHome {

    private static final Logger LOG = LoggerFactory.getLogger(JeffreyHome.class);

    public static void initialize(String homeFolderPath) {
        if (homeFolderPath == null) {
            throw new IllegalArgumentException("Jeffrey Home is not configured. Use 'jeffrey.home.dir' property");
        }

        if (!Files.exists(Path.of(homeFolderPath))) {
            try {
                Files.createDirectories(Path.of(homeFolderPath));
            } catch (IOException e) {
                throw new RuntimeException("Cannot create Jeffrey Home directory: " + homeFolderPath, e);
            }

            LOG.info("Jeffrey Home directory created: " + homeFolderPath);
        }
    }
}
