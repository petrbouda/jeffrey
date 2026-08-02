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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns the diff section of the model's answer into something that can actually be applied — the work
 * behind the run's {@link AdvisorStatus#BUILDING_PATCH} step.
 *
 * <p>This is deliberately separate from {@link AdvisorOutputParser}, which only splits the response
 * into sections. Splitting text and producing an applicable patch are different jobs with different
 * failure modes, and only the second one needs the checkout. Keeping them apart is also what lets the
 * timeline report the patch as its own timed step rather than hiding it inside parsing.</p>
 *
 * <p>The build repairs the diff (see {@link UnifiedDiffNormalizer}) and then checks it against the
 * checkout the same way {@link ClaimGrounder} checks a cited path. A payload with no hunk at all is
 * discarded: it is prose the model wrote under the patch marker, and offering it behind a
 * "Save .patch" button would hand the reader a file that cannot apply. Files that do not resolve are
 * logged but kept — a patch against a path Jeffrey cannot see may still be right, and the reader can
 * judge a diff they can read.</p>
 */
public final class PatchBuilder {

    private static final Logger LOG = LoggerFactory.getLogger(PatchBuilder.class);

    private static final String HUNK_PREFIX = "@@ ";

    /** {@code --- a/src/Order.java} / {@code +++ b/src/Order.java}, with the optional git prefix. */
    private static final Pattern FILE_HEADER =
            Pattern.compile("^(?:---|\\+\\+\\+) (?:[ab]/)?([^\\t]+?)\\s*$");

    /** git's placeholder for the missing side of an added or deleted file. */
    private static final String DEV_NULL = "/dev/null";

    private final SourcePaths sourcePaths;

    public PatchBuilder(Path repositoryRoot) {
        this.sourcePaths = new SourcePaths(repositoryRoot);
    }

    /**
     * The applicable diff, or null when the model proposed no edit or wrote something that is not one.
     *
     * @param rawPatch the patch section as the model wrote it, or null when it proposed no edit
     */
    public String build(String rawPatch) {
        if (rawPatch == null || rawPatch.isBlank()) {
            return null;
        }

        String normalized = UnifiedDiffNormalizer.normalize(rawPatch);
        if (!containsHunk(normalized)) {
            LOG.warn("Discarding a proposed patch with no hunk: length={}", rawPatch.length());
            return null;
        }

        List<String> unresolved = touchedFiles(normalized).stream()
                .filter(file -> !sourcePaths.containsFile(file))
                .toList();

        if (!unresolved.isEmpty()) {
            LOG.warn("Proposed patch touches files that are not in the source folder: files={}",
                    unresolved);
        }
        return normalized;
    }

    private static boolean containsHunk(String diff) {
        return diff.lines().anyMatch(line -> line.startsWith(HUNK_PREFIX));
    }

    /**
     * Every path the diff names, in the order it names them. Both sides of a rename are reported, so a
     * patch that moves a file is checked against the source it moves from.
     */
    private static List<String> touchedFiles(String diff) {
        Set<String> files = new LinkedHashSet<>();
        diff.lines().forEach(line -> {
            Matcher header = FILE_HEADER.matcher(line);
            if (header.matches() && !DEV_NULL.equals(header.group(1))) {
                files.add(header.group(1));
            }
        });
        return List.copyOf(files);
    }
}
