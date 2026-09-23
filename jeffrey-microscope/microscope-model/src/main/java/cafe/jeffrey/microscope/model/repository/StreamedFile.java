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

package cafe.jeffrey.microscope.model.repository;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public record StreamedFile(String fileName, Path path, Closeable cleanup) {

    public StreamedFile(String fileName, Path path) {
        this(fileName, path, null);
    }

    /**
     * Opens an InputStream for the file. If a cleanup action is present,
     * it will be executed when the stream is closed.
     */
    public InputStream openStream() throws IOException {
        InputStream stream = Files.newInputStream(path);
        if (cleanup != null) {
            stream = new CleanupInputStream(stream, cleanup);
        }
        return stream;
    }
}
