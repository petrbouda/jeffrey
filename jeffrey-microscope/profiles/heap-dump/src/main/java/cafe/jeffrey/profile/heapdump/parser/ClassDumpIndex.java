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
package cafe.jeffrey.profile.heapdump.parser;

import java.util.List;
import java.util.Map;

/**
 * Output of Pass A ({@link HprofClassDumpWalker}). Read-only after
 * construction; passed by reference to Pass B (which uses {@link #byId}
 * read-only), the shallow-size corrector, the string-content writer, and the
 * metadata writer.
 */
public record ClassDumpIndex(
        Map<Long, HprofRecord.ClassDump> byId,
        long classCount,
        List<ParseWarning> warnings) {
}
