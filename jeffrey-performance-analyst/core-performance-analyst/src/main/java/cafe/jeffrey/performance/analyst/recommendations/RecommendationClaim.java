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

/**
 * One citation the model made in its report: the profile frame it claims is hot, the source location it
 * says that frame maps to, and a short title. Emitted by the model in a dedicated machine-readable
 * section so each claim can be checked against the measured profile and the real checkout rather than
 * taken on trust.
 *
 * @param title      a short human-readable label for the finding
 * @param citedFrame the profile frame the claim rests on, as the model named it
 * @param sourcePath the repository-relative source file the claim points at, or null when it named none
 */
public record RecommendationClaim(String title, String citedFrame, String sourcePath) {

    public RecommendationClaim {
        if (citedFrame == null || citedFrame.isBlank()) {
            throw new IllegalArgumentException("A claim must cite a frame");
        }
        title = (title == null || title.isBlank()) ? citedFrame : title;
    }
}
