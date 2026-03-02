/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
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

import RecordingStatus from "@/services/api/model/RecordingStatus.ts";

export default interface RepositoryStatistics {
    // Session Overview
    totalSessions: number;
    sessionStatus: RecordingStatus; // Status of the latest session
    lastActivityTime: number; // Timestamp in milliseconds - for use with FormattingService.formatRelativeTime()
    
    // Storage Overview  
    totalSize: number; // Total repository size in bytes
    totalFiles: number; // Total number of files across all sessions
    biggestSessionSize: number; // Size of the largest session in bytes
    
    // File Type Breakdown
    jfrFiles: number; // Number of JFR files
    jfrSize: number; // Total size of JFR files in bytes
    heapDumpFiles: number; // Number of heap dump files
    heapDumpSize: number; // Total size of heap dump files in bytes
    logFiles: number; // Number of JVM log files
    logSize: number; // Total size of JVM log files in bytes
    appLogFiles: number; // Number of application log files
    appLogSize: number; // Total size of application log files in bytes
    errorLogFiles: number; // Number of HS JVM error log files
    errorLogSize: number; // Total size of HS JVM error log files in bytes
    otherFiles: number; // Number of other/unknown files
    otherSize: number; // Total size of other/unknown files in bytes
}