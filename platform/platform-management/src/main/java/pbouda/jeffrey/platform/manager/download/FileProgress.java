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

/**
 * Progress information for a single file being downloaded.
 *
 * @param fileName        Name of the file
 * @param fileSize        Total size of the file in bytes
 * @param downloadedBytes Bytes downloaded so far
 * @param status          Current status of this file's download
 */
public record FileProgress(
        String fileName,
        long fileSize,
        long downloadedBytes,
        FileProgressStatus status
) {
    /**
     * Creates a new FileProgress for a file that is starting to download.
     */
    public static FileProgress starting(String fileName, long fileSize) {
        return new FileProgress(fileName, fileSize, 0, FileProgressStatus.DOWNLOADING);
    }

    /**
     * Creates an updated FileProgress with new downloaded bytes.
     * Adjusts fileSize upward if downloadedBytes exceeds it to prevent progress exceeding 100%.
     */
    public FileProgress withProgress(long downloadedBytes) {
        long adjustedSize = Math.max(fileSize, downloadedBytes);
        return new FileProgress(fileName, adjustedSize, downloadedBytes, status);
    }
}
