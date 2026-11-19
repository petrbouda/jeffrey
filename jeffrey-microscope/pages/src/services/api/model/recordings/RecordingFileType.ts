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

enum RecordingFileType {
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

export default RecordingFileType;
