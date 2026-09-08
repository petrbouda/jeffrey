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

package cafe.jeffrey.shared.common;

public abstract class CliConstants {

    public static final String PROFILER_PATH = "<<JEFFREY:PROFILER_PATH>>";
    public static final String CURRENT_SESSION = "<<JEFFREY:CURRENT_SESSION>>";

    public static final String DEFAULT_PROFILER_CONFIG =
            "-agentpath:" + PROFILER_PATH + "=start,alloc,lock,event=ctimer,jfrsync=default,loop=15m,chunksize=5m,file="
                    + CURRENT_SESSION + "/profile-%t.jfr";

    /**
     * Reference id of the workspace used when {@code project.workspace-ref-id} is not set
     * in the provisioner's HOCON config. Both jeffrey-provisioner (when writing project metadata + the
     * filesystem layout) and jeffrey-hub (as the fallback for
     * {@code jeffrey.hub.default-workspace.reference-id}) resolve to this single
     * value so the two sides agree on the directory structure
     * <code>&lt;workspaces&gt;/$default/&lt;project&gt;/...</code>.
     */
    public static final String DEFAULT_WORKSPACE_REF_ID = "$default";
}
