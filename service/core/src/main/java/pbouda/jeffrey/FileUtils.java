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

import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.util.ResourceUtils;
import pbouda.jeffrey.common.Json;

import java.io.File;
import java.io.FileNotFoundException;

public abstract class FileUtils {

    /**
     * Loads a JSON file from the path and parses it into an object of the specified type. It can also read from
     * classpath resources if the path starts with "classpath:".
     *
     * @param path the path to the JSON file
     * @param type the type reference for the object to be parsed
     * @param <T>  the type of the object
     * @return the parsed object
     */
    public static <T> T readJson(String path, TypeReference<T> type) {
        try {
            File file = ResourceUtils.getFile(path);
            return Json.read(file, type);
        } catch (FileNotFoundException e) {
            throw new RuntimeException("File for loading definitions not found: " + path, e);
        }
    }
}
