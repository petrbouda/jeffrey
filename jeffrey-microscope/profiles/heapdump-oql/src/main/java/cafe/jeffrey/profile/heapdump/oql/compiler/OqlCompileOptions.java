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
package cafe.jeffrey.profile.heapdump.oql.compiler;

/**
 * Per-query knobs threaded into {@code OqlCompiler.compile}. Today there is
 * only one — whether to also scan {@code java.lang.String} instances whose
 * decoded content exceeded the indexer's content cap (the
 * {@code string_content.content IS NULL} rows). Off by default; the UI
 * "Scan large Strings" checkbox flips it on per query.
 */
public record OqlCompileOptions(boolean scanLargeStrings) {

    public static final OqlCompileOptions DEFAULTS = new OqlCompileOptions(false);
}
