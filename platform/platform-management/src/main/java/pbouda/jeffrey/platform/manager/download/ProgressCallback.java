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
 * Callback interface for reporting download progress.
 * Implementations receive progress updates during file downloads from remote servers.
 */
public interface ProgressCallback {

    /**
     * Called when the download starts.
     *
     * @param totalFiles total number of files to download
     * @param totalBytes total bytes to download across all files
     */
    void onStart(int totalFiles, long totalBytes);

    /**
     * Called when a new file starts downloading.
     *
     * @param fileName name of the file
     * @param fileSize size of the file in bytes
     */
    void onFileStart(String fileName, long fileSize);

    /**
     * Called periodically during file download to report progress.
     * For parallel downloads, fileName identifies which file is being updated.
     *
     * @param fileName        name of the file being downloaded
     * @param bytesDownloaded bytes downloaded so far for this file
     */
    void onFileProgress(String fileName, long bytesDownloaded);

    /**
     * Called when a file has finished downloading.
     *
     * @param fileName name of the completed file
     */
    void onFileComplete(String fileName);

    /**
     * Called when a file download fails.
     * For parallel downloads, other files may continue downloading.
     *
     * @param fileName     name of the failed file
     * @param errorMessage description of the error
     */
    void onFileError(String fileName, String errorMessage);

    /**
     * Called when all files have been downloaded and processing begins.
     */
    void onProcessing();

    /**
     * Called when all files have been downloaded and processed successfully.
     */
    void onComplete();

    /**
     * Called when an error occurs during download.
     *
     * @param errorMessage description of the error
     */
    void onError(String errorMessage);

    /**
     * Returns true if the download has been cancelled.
     * Implementations should check this periodically and abort if true.
     */
    boolean isCancelled();
}
