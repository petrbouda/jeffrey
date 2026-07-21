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

package cafe.jeffrey.profile.panel;

import cafe.jeffrey.profile.model.Classification;
import cafe.jeffrey.profile.model.ToggleOption;
import cafe.jeffrey.profile.model.WeightKind;
import cafe.jeffrey.shared.common.model.EventTypeName;

/**
 * The fixed presentation template for each flamegraph card section — the single source of truth for the
 * grid's shape (order, color, icon, toggles, weight, classification, and the placeholder identity for
 * empty JFR sections). Providers combine a section with an event summary and a {@link PanelContext} via
 * {@link PanelAssembler} to build a concrete {@link cafe.jeffrey.profile.model.FlamegraphPanel}.
 *
 * <p>Values mirror the eight card sections the frontend previously hard-coded, so the migration is
 * presentation-preserving. A {@code null} title means the provider titles the card from the event label
 * (blocking).
 */
public enum PanelSection {

    EXECUTION("execution", 0, "Execution Samples",
            EventTypeName.EXECUTION_SAMPLE, "Execution", "blue", "bi-sprint",
            ThreadScope.PRIMARY, false, WeightTemplate.off(),
            ToggleOption.OFF, ToggleOption.OFF, ToggleOption.OFF, Classification.PLAIN),

    CPU_TIME("cpu-time", 1, "CPU-Time Samples",
            EventTypeName.CPU_TIME_SAMPLE, "CPU-Time", "blue", "bi-cpu",
            ThreadScope.PRIMARY, false, WeightTemplate.opt("CPU Time", WeightKind.DURATION),
            ToggleOption.OFF, ToggleOption.OFF, ToggleOption.OFF, Classification.PLAIN),

    METHOD("method", 2, "Method Traces",
            EventTypeName.METHOD_TRACE, "Method Trace", "blue", "bi-sprint",
            ThreadScope.PRIMARY, false, WeightTemplate.primaryOnly("Total Time", WeightKind.DURATION),
            ToggleOption.OFF, ToggleOption.OFF, ToggleOption.OFF, new Classification(true, false, false)),

    WALL("wall", 3, "Wall-Clock Samples",
            EventTypeName.WALL_CLOCK_SAMPLE, "Wall-Clock", "purple", "bi-alarm",
            ThreadScope.PRIMARY, true, WeightTemplate.off(),
            ToggleOption.ON, ToggleOption.ON, ToggleOption.OFF, Classification.PLAIN),

    ALLOCATION("allocation", 4, "Allocation Samples",
            EventTypeName.OBJECT_ALLOCATION_IN_NEW_TLAB, "Allocation", "green", "bi-memory",
            ThreadScope.PRIMARY, false, WeightTemplate.on("Total Allocation", WeightKind.BYTES),
            ToggleOption.OFF, ToggleOption.OFF, ToggleOption.OFF, Classification.PLAIN),

    NATIVE_ALLOC("native-alloc", 5, "Native Allocation Samples",
            EventTypeName.MALLOC, "Native Allocation", "pink", "bi-memory",
            ThreadScope.ALWAYS, false, WeightTemplate.on("Total Allocation", WeightKind.BYTES),
            ToggleOption.OFF, ToggleOption.OFF, ToggleOption.ON, new Classification(false, true, false)),

    NATIVE_LEAK("native-leak", 6, "Native Allocation Leaks",
            EventTypeName.NATIVE_LEAK, "Native Allocation Leaks", "pink", "bi-memory",
            ThreadScope.ALWAYS, false, WeightTemplate.on("Total Allocation", WeightKind.BYTES),
            ToggleOption.OFF, ToggleOption.OFF, ToggleOption.ON, new Classification(false, true, false)),

    BLOCKING("blocking", 7, null,
            EventTypeName.JAVA_MONITOR_ENTER, "Locks & Blocking", "red", "bi-lock",
            ThreadScope.ALWAYS, false, WeightTemplate.on("Blocked Time", WeightKind.DURATION),
            ToggleOption.OFF, ToggleOption.OFF, ToggleOption.OFF, new Classification(false, false, true));

    /** Whether the "Use Thread-mode" toggle is offered only in primary mode or always. */
    public enum ThreadScope {
        PRIMARY,
        ALWAYS
    }

    /**
     * Weight-toggle template. {@code primaryOnly} means the toggle is offered and pre-checked only in
     * primary (non-differential) mode (method traces); otherwise {@code applicable}/{@code defaultOn}
     * are used verbatim. {@code kind} is always set (used for the sample-interval formatter too).
     */
    public record WeightTemplate(boolean applicable, boolean primaryOnly, boolean defaultOn,
                                 String label, WeightKind kind) {
        static WeightTemplate off() {
            return new WeightTemplate(false, false, false, null, WeightKind.DURATION);
        }

        static WeightTemplate opt(String label, WeightKind kind) {
            return new WeightTemplate(true, false, false, label, kind);
        }

        static WeightTemplate on(String label, WeightKind kind) {
            return new WeightTemplate(true, false, true, label, kind);
        }

        static WeightTemplate primaryOnly(String label, WeightKind kind) {
            return new WeightTemplate(false, true, false, label, kind);
        }
    }

    private final String id;
    private final int order;
    private final String title;
    private final String placeholderCode;
    private final String placeholderLabel;
    private final String color;
    private final String icon;
    private final ThreadScope threadScope;
    private final boolean threadModeDefaultOn;
    private final WeightTemplate weight;
    private final ToggleOption excludeNonJava;
    private final ToggleOption excludeIdle;
    private final ToggleOption onlyUnsafe;
    private final Classification classification;

    PanelSection(String id, int order, String title, String placeholderCode, String placeholderLabel,
                 String color, String icon, ThreadScope threadScope, boolean threadModeDefaultOn,
                 WeightTemplate weight, ToggleOption excludeNonJava, ToggleOption excludeIdle,
                 ToggleOption onlyUnsafe, Classification classification) {
        this.id = id;
        this.order = order;
        this.title = title;
        this.placeholderCode = placeholderCode;
        this.placeholderLabel = placeholderLabel;
        this.color = color;
        this.icon = icon;
        this.threadScope = threadScope;
        this.threadModeDefaultOn = threadModeDefaultOn;
        this.weight = weight;
        this.excludeNonJava = excludeNonJava;
        this.excludeIdle = excludeIdle;
        this.onlyUnsafe = onlyUnsafe;
        this.classification = classification;
    }

    public String id() {
        return id;
    }

    public int order() {
        return order;
    }

    public String title() {
        return title;
    }

    public String placeholderCode() {
        return placeholderCode;
    }

    public String placeholderLabel() {
        return placeholderLabel;
    }

    public String color() {
        return color;
    }

    public String icon() {
        return icon;
    }

    public ThreadScope threadScope() {
        return threadScope;
    }

    public boolean threadModeDefaultOn() {
        return threadModeDefaultOn;
    }

    public WeightTemplate weight() {
        return weight;
    }

    public ToggleOption excludeNonJava() {
        return excludeNonJava;
    }

    public ToggleOption excludeIdle() {
        return excludeIdle;
    }

    public ToggleOption onlyUnsafe() {
        return onlyUnsafe;
    }

    public Classification classification() {
        return classification;
    }
}
