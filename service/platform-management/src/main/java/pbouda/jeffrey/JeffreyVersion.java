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

package pbouda.jeffrey;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public abstract class JeffreyVersion {

    private static final Logger LOG = LoggerFactory.getLogger(JeffreyVersion.class);

    private static final String JEFFREY_VERSION = "jeffrey-tag.txt";
    private static final String NO_VERSION = "Cannot resolve the version!";

    public static void print() {
        System.out.println(resolveJeffreyVersion());
    }

    public static String resolveJeffreyVersion() {
        try (InputStream in = Application.class.getClassLoader()
                .getResourceAsStream(JEFFREY_VERSION)) {
            if (in != null) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(in))) {
                    String version = reader.readLine();
                    return version.isBlank() ? NO_VERSION : version;
                }
            } else {
                LOG.warn("Unable to read a version: {}", JEFFREY_VERSION);
                return NO_VERSION;
            }
        } catch (IOException ex) {
            LOG.warn("Unable to read a version: {}", JEFFREY_VERSION, ex);
            return NO_VERSION;
        }
    }
}
