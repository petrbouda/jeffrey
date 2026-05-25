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

package cafe.jeffrey.microscope.core.manager.ide;

/**
 * Cache-only view of a profile's IDE link, backing the profile-wide nav control. Read straight from
 * the per-profile cache with no discovery / port scan — a linked target stays linked until a jump
 * actually fails.
 *
 * @param selectable   whether the active bridge supports choosing a target window (only the
 *                     multi-window Jeffrey plugin / {@link IdeMode#DEFAULT}); when {@code false} the
 *                     nav control is hidden.
 * @param linked       whether a target is currently cached for the profile.
 * @param ideName      cached IDE name (e.g. {@code IntelliJ IDEA}); {@code null} when not linked.
 * @param projectName  cached project name (e.g. {@code jeffrey}); {@code null} when not linked.
 * @param port         cached built-in-server port; {@code 0} when not linked.
 * @param pid          cached IDE process id; {@code 0} when not linked.
 */
public record IdeTargetStatus(
        boolean selectable,
        boolean linked,
        String ideName,
        String projectName,
        int port,
        long pid) {

    public static IdeTargetStatus notSelectable() {
        return new IdeTargetStatus(false, false, null, null, 0, 0);
    }

    public static IdeTargetStatus notLinked() {
        return new IdeTargetStatus(true, false, null, null, 0, 0);
    }

    public static IdeTargetStatus linked(IdeTarget target) {
        return new IdeTargetStatus(
                true, true, target.ideName(), target.projectName(), target.port(), target.pid());
    }
}
