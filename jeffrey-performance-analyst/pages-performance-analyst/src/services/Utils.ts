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

/**
 * Minimal Analyst-local Utils — only the helpers the shared recording components need.
 * (The Microscope Utils carries streaming/profile-specific helpers that the Analyst does not use.)
 */
export default class Utils {
  static capitalize(str: string): string {
    return str.charAt(0).toUpperCase() + str.slice(1);
  }

  static formatFileType(fileType: string): string {
    switch (fileType) {
      case 'JFR_LZ4':
        return 'JFR (LZ4)';
      case 'PERF_COUNTERS':
        return 'Perf Counters';
      case 'HEAP_DUMP_GZ':
        return 'Heap Dump (GZ)';
      case 'HEAP_DUMP':
        return 'Heap Dump';
      case 'UNKNOWN':
        return 'Unknown';
      case 'ASPROF_TEMP':
        return 'Asprof Temp';
      case 'JVM_LOG':
        return 'JVM Log';
      case 'HS_JVM_ERROR_LOG':
        return 'HotSpot JVM Error Log';
      case 'APP_LOG':
        return 'Application Log';
      default:
        return fileType;
    }
  }
}
