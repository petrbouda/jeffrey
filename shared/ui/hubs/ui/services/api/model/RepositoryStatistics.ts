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
