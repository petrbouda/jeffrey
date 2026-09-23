/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
 * @param basePath     the linked project's directory on disk; {@code null} when not linked. Carried
 *                     here so the one question "what checkout is this profile about?" has one answer,
 *                     rather than each caller reaching into the cache for itself.
 * @param port         cached built-in-server port; {@code 0} when not linked.
 * @param pid          cached IDE process id; {@code 0} when not linked.
 */
public record IdeTargetStatus(
        boolean selectable,
        boolean linked,
        String ideName,
        String projectName,
        String basePath,
        int port,
        long pid) {

    public static IdeTargetStatus notSelectable() {
        return new IdeTargetStatus(false, false, null, null, null, 0, 0);
    }

    public static IdeTargetStatus notLinked() {
        return new IdeTargetStatus(true, false, null, null, null, 0, 0);
    }

    public static IdeTargetStatus linked(IdeTarget target) {
        return new IdeTargetStatus(
                true,
                true,
                target.ideName(),
                target.projectName(),
                target.basePath(),
                target.port(),
                target.pid());
    }
}
