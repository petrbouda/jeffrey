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

package cafe.jeffrey.microscope.core.web.controllers;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Serves the visualization defaults the frontend needs but cannot read for itself.
 * <p>
 * The frame text mode decides how a flamegraph labels its frames, and the flamegraph is drawn in the
 * browser, so the value has to travel there. It is a static application property like every other one
 * — read once at startup, changed by editing {@code application.properties} and restarting — and the
 * per-flamegraph toggle in the UI still overrides it for the graph in front of the reader.
 */
@RestController
@RequestMapping("/api/internal/config/visualization")
public class VisualizationConfigController {

    private static final String FRAME_TEXT_MODE_PROPERTY =
            "${jeffrey.microscope.visualization.flamegraph.frame-text-mode:single-line}";

    private final String frameTextMode;

    public VisualizationConfigController(@Value(FRAME_TEXT_MODE_PROPERTY) String frameTextMode) {
        this.frameTextMode = frameTextMode;
    }

    @GetMapping
    public VisualizationConfigResponse get() {
        return new VisualizationConfigResponse(frameTextMode);
    }

    public record VisualizationConfigResponse(String frameTextMode) {
    }
}
