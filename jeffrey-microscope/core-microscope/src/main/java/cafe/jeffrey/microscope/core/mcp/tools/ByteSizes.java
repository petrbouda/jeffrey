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

package cafe.jeffrey.microscope.core.mcp.tools;

import java.util.Locale;

/**
 * Byte counts as a table cell reads them: {@code 512B}, {@code 3MB}, {@code 1.2GB}. Whole units up to
 * megabytes, one decimal from gigabytes, because that is the precision a reader deciding whether to
 * transfer a file needs and no more.
 */
final class ByteSizes {

    private static final long KIB = 1024;
    private static final long MIB = KIB * 1024;
    private static final long GIB = MIB * 1024;

    private ByteSizes() {
    }

    static String format(Long bytes) {
        if (bytes == null) {
            return "";
        }
        if (bytes < KIB) {
            return bytes + "B";
        }
        if (bytes < MIB) {
            return Math.round(bytes / (double) KIB) + "KB";
        }
        if (bytes < GIB) {
            return Math.round(bytes / (double) MIB) + "MB";
        }
        return String.format(Locale.ROOT, "%.1fGB", bytes / (double) GIB);
    }
}
