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

package pbouda.jeffrey.platform.manager.download;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.function.BiConsumer;

/**
 * An InputStream wrapper that reports read progress via a callback.
 * Progress is reported at configurable intervals to avoid excessive callbacks.
 * Includes fileName for parallel download tracking.
 */
public class ProgressTrackingInputStream extends FilterInputStream {

    /**
     * Minimum bytes between progress reports (1 MB).
     */
    private static final long REPORT_INTERVAL = 1024 * 1024;

    private final String fileName;
    private final BiConsumer<String, Long> progressConsumer;
    private long bytesRead = 0;
    private long lastReportedBytes = 0;

    /**
     * Creates a progress tracking input stream.
     *
     * @param in               the underlying input stream
     * @param fileName         name of the file being read (for parallel tracking)
     * @param progressConsumer callback invoked with (fileName, bytesRead) for progress updates
     */
    public ProgressTrackingInputStream(InputStream in, String fileName, BiConsumer<String, Long> progressConsumer) {
        super(in);
        this.fileName = fileName;
        this.progressConsumer = progressConsumer;
    }

    @Override
    public int read() throws IOException {
        int b = super.read();
        if (b != -1) {
            bytesRead++;
            reportProgressIfNeeded();
        }
        return b;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        int count = super.read(b, off, len);
        if (count > 0) {
            bytesRead += count;
            reportProgressIfNeeded();
        }
        return count;
    }

    @Override
    public long skip(long n) throws IOException {
        long skipped = super.skip(n);
        if (skipped > 0) {
            bytesRead += skipped;
            reportProgressIfNeeded();
        }
        return skipped;
    }

    @Override
    public void close() throws IOException {
        // Report final progress before closing
        if (bytesRead > lastReportedBytes) {
            progressConsumer.accept(fileName, bytesRead);
        }
        super.close();
    }

    /**
     * Reports progress if enough bytes have been read since the last report.
     */
    private void reportProgressIfNeeded() {
        if (bytesRead - lastReportedBytes >= REPORT_INTERVAL) {
            progressConsumer.accept(fileName, bytesRead);
            lastReportedBytes = bytesRead;
        }
    }

    /**
     * Returns the name of the file being tracked.
     */
    public String getFileName() {
        return fileName;
    }

    /**
     * Returns the total number of bytes read so far.
     */
    public long getBytesRead() {
        return bytesRead;
    }
}
