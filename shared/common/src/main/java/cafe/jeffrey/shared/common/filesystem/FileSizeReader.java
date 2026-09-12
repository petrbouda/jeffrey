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
 * <p>{@link #OPEN_HANDLE} is for a file another process may still be writing, where the listing
 * cannot be trusted; {@link #FILE_ATTRIBUTES} is for a file whose writer has closed it, where
 * the listing is right and an open would only cost a round trip. Which one a caller wants is
 * decided by what it knows about the file, never by the size that comes back: a zero is a
 * perfectly ordinary answer for a closed file, and re-reading every one of them turns a listing
 * of many sessions into as many round trips. Neither reader can see further than the writer's
 * last flush — that lag belongs to the writer's own mount.
 *
 * <p>Both readers are stateless: the constants exist so a caller need not allocate one per
 * session, and nothing — no size, no path, no handle — is kept between calls. The only cache
 * in the picture is the kernel's, which the hub cannot switch off from Java.
 */
public interface FileSizeReader {

    FileSizeReader OPEN_HANDLE = new OpenHandle();
    FileSizeReader FILE_ATTRIBUTES = new FileAttributes();

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
     * Reads the file's attributes as {@code stat()} reports them, which on a network mount is
     * the kernel's cached copy and costs no round trip. Correct for any file whose writer has
     * closed it, and wrong — sometimes zero, sometimes a size frozen at the last flush — for
     * one still being written elsewhere.
     */
    record FileAttributes() implements FileSizeReader {

        @Override
        public long size(Path path) {
            try {
                return Files.size(path);
            } catch (IOException e) {
                throw new RuntimeException("Cannot get size of file: " + path, e);
            }
        }
    }
}
