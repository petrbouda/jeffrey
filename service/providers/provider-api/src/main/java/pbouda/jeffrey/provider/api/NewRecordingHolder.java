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

package pbouda.jeffrey.provider.api;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class NewRecordingHolder implements AutoCloseable {

    private final String recordingId;
    private final Path targetPath;
    private final Runnable uploadCompleteCallback;

    public NewRecordingHolder(
            String recordingId,
            Path targetPath,
            Runnable uploadCompleteCallback) {

        this.recordingId = recordingId;
        this.targetPath = targetPath;
        this.uploadCompleteCallback = uploadCompleteCallback;
    }

    public String getRecordingId() {
        return recordingId;
    }

    public Path outputPath() {
        return targetPath;
    }

    public void transferFrom(InputStream input) {
        try (var output = Files.newOutputStream(targetPath, StandardOpenOption.CREATE_NEW)) {
            input.transferTo(output);
        } catch (Exception e) {
            throw new RuntimeException("Cannot transfer data to a new recording", e);
        }
    }

    @Override
    public void close() throws Exception {
        uploadCompleteCallback.run();
    }
}
