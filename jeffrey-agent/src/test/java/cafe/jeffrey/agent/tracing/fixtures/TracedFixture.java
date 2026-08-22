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

package cafe.jeffrey.agent.tracing.fixtures;

import cafe.jeffrey.jfr.events.trace.Traced;

import java.io.IOException;

/**
 * Loaded only after the weaver is installed, so its methods arrive woven. Every method here exists
 * to prove that weaving left the method's own behaviour alone.
 */
public class TracedFixture {

    /** Thrown by identity, so a test can prove the very same instance reaches the caller. */
    public static final IOException BOOM = new IOException("boom");

    @Traced
    public String returnsAValue(String value) {
        return "body:" + value;
    }

    @Traced
    public int returnsAPrimitive(int value) {
        return value * 2;
    }

    @Traced
    public void returnsNothing(StringBuilder sink) {
        sink.append("ran");
    }

    @Traced
    public void throwsChecked() throws IOException {
        throw BOOM;
    }

    @Traced
    public static String isStatic() {
        return "static";
    }

    public String isNotAnnotated() {
        return "untouched";
    }
}
