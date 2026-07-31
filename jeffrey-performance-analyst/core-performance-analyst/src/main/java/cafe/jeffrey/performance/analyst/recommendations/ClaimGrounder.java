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

package cafe.jeffrey.performance.analyst.recommendations;

import cafe.jeffrey.performance.analyst.flamegraph.ProfileFrame;
import cafe.jeffrey.performance.analyst.flamegraph.ProfileFrameIndex;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Checks each claim the model made against the two things that can actually settle it: the measured
 * profile and the cloned checkout. Both checks are exact lookups — a frame is in the tree or it is not,
 * a file is on disk or it is not — so no second model call is needed to catch the most common failure
 * mode, which is a confident recommendation about a hotspot that was never sampled.
 *
 * <p>Path resolution mirrors {@link RepoAnalysisTools}: a cited source path is only accepted when it
 * stays inside the checkout, so a claim can never be "grounded" by pointing outside the repository.</p>
 */
public class ClaimGrounder {

    private final ProfileFrameIndex frameIndex;
    private final Path repositoryRoot;

    public ClaimGrounder(ProfileFrameIndex frameIndex, Path repositoryRoot) {
        this.frameIndex = frameIndex;
        this.repositoryRoot = repositoryRoot == null ? null : repositoryRoot.toAbsolutePath().normalize();
    }

    public List<GroundedClaim> ground(List<RecommendationClaim> claims) {
        return claims.stream().map(this::ground).toList();
    }

    private GroundedClaim ground(RecommendationClaim claim) {
        Optional<ProfileFrame> frame = frameIndex.find(claim.citedFrame());
        if (frame.isEmpty()) {
            return GroundedClaim.ungrounded(claim);
        }
        return new GroundedClaim(claim, frame.get(), sourceExists(claim.sourcePath()));
    }

    private boolean sourceExists(String sourcePath) {
        if (sourcePath == null || sourcePath.isBlank() || repositoryRoot == null) {
            return false;
        }
        Path resolved = repositoryRoot.resolve(stripLineNumber(sourcePath)).normalize();
        if (!resolved.startsWith(repositoryRoot)) {
            return false;
        }
        return Files.isRegularFile(resolved);
    }

    /**
     * Accepts both {@code path/To/File.java} and the {@code path/To/File.java:43} form the model uses
     * when it wants to point at a specific line.
     */
    private static String stripLineNumber(String sourcePath) {
        int colon = sourcePath.lastIndexOf(':');
        if (colon < 0) {
            return sourcePath.strip();
        }
        String suffix = sourcePath.substring(colon + 1).strip();
        if (!suffix.isEmpty() && suffix.chars().allMatch(Character::isDigit)) {
            return sourcePath.substring(0, colon).strip();
        }
        return sourcePath.strip();
    }
}
