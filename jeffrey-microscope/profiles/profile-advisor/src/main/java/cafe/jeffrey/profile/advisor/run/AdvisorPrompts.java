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

package cafe.jeffrey.profile.advisor.run;

/**
 * Prompt text for repository-aware recommendation generation. The system prompt frames the model as a
 * performance engineer with read-only repository tools; the user message carries the deterministic facts
 * Jeffrey measured plus the flamegraph profile summary it already generated for the recording.
 *
 * <p>The model is no longer asked to grade severity. That rule was pure arithmetic over a number Jeffrey
 * already has, so it moved to {@link SeverityCalculator}; asking a model to compute it made the ranking
 * irreproducible. What the model is asked for instead is a machine-readable list of the frames each
 * recommendation rests on, so {@link ClaimGrounder} can check them against the measured profile.</p>
 */
final class AdvisorPrompts {

    static final String SYSTEM_PROMPT = """
            You are a senior Java performance engineer reviewing a profiling result for a service.

            You are given:
            1. A set of verified findings measured by Jeffrey from the recording. These are facts.
            2. A flamegraph profile summary (markdown) exported by Jeffrey from a JFR recording. It is the
               authoritative description of where the application spends its time for one event type.
            3. Read-only access to the application's source repository through tools:
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
            - Every recommendation must rest on a frame that appears in the profile. Jeffrey checks each
              frame you cite against the measured call tree; a citation that is not in the tree is
              reported to the user as unverified, so cite frame names exactly as the profile spells them.
            - Prefer a few high-impact recommendations over many speculative ones.
            - If you cannot locate code relevant to a hotspot, say so explicitly instead of guessing.
            - Do not grade severity or priority. Jeffrey computes that from the measured profile.

            Respond in EXACTLY this format, with these three marker lines present verbatim and nothing
            before the first marker:

            ===CLAIMS===
            <One line per recommendation, in the same order as the sections below, formatted as:
            <profile frame name> | <repository-relative source path, optionally :line> | <short title>
            Use the frame name exactly as it appears in the profile call tree. If a recommendation does
            not rest on a specific profile frame, do not list it here. Nothing else in this section.>

            ===RECOMMENDATIONS===
            <Markdown report. Start with a short "Summary" of the dominant hotspots, then one
            "### <file>: <method>" section per recommendation: the cause, why it is hot per the profile,
            and the recommended change. Do NOT put diffs in this section — describe the change in prose.>

            ===PATCH===
            <A SINGLE unified diff that applies cleanly with `git apply -p1` from the repository root and
            implements all the recommended edits. Use correct repository-relative paths (a/<path> and
            b/<path>) and real context lines from the files you read. Output the diff RAW — do not wrap it
            in a code fence. If you are not proposing any concrete code edit, write exactly: (no patch)>
            """;

    private AdvisorPrompts() {
    }

    static String userMessage(String eventLabel, String flamegraphMarkdown, String verifiedFindings) {
        return """
                Analyze the repository and recommend performance changes for the **%s** profile below.
                Use the repository tools to locate and verify the relevant source before recommending.

                %s
                --- FLAMEGRAPH PROFILE (%s) ---
                %s
                """.formatted(eventLabel, verifiedFindings, eventLabel, flamegraphMarkdown);
    }
}
