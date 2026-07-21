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
import cafe.jeffrey.profile.model.EventSummaryResult;
import cafe.jeffrey.profile.model.FlamegraphPanel;
import cafe.jeffrey.profile.model.ToggleOption;
import cafe.jeffrey.profile.model.WeightKind;
import cafe.jeffrey.profile.model.WeightOption;
import cafe.jeffrey.shared.common.model.WeightUnit;

/**
 * Builds {@link FlamegraphPanel}s: from a curated {@link PanelSection} template for JFR, or as a plain
 * generic card for aggregated stack-sample formats (pprof / OTLP), where the only per-card variation is
 * the weight formatting derived from the sample unit.
 */
public final class PanelAssembler {

    // pprof / OTLP store the original sample type as `type/unit` under this extras key; the unit (shared
    // with the generator via WeightUnit) tells us how to format the weight without inspecting the code.
    private static final String EXTRA_SAMPLE_TYPE = "sampleType";

    private static final String COLOR_ALLOCATION = "green";
    private static final String COLOR_SAMPLES = "blue";
    private static final String ICON_ALLOCATION = "bi-memory";
    private static final String ICON_SAMPLES = "bi-fire";
    private static final String WEIGHT_SIZE = "Total Size";
    private static final String WEIGHT_TIME = "Total Time";

    private PanelAssembler() {
    }

    /**
     * A generic card for one aggregated stack-sample dimension (pprof / OTLP). Titled with the event code
     * verbatim; weight formatting comes from the sample unit; thread-mode is never offered (these formats
     * are pre-aggregated) and the raw "Type" row is hidden.
     */
    public static FlamegraphPanel stackSample(EventSummaryResult event, int order) {
        WeightUnit unit = WeightUnit.fromSampleType(event.primary().extras().get(EXTRA_SAMPLE_TYPE));
        WeightOption weight = weightFor(unit);
        boolean allocation = unit == WeightUnit.BYTES;
        return new FlamegraphPanel(
                event.code(),
                order,
                event.code(),
                allocation ? COLOR_ALLOCATION : COLOR_SAMPLES,
                allocation ? ICON_ALLOCATION : ICON_SAMPLES,
                false,
                ToggleOption.OFF,
                weight,
                ToggleOption.OFF,
                ToggleOption.OFF,
                ToggleOption.OFF,
                Classification.PLAIN,
                event);
    }

    private static WeightOption weightFor(WeightUnit unit) {
        return switch (unit) {
            case BYTES -> new WeightOption(true, true, WEIGHT_SIZE, WeightKind.BYTES);
            case DURATION -> new WeightOption(true, false, WEIGHT_TIME, WeightKind.DURATION);
            case NONE -> new WeightOption(false, false, null, WeightKind.DURATION);
        };
    }

    /**
     * @param title explicit card title, or {@code null} to title the card from the event's label
     *              (used for the blocking section, which shows the concrete event label)
     */
    public static FlamegraphPanel assemble(
            PanelSection section,
            EventSummaryResult event,
            String title,
            PanelContext context,
            boolean hideThreadMode,
            boolean showType) {

        boolean primary = context.primary();

        boolean threadApplicable = switch (section.threadScope()) {
            case ALWAYS -> !hideThreadMode;
            case PRIMARY -> primary && !hideThreadMode;
        };
        ToggleOption threadMode = new ToggleOption(threadApplicable, section.threadModeDefaultOn());

        PanelSection.WeightTemplate template = section.weight();
        boolean weightApplicable = template.primaryOnly() ? primary : template.applicable();
        boolean weightDefaultOn = template.primaryOnly() ? primary : template.defaultOn();
        WeightOption weight = new WeightOption(weightApplicable, weightDefaultOn, template.label(), template.kind());

        String resolvedTitle = title != null ? title : event.label();

        return new FlamegraphPanel(
                section.id(),
                section.order(),
                resolvedTitle,
                section.color(),
                section.icon(),
                showType,
                threadMode,
                weight,
                section.excludeNonJava(),
                section.excludeIdle(),
                section.onlyUnsafe(),
                section.classification(),
                event);
    }
}
