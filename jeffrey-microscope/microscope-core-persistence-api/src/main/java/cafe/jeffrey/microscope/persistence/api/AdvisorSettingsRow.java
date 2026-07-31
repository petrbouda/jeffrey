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

package cafe.jeffrey.microscope.persistence.api;

import java.time.Instant;

/**
 * A project's stored Advisor configuration. Kept deliberately dumb — validation belongs to the advisor
 * domain, which falls back per field when a stored value is out of range, so one bad column cannot
 * discard a good neighbour.
 *
 * @param workspaceId           the workspace the project belongs to, or null
 * @param projectId             the project the settings belong to
 * @param sourcePath            absolute path to the working copy on this machine, or null
 * @param pruneThresholdPct     minimum share of total samples for a frame to reach the model
 * @param regressionThresholdPp minimum percentage-point change for a frame to count as moved
 * @param compileCommand        command that compiles the patched source, or null
 * @param testCommand           command that runs the tests, or null
 * @param modifiedAt            when the row was last written
 */
public record AdvisorSettingsRow(
        String workspaceId,
        String projectId,
        String sourcePath,
        double pruneThresholdPct,
        double regressionThresholdPp,
        String compileCommand,
        String testCommand,
        Instant modifiedAt) {
}
