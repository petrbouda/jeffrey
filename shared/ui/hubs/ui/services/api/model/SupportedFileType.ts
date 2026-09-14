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

/**
 * Mirror of the backend's SupportedFile enum names, which is what the `fileType` field carries.
 * Everything a type implies is declared on the backend type; the one fact the UI needs on its own
 * is which types are chunks of a session's recording, kept here so no component holds a list.
 */
enum SupportedFileType {
  JFR_LZ4 = 'JFR_LZ4',
  JFR = 'JFR',
  HEAP_DUMP_GZ = 'HEAP_DUMP_GZ',
  HEAP_DUMP = 'HEAP_DUMP',
  ASPROF = 'ASPROF_TEMP',
  PERF_COUNTERS = 'PERF_COUNTERS',
  JVM_LOG = 'JVM_LOG',
  HS_JVM_ERROR_LOG = 'HS_JVM_ERROR_LOG',
  APP_LOG = 'APP_LOG',
  PPROF = 'PPROF',
  OTLP_PROFILE = 'OTLP_PROFILE',
  UNKNOWN = 'UNKNOWN'
}

const RECORDING_CHUNK_TYPES: ReadonlySet<SupportedFileType> = new Set([
  SupportedFileType.JFR,
  SupportedFileType.JFR_LZ4
]);

/**
 * Whether a file is a chunk of the session's recording. A function rather than a method because
 * files arrive as plain JSON, never as class instances.
 */
export function isRecordingChunk(file: { fileType: SupportedFileType }): boolean {
  return RECORDING_CHUNK_TYPES.has(file.fileType);
}

export default SupportedFileType;
