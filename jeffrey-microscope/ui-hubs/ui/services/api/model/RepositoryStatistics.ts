/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
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

/**
 * What a project's repository occupies on the hub, in bytes.
 *
 * One figure, because one figure is what anything actually read. This used to carry a status, a
 * session count, a file count, a last-activity timestamp, a biggest-session size and six file-type
 * buckets. The buckets made the hub decide what every file type means, and a pprof or OTLP
 * recording arrived as "other"; the status had no reader at all.
 *
 * There is no capacity to divide it by on purpose. The hub reads a ReadWriteMany volume, and
 * neither NFS, EFS nor hostPath reports the claim's own size, so a percentage would be fiction.
 */
export default interface RepositoryStatistics {
  totalSize: number; // Total repository size in bytes
}
