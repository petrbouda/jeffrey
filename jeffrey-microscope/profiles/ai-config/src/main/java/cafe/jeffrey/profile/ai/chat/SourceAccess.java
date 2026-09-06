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

package cafe.jeffrey.profile.ai.chat;

import java.nio.file.Path;

/**
 * A directory an AI analysis may read while it works.
 *
 * <p>Everything else the assistant is given describes a recording: tables, aggregates, call trees. It
 * can name a class and a method and can say what the JVM was doing in them, and then it stops,
 * because it has never seen the code. That gap is why an in-app answer says "consider optimising
 * {@code OrderService.process}" where a coding agent with the repository open says which loop and
 * why.
 *
 * <p>What makes this safe to offer at all is that the directory is not guessed. It is the checkout of
 * the IDE window the reader themselves linked to this profile — a deliberate act, in a window the IDE
 * has already confirmed as a trusted project — and it is off unless the installation turns it on.
 *
 * @param root the directory the model may read, and the only one: the analysis runs with this as its
 *             working directory, so the reach is the linked project rather than wherever Jeffrey
 *             happens to have been started from
 */
public record SourceAccess(Path root) {

    public SourceAccess {
        if (root == null) {
            throw new IllegalArgumentException("root must not be null");
        }
    }
}
