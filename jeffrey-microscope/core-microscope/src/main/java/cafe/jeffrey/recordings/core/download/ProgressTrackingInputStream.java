/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package cafe.jeffrey.recordings.core.download;

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

}
