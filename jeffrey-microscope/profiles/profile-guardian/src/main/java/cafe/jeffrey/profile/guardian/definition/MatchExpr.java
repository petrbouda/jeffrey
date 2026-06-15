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

package cafe.jeffrey.profile.guardian.definition;

import java.util.List;

/**
 * A generic, fully user-specifiable matcher for finding the anchor frame of a guard. Modelled as a
 * recursive boolean predicate tree — a {@link Predicate} leaf tests a frame name with a {@link MatchOp},
 * and {@link AnyOf}/{@link AllOf}/{@link Not} compose leaves into arbitrary boolean expressions.
 * <p>
 * This subsumes the legacy prefix/suffix/jvm/composite matchers and goes beyond them: custom guards
 * can be created from the UI without any code change, with {@code REGEX} covering arbitrary cases.
 */
public sealed interface MatchExpr
        permits MatchExpr.Predicate, MatchExpr.AnyOf, MatchExpr.AllOf, MatchExpr.Not {

    record Predicate(MatchOp op, String value) implements MatchExpr {
        public Predicate {
            if (op == null) {
                throw new IllegalArgumentException("MatchOp must not be null");
            }
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("Match value must not be blank");
            }
        }
    }

    record AnyOf(List<MatchExpr> of) implements MatchExpr {
        public AnyOf {
            if (of == null || of.isEmpty()) {
                throw new IllegalArgumentException("AnyOf requires at least one expression");
            }
            of = List.copyOf(of);
        }
    }

    record AllOf(List<MatchExpr> of) implements MatchExpr {
        public AllOf {
            if (of == null || of.isEmpty()) {
                throw new IllegalArgumentException("AllOf requires at least one expression");
            }
            of = List.copyOf(of);
        }
    }

    record Not(MatchExpr expr) implements MatchExpr {
        public Not {
            if (expr == null) {
                throw new IllegalArgumentException("Not requires a nested expression");
            }
        }
    }

    static MatchExpr prefix(String value) {
        return new Predicate(MatchOp.PREFIX, value);
    }

    static MatchExpr suffix(String value) {
        return new Predicate(MatchOp.SUFFIX, value);
    }

    static MatchExpr equalsTo(String value) {
        return new Predicate(MatchOp.EQUALS, value);
    }

    static MatchExpr anyOf(MatchExpr... expressions) {
        return new AnyOf(List.of(expressions));
    }
}
