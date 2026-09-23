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
