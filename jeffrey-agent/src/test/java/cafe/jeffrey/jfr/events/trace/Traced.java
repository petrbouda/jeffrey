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

package cafe.jeffrey.jfr.events.trace;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Stands in for the real annotation, which lives in {@code jeffrey-tracing} and cannot be depended
 * on here: that library is compiled for Java 25 while the agent targets 21, and the agent resolves
 * everything about tracing by name at runtime rather than by linking.
 * <p>
 * That is precisely what makes this stub the right test double. The agent's contract with the
 * library is a fully-qualified name and a method signature, nothing more, so a stub carrying the
 * same name and signature exercises the real contract. Its attributes are absent on purpose — the
 * agent reads none of them; the library does.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Traced {
}
