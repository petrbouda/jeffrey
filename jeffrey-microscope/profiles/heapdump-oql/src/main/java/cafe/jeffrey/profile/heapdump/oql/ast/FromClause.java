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
package cafe.jeffrey.profile.heapdump.oql.ast;

/**
 * FROM clause: source of objects, an optional hierarchy expansion kind
 * ({@link FromKind#INSTANCEOF} or {@link FromKind#IMPLEMENTS}), and the
 * binding variable name used inside SELECT/WHERE/GROUP BY/ORDER BY/HAVING.
 *
 * <p>The {@code alias} is null only when none is needed (e.g. {@code SELECT *
 * FROM java.lang.String}); whenever an expression references the iterated
 * object by name, the binding must be present.
 */
public record FromClause(FromKind kind, ObjectSource source, String alias) {

    public FromClause {
        kind = kind == null ? FromKind.NONE : kind;
        if (source == null) {
            throw new IllegalArgumentException("from source must be present");
        }
    }

    public enum FromKind {
        NONE,
        INSTANCEOF,
        IMPLEMENTS
    }
}
