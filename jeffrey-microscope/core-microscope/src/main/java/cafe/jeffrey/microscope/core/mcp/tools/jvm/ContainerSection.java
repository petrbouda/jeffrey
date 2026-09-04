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

package cafe.jeffrey.microscope.core.mcp.tools.jvm;

import cafe.jeffrey.profile.common.event.ContainerConfiguration;
import cafe.jeffrey.profile.manager.ContainerManager;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.profile.manager.model.container.ContainerCpuThrottlingData;
import cafe.jeffrey.shared.common.model.Type;

import java.util.Set;

/**
 * The Container dashboard: the limits the JVM is actually running under, and whether the scheduler
 * took CPU away from it.
 * <p>
 * "Slow in the cluster, fine on my laptop" is answered here and nowhere else. CFS throttling parks
 * every thread for the rest of the period once the quota is spent, which a CPU flamegraph cannot show
 * — the frames simply stop being sampled — and which no amount of reading the application's own code
 * explains.
 */
public record ContainerSection(ProfileManager profileManager) implements JvmSection {

    public static final String ID = "container";

    private static final String TITLE = "Container";

    private static final Set<Type> EVENT_TYPES = Set.of(
            Type.CONTAINER_CONFIGURATION,
            Type.CONTAINER_CPU_THROTTLING);

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String title() {
        return TITLE;
    }

    @Override
    public Set<Type> eventTypes() {
        return EVENT_TYPES;
    }

    @Override
    public Object render() {
        ContainerManager manager = profileManager.containerManager();
        ContainerCpuThrottlingData throttling = manager.throttling();

        return new ContainerDashboard(
                manager.configuration().configuration(),
                verdict(throttling),
                throttling.summary());
    }

    private static Verdict verdict(ContainerCpuThrottlingData throttling) {
        ContainerCpuThrottlingData.Verdict verdict = throttling.verdict();
        if (verdict == null) {
            return null;
        }
        return new Verdict(
                verdict.throttled(),
                verdict.severity().name(),
                verdict.title(),
                verdict.description());
    }

    /**
     * @param configuration the cgroup limits the JVM read at start-up, as reported by
     *                      {@code jdk.ContainerConfiguration}
     * @param verdict       Jeffrey's own reading of the throttling counters, null when the recording
     *                      carries no throttling events
     * @param summary       the counters the verdict was drawn from, so the reading can be checked
     */
    private record ContainerDashboard(
            ContainerConfiguration configuration,
            Verdict verdict,
            ContainerCpuThrottlingData.Summary summary) {
    }

    private record Verdict(boolean throttled, String severity, String title, String description) {
    }
}
