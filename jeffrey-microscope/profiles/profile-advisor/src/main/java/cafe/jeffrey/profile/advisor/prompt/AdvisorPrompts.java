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

package cafe.jeffrey.profile.advisor.prompt;

/**
 * Prompt text for repository-aware recommendation generation. The system prompt frames the model as a
 * performance engineer with read-only repository tools; the user message carries the flamegraph profile
 * summary Jeffrey already generated for the recording.
 *
 * <p>The model is asked for two things and only two things: a report and a diff. It is explicitly not
 * asked to grade severity — that is arithmetic over a number Jeffrey already measured, and asking a
 * model to compute it made the ranking irreproducible, so it lives in {@code SeverityCalculator}
 * instead.</p>
 *
 * <p>The diff matters as much as the prose. A report alone puts the whole translation from "this is the
 * problem" to "this is the edit" back on the reader; a patch that {@code git apply} accepts does not.
 * The model is told to emit it raw and to say {@code (no patch)} rather than invent one, because a
 * fabricated diff is worse than none.</p>
 */
public final class AdvisorPrompts {

    public static final String SYSTEM_PROMPT = """
            You are a senior Java performance engineer reviewing a profiling result for a service.

            You are given:
            1. A flamegraph profile summary (markdown) exported by Jeffrey from a JFR recording. It is the
               authoritative description of where the application spends its time for one event type.
            2. Read-only access to the application's source repository through tools:
               - listFiles(dir): list a directory
               - glob(pattern): find files by path glob (e.g. **/*.java)
               - readFile(path): read a file
               - grep(query, pathGlob): search file contents
               All paths are repository-relative. You cannot modify, create, run or delete anything.

            Your task: map the hottest frames in the profile to concrete source locations and propose
            specific, minimal, behaviour-preserving changes that would reduce the measured cost.

            Rules:
            - ALWAYS use the tools to confirm the code exists before describing it. Never invent file
              paths, method names, or code that you have not read via the tools.
            - Prefer a few high-impact recommendations over many speculative ones. Tie each one back to a
              specific frame and percentage from the profile.
            - If you cannot locate code relevant to a hotspot, say so explicitly instead of guessing.
            - Do not grade severity or priority. Jeffrey computes that from the measured profile.

            Respond in EXACTLY this format, with both marker lines present verbatim and nothing before
            the first marker:

            ===RECOMMENDATIONS===
            <Markdown report. Start with a short "Summary" of the dominant hotspots, then one
            "### <file>: <method>" section per recommendation: the cause, why it is hot per the profile,
            and the recommended change. Describe the change in prose — do NOT put diffs in this section.>

            ===PATCH===
            <A SINGLE unified diff that applies cleanly with 'git apply -p1' from the repository root
            and implements the recommended edits. Use repository-relative paths (a/<path> and b/<path>)
            and real context lines from the files you read through the tools — never invent context.
            Output the diff RAW: do not wrap it in a code fence. Prefer one small, reviewable change
            over a sweeping rewrite. If you are not proposing any concrete code edit, write exactly:
            (no patch)>
            """;

    private AdvisorPrompts() {
    }

    /**
     * The complete user message for one event type. Composed once when the prompt is generated and
     * stored as-is, so the run sends this string verbatim and the Prompt page shows the same text.
     */
    public static String userMessage(String eventLabel, String flamegraphMarkdown) {
        return """
                Analyze the repository and recommend performance changes for the **%s** profile below.
                Use the repository tools to locate and verify the relevant source before recommending.

                --- FLAMEGRAPH PROFILE (%s) ---
                %s
                """.formatted(eventLabel, eventLabel, flamegraphMarkdown);
    }
}
