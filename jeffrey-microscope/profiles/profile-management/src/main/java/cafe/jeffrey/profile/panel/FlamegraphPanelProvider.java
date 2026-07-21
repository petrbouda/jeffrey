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

import cafe.jeffrey.profile.model.EventSummaryResult;
import cafe.jeffrey.profile.model.FlamegraphPanel;

import java.util.List;

/**
 * Builds the ordered flamegraph card grid for one recording format from its event summaries. Each format
 * (JFR, pprof, OTLP) presents a different set of panels; keeping that knowledge server-side lets the
 * frontend render the grid generically without inferring anything from event codes.
 */
public sealed interface FlamegraphPanelProvider
        permits JfrFlamegraphPanelProvider, StackSampleFlamegraphPanelProvider {

    List<FlamegraphPanel> panels(List<EventSummaryResult> summaries, PanelContext context);
}
