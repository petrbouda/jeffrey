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

/**
 * JDK instance-field names that the heap-dump analyzers match against. Kept
 * in one place so the same literal isn't scattered across analyzers that all
 * need to recognise {@code String.value}, {@code String.coder}, or
 * {@code Thread.name}.
 */
public final class JdkFieldNames {

    public static final String STRING_VALUE = "value";
    public static final String STRING_CODER = "coder";
    public static final String THREAD_NAME = "name";

    private JdkFieldNames() {
    }
}
