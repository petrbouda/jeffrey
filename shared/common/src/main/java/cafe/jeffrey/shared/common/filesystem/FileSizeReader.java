/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
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

package cafe.jeffrey.shared.common.filesystem;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * How a file's size is read, which on a network mount is a real choice.
 *
 * <p>{@link Files#size(Path)} is a {@code stat()} answered from the client's attribute cache,
 * and on an SMB mount (Azure Files, a Windows share) that cache is primed from the directory
 * listing the caller has usually just made. For a file another client holds open for writing
 * — the profiled JVM's {@code -Xlog} file, async-profiler's {@code .jfr.N~} cache — the
 * listing carries the directory entry's copy of the size, which the server refreshes only when
 * the writer flushes or closes, or its op-lock breaks. A session that is recording perfectly
 * well can therefore list as nothing but zeros for as long as it runs, or freeze at whatever
 * size the directory entry last saw. Opening the file is a different question to the server:
 * the open's response carries the file's current length, and the kernel refreshes the cached
 * attributes from it before the handle's own {@code fstat()} answers.
 *
 * <p>{@link #OPEN_HANDLE} is for files that another process may still be writing, where the
 * listing cannot be trusted at all; {@link #CACHED_ATTRIBUTES} is for files whose writer has
 * closed them, where the listing is right and the open would only cost a round trip. It still
 * distrusts a zero, since an empty file is rare and the re-read is cheap. Neither can see
 * further than the writer's last flush — that lag belongs to the writer's own mount.
 */
public interface FileSizeReader {

    FileSizeReader OPEN_HANDLE = new OpenHandle();
    FileSizeReader CACHED_ATTRIBUTES = new CachedAttributes(OPEN_HANDLE);

    /**
     * @throws RuntimeException when the file cannot be read, wrapping the {@link IOException}
     */
    long size(Path path);

    /**
     * Opens the file read-only and asks the handle, forcing the server to answer with the
     * file's current length regardless of what the directory listing said.
     */
    record OpenHandle() implements FileSizeReader {

        @Override
        public long size(Path path) {
            try (FileChannel channel = FileChannel.open(path, StandardOpenOption.READ)) {
                return channel.size();
            } catch (IOException e) {
                throw new RuntimeException("Cannot get size of file through an open handle: " + path, e);
            }
        }
    }

    /**
     * Reads the cached attributes and trusts them unless they say the file is empty, in which
     * case the given reader gets the last word.
     */
    record CachedAttributes(FileSizeReader onZero) implements FileSizeReader {

        private static final Logger LOG = LoggerFactory.getLogger(CachedAttributes.class);

        @Override
        public long size(Path path) {
            long attributeSize;
            try {
                attributeSize = Files.size(path);
            } catch (IOException e) {
                throw new RuntimeException("Cannot get size of file: " + path, e);
            }
            if (attributeSize != 0) {
                return attributeSize;
            }
            long reReadSize = onZero.size(path);
            if (reReadSize != 0) {
                LOG.debug("File size re-read after the cached attribute said empty: path={} size={}", path, reReadSize);
            }
            return reReadSize;
        }
    }
}
