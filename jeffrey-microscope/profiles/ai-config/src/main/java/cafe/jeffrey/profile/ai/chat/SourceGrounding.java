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

/**
 * The part of a system prompt that tells the model it can read the code behind the profile.
 *
 * <p>Granting {@link SourceAccess} puts the checkout in front of the model and nothing more. The
 * reading tools appear in its tool list the way every other tool does, with no indication that this
 * particular directory is the code that produced the measurements it is looking at — so an analysis
 * that could have opened the method it is about to describe goes on describing it from its name. A
 * capability nobody mentions is close to no capability.
 *
 * <p>One copy for both assistants. The discipline is identical whether the finding started as a hot
 * frame or as a retained object — read it before naming it — and two copies of a rule like that drift
 * apart one edit at a time.
 */
public final class SourceGrounding {

    private static final String SECTION = """

            ## The Code Behind This Profile

            You are running in `%s`, the checkout of the project this profile was recorded from. Read
            it with Read, Grep and Glob. You cannot change it — there is no Edit, no Write and no
            shell — because an analysis explains a recording rather than rewriting somebody's working
            tree.

            This is what separates an answer that can be acted on from one that merely sounds right.
            The profile names classes, methods and lines; only the code says what they do.

            - **Open the file before naming it.** Never describe a method, a file or a line you have
              not read. A frame name is a label, not evidence about the code behind it.
            - **Tie each finding to both halves**: the measurement that identified it, and the code
              that explains it. A reader should be able to check either one.
            - **Read what you measured, not what might be slow.** Go to the hotspots the data already
              gave you rather than surveying the project.
            - **Say when the two disagree.** The checkout may not be the build that was profiled — a
              method may have moved, been renamed or been deleted since. If what you read cannot
              explain what was measured, report that rather than reconciling them by guessing.
            """;

    private SourceGrounding() {
    }

    /**
     * The section for a prompt, or nothing at all when this analysis was given no sources — in which
     * case the prompt says nothing about reading code, rather than describing a capability the model
     * does not have.
     */
    public static String section(SourceAccess sourceAccess) {
        if (sourceAccess == null) {
            return "";
        }
        return SECTION.formatted(sourceAccess.root());
    }
}
