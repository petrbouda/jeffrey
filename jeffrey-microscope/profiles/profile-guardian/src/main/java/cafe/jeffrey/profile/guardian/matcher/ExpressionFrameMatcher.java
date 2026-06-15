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

package cafe.jeffrey.profile.guardian.matcher;

import cafe.jeffrey.frameir.Frame;
import cafe.jeffrey.profile.guardian.definition.MatchOp;

import java.util.regex.Pattern;

/**
 * A single frame-name predicate evaluated with a {@link MatchOp}. This is the leaf used by the
 * generic {@code MatchExpr} model; the boolean composition (any/all/not) is provided by the
 * {@link FrameMatcher} default combinators, so this class only has to evaluate one operator.
 */
public class ExpressionFrameMatcher implements FrameMatcher {

    private final MatchOp op;
    private final String value;
    private final Pattern pattern;

    public ExpressionFrameMatcher(MatchOp op, String value) {
        this.op = op;
        this.value = value;
        this.pattern = op == MatchOp.REGEX ? Pattern.compile(value) : null;
    }

    @Override
    public boolean matches(Frame frame) {
        String methodName = frame.methodName();
        return switch (op) {
            case PREFIX -> methodName.startsWith(value);
            case SUFFIX -> methodName.endsWith(value);
            case CONTAINS -> methodName.contains(value);
            case EQUALS -> methodName.equals(value);
            case REGEX -> pattern.matcher(methodName).matches();
        };
    }
}
