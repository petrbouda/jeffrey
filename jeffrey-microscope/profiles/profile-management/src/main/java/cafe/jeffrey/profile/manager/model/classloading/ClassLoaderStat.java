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

package cafe.jeffrey.profile.manager.model.classloading;

/**
 * Latest per-class-loader statistics snapshot, derived from {@code jdk.ClassLoaderStatistics}.
 *
 * @param name                 readable loader identity (type, optionally with instance name)
 * @param parentName           readable parent loader identity, or {@code null} for the bootstrap parent
 * @param classCount           number of classes currently loaded by this loader
 * @param metaspaceBytes       metaspace reserved by this loader (chunk size)
 * @param blockBytes           metaspace actually used by this loader (block size)
 * @param hiddenClassCount     hidden classes loaded by this loader
 * @param hiddenMetaspaceBytes metaspace reserved for hidden classes (hidden chunk size)
 */
public record ClassLoaderStat(
        String name,
        String parentName,
        long classCount,
        long metaspaceBytes,
        long blockBytes,
        long hiddenClassCount,
        long hiddenMetaspaceBytes) {
}
