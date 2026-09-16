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

import RecordingStatus from '@hubs/services/api/model/RecordingStatus.ts';

/**
 * A project's repository in totals. There is deliberately no breakdown by file type: the hub used
 * to send six fixed buckets, which meant deciding what every type means, and a pprof or OTLP
 * recording arrived as "other". Adding a file type now changes nothing here.
 */
export default interface RepositoryStatistics {
  // Session Overview
  totalSessions: number;
  sessionStatus: RecordingStatus; // Status of the latest session
  lastActivityTime: number; // Timestamp in milliseconds - for use with FormattingService.formatRelativeTime()

  // Storage Overview
  totalSize: number; // Total repository size in bytes
  totalFiles: number; // Total number of files across all sessions
  biggestSessionSize: number; // Size of the largest session in bytes
}
