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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * An InputStream wrapper that invokes a cleanup action when the stream is closed.
 * Used to delete temporary files (e.g., merged recordings) after they have been
 * fully streamed to the client.
 */
public class CleanupInputStream extends FilterInputStream {

    private static final Logger LOG = LoggerFactory.getLogger(CleanupInputStream.class);

    private final Closeable cleanup;

    public CleanupInputStream(InputStream delegate, Closeable cleanup) {
        super(delegate);
        this.cleanup = cleanup;
    }

    @Override
    public void close() throws IOException {
        try {
            super.close();
        } finally {
            try {
                cleanup.close();
            } catch (IOException e) {
                LOG.warn("Failed to execute cleanup after stream close", e);
            }
        }
    }
}
