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

package cafe.jeffrey.profile.manager.model.stw;

/**
 * The lanes of the Unified Stop-The-World timeline. Each category groups one pause source.
 *
 * <p>{@code stopBudget} marks the categories whose durations are summed into the global "app-stop
 * budget" — the actual time the whole JVM was frozen. Time-to-safepoint is reported as its own GLOBAL
 * lane but excluded from the budget: it is the latency to <em>reach</em> a safepoint, distinct from (and
 * not double-counted with) the operation pause that follows.
 */
public enum StwCategory {
    GC_PAUSE("GC Pause", StwScope.GLOBAL, true),
    VM_OPERATION("VM Operation", StwScope.GLOBAL, true),
    TIME_TO_SAFEPOINT("Time to Safepoint", StwScope.GLOBAL, false),
    MONITOR("Monitor Contention", StwScope.LOCAL, false),
    PARK("Thread Park", StwScope.LOCAL, false),
    PINNED("VThread Pinned", StwScope.LOCAL, false);

    private final String label;
    private final StwScope scope;
    private final boolean stopBudget;

    StwCategory(String label, StwScope scope, boolean stopBudget) {
        this.label = label;
        this.scope = scope;
        this.stopBudget = stopBudget;
    }

    public String label() {
        return label;
    }

    public StwScope scope() {
        return scope;
    }

    /**
     * Whether this category contributes to the global app-stop budget (whole-JVM frozen time).
     */
    public boolean stopBudget() {
        return stopBudget;
    }
}
