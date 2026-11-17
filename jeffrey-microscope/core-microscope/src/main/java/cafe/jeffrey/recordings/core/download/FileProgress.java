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
     * Creates a new FileProgress for a file that is pending download.
     */
    public static FileProgress pending(String fileName, long fileSize) {
        return new FileProgress(fileName, fileSize, 0, FileProgressStatus.PENDING);
    }

    /**
     * Creates a new FileProgress for a file that is starting to download.
     */
    public static FileProgress starting(String fileName, long fileSize) {
        return new FileProgress(fileName, fileSize, 0, FileProgressStatus.DOWNLOADING);
    }

    /**
     * Creates an updated FileProgress with new downloaded bytes.
     */
    public FileProgress withProgress(long downloadedBytes) {
        return new FileProgress(fileName, fileSize, downloadedBytes, status);
    }
}
