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
import java.io.OutputStream;

public class NewRecordingHolder implements AutoCloseable {

    private final String recordingId;
    private final OutputStream output;
    private final Runnable uploadCompleteCallback;
    private final Runnable cleanup;

    public NewRecordingHolder(
            String recordingId,
            OutputStream stream,
            Runnable uploadCompleteCallback,
            Runnable cleanupCallback) {

        this.recordingId = recordingId;
        this.output = stream;
        this.uploadCompleteCallback = uploadCompleteCallback;
        this.cleanup = cleanupCallback;
    }

    public String getRecordingId() {
        return recordingId;
    }

    public void transferFrom(InputStream input) {
        try {
            input.transferTo(output);
        } catch (Exception e) {
            cleanup.run();
            throw new RuntimeException(e);
        }
    }

    @Override
    public void close() throws Exception {
        output.close();
        uploadCompleteCallback.run();
    }
}
