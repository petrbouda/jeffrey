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

package cafe.jeffrey.profile.manager.model.system;

/**
 * A subprocess launched by the JVM during the recording, from {@code jdk.ProcessStart}.
 *
 * @param timeOffsetMillis offset from recording start
 * @param pid              the started process id
 * @param command          the executed command
 * @param directory        the working directory
 * @param thread           the JVM thread that launched it
 */
public record LaunchedProcessInfo(
        long timeOffsetMillis,
        long pid,
        String command,
        String directory,
        String thread) {
}
