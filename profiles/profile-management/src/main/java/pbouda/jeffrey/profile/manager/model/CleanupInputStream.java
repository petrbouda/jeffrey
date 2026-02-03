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

package pbouda.jeffrey.profile.manager.model;

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
