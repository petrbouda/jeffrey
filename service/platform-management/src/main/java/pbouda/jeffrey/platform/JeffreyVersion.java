/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
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

package pbouda.jeffrey.platform;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.shared.filesystem.FileSystemUtils;

public abstract class JeffreyVersion {

    private static final Logger LOG = LoggerFactory.getLogger(JeffreyVersion.class);

    private static final String JEFFREY_VERSION = "jeffrey-tag.txt";
    private static final String NO_VERSION = "Cannot resolve the version!";

    public static void print() {
        System.out.println(resolveJeffreyVersion());
    }

    public static String resolveJeffreyVersion() {
        try {
            String version = FileSystemUtils.readFromClasspath("classpath:" + JEFFREY_VERSION);
            if (version.isBlank()) {
                return NO_VERSION;
            } else {
                return version;
            }
        } catch (Exception ex) {
            return NO_VERSION;
        }
    }
}
