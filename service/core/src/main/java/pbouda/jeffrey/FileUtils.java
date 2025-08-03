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
import pbouda.jeffrey.common.Json;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public abstract class FileUtils {

    /**
     * Loads a String file from the path. It can also read from classpath resources
     * if the path starts with "classpath:", or file if the path starts with "file:".
     *
     * @param path the path to the file
     * @return the parsed object
     */
    public static String readString(String path) {
        String content;
        if (path.startsWith("classpath:")) {
            content = readFromClasspath(path);
        } else if (path.startsWith("file:")) {
            content = readFromFile(path);
        } else {
            throw new IllegalArgumentException(
                    "Unsupported path, must start with 'classpath:' or 'file:': path=" + path);
        }

        return content;
    }

    /**
     * Loads a JSON file from the path and parses it into an object of the specified type. It can also read from
     * classpath resources if the path starts with "classpath:", or file if the path starts with "file:".
     *
     * @param path the path to the JSON file
     * @param type the type reference for the object to be parsed
     * @param <T>  the type of the object
     * @return the parsed object
     */
    public static <T> T readJson(String path, TypeReference<T> type) {
        String content = readString(path);
        return Json.read(content, type);
    }

    private static String readFromFile(String path) {
        Path contentPath = Path.of(path.substring("file:".length()));
        try {
            return Files.readString(contentPath);
        } catch (IOException e) {
            throw new RuntimeException("Error reading file: " + path, e);
        }
    }

    private static String readFromClasspath(String pathOnClasspath) {
        String path = pathOnClasspath.substring("classpath:".length());
        try (InputStream stream = FileUtils.class.getClassLoader().getResourceAsStream(path)) {
            if (stream == null) {
                throw new FileNotFoundException("File not found in classpath: " + path);
            }
            return new String(stream.readAllBytes());
        } catch (Exception e) {
            throw new RuntimeException("Error reading file from classpath: " + path, e);
        }
    }
}
