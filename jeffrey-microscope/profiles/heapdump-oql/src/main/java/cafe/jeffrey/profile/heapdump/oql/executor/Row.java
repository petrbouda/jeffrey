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
package cafe.jeffrey.profile.heapdump.oql.executor;

import cafe.jeffrey.profile.heapdump.parser.HeapView;
import cafe.jeffrey.profile.heapdump.parser.InstanceRow;
import cafe.jeffrey.profile.heapdump.parser.JavaClassRow;

/**
 * Per-iteration context passed to {@link ExprEvaluator}. {@code instance} is
 * the candidate row being evaluated; {@code clazz} is its resolved class
 * descriptor; {@code bindingName} is the FROM-clause alias the user gave (or
 * {@code null} when no alias).
 */
public record Row(HeapView view, InstanceRow instance, JavaClassRow clazz, String bindingName) {

    public Row {
        if (view == null) {
            throw new IllegalArgumentException("view must not be null");
        }
        if (instance == null) {
            throw new IllegalArgumentException("instance must not be null");
        }
        if (clazz == null) {
            throw new IllegalArgumentException("clazz must not be null");
        }
    }
}
